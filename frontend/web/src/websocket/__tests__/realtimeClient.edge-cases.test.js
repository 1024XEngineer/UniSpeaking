import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({
  getAccessToken: vi.fn(() => "access-token"),
  requestAuthenticated: vi.fn(),
  advanceCustomDialogueState: vi.fn(),
  advanceIeltsDialogueState: vi.fn(),
  advanceIeltsPart2State: vi.fn(),
  completeCustomDialogue: vi.fn(),
  endInterview: vi.fn(),
  evaluateCustomDialogueTurn: vi.fn(),
  evaluateIeltsDialogueTurn: vi.fn(),
  getCustomDialogueEvaluation: vi.fn(),
  getInterviewReport: vi.fn(),
  getRealtimeIceConfiguration: vi.fn(),
  submitInterviewTurn: vi.fn(),
  createPcmWavSegmentRecorder: vi.fn(),
  createRtcTelemetryMonitor: vi.fn(),
}));

vi.mock("../../infrastructure/http/apiClient.js", () => mocks);
vi.mock("../../infrastructure/audio/audioRecorder.js", () => ({
  createPcmWavSegmentRecorder: mocks.createPcmWavSegmentRecorder,
}));
vi.mock("../../telemetry/rtcTelemetry.js", () => ({
  createRtcTelemetryMonitor: mocks.createRtcTelemetryMonitor,
}));

import { createRealtimeClient } from "../realtimeClient.js";

let peerMode;
let channelMode;
let wsMode;
let wsAckMode;
let createdPeers;
let createdSockets;

class EdgeChannel {
  constructor() {
    this.readyState = channelMode.initialState || "open";
    this.listeners = {};
    this.sent = [];
    this.close = vi.fn(() => {
      this.readyState = "closed";
      this.onclose?.();
      this.dispatch("close", {});
    });
  }

  addEventListener(type, handler) {
    (this.listeners[type] ||= []).push(handler);
  }

  removeEventListener(type, handler) {
    this.listeners[type] = (this.listeners[type] || []).filter((item) => item !== handler);
  }

  dispatch(type, event) {
    this.listeners[type]?.slice().forEach((handler) => handler(event));
  }

  send(payload) {
    if (channelMode.sendError) throw new Error("data channel send failed");
    if (channelMode.sendErrorAndClose) {
      this.readyState = "closed";
      throw new Error("data channel send failed after close");
    }
    this.sent.push(JSON.parse(payload));
  }
}

class EdgePeer {
  constructor(configuration) {
    this.configuration = configuration;
    this.iceGatheringState = "new";
    this.iceConnectionState = "connected";
    this.connectionState = "connected";
    this.signalingState = "stable";
    this.listeners = {};
    this.localDescription = null;
    this.remoteDescription = null;
    this.channel = null;
    this.close = vi.fn(() => {
      this.signalingState = "closed";
      this.connectionState = "closed";
    });
    createdPeers.push(this);
  }

  addEventListener(type, handler) {
    (this.listeners[type] ||= []).push(handler);
  }

  removeEventListener(type, handler) {
    this.listeners[type] = (this.listeners[type] || []).filter((item) => item !== handler);
  }

  dispatch(type, event = {}) {
    this.listeners[type]?.slice().forEach((handler) => handler(event));
  }

  addTrack(track, stream) {
    this.localStream = stream;
    this.sender = {
      track: null,
      replaceTrack: vi.fn(async (nextTrack) => {
        this.sender.track = nextTrack;
      }),
    };
    return this.sender;
  }

  createDataChannel() {
    this.channel = new EdgeChannel();
    return this.channel;
  }

  async createOffer() {
    if (peerMode.createOfferError) throw new Error("offer failed");
    return { type: "offer", sdp: "v=0\n" };
  }

  async setLocalDescription(offer) {
    this.localDescription = {
      ...offer,
      sdp: "v=0\r\na=candidate:1 1 UDP 1 127.0.0.1 9 typ host\r\n",
    };
    this.iceGatheringState = "complete";
  }

  async setRemoteDescription(description) {
    if (peerMode.setRemoteDescriptionError) throw new Error("answer rejected");
    this.remoteDescription = description;
  }

  async getStats() {
    return peerMode.stats || [];
  }
}

class EdgeWebSocket {
  static OPEN = 1;

  static CONNECTING = 0;

  constructor(url) {
    this.url = url;
    this.readyState = EdgeWebSocket.CONNECTING;
    this.listeners = {};
    this.sent = [];
    createdSockets.push(this);
    if (wsMode === "timeout") return;
    queueMicrotask(() => {
      if (wsMode === "error") {
        this.onerror?.();
        this.dispatch("error", {});
        return;
      }
      this.readyState = EdgeWebSocket.OPEN;
      this.onopen?.();
      this.dispatch("open", {});
    });
  }

  addEventListener(type, handler) {
    (this.listeners[type] ||= []).push(handler);
  }

  removeEventListener(type, handler) {
    this.listeners[type] = (this.listeners[type] || []).filter((item) => item !== handler);
  }

  dispatch(type, event) {
    this.listeners[type]?.slice().forEach((handler) => handler(event));
  }

  send(payload) {
    const message = JSON.parse(payload);
    this.sent.push(message);
    if (!["activate", "bind", "end", "message"].includes(message.type)) return;
    queueMicrotask(() => {
      if (wsAckMode === "reject" && message.type === "activate") {
        this.onmessage?.({ data: JSON.stringify({
          type: "session.activate.accepted",
          success: false,
          code: "ACTIVATE_FAILED",
          message: "activation rejected",
        }) });
        return;
      }
      if (wsAckMode === "reject-end" && message.type === "end") {
        this.onmessage?.({ data: JSON.stringify({
          type: "session.end.accepted",
          success: false,
          code: "END_FAILED",
          message: "end rejected",
        }) });
        return;
      }
      if (wsAckMode === "reject-bind" && message.type === "bind") {
        this.onmessage?.({ data: JSON.stringify({
          type: "session.bind.accepted",
          success: false,
          code: "BIND_FAILED",
          message: "bind rejected",
        }) });
        return;
      }
      if (wsAckMode === "silent") return;
      this.onmessage?.({ data: JSON.stringify({
        type: `session.${message.type}.accepted`,
        success: true,
        data: wsAckMode === "quota" && message.type === "activate"
          ? { quotaRemainingMillis: 0 }
          : undefined,
      }) });
    });
  }

  close = vi.fn(() => {
    this.readyState = 3;
    this.onclose?.();
    this.dispatch("close", {});
  });
}

function makeStream() {
  const track = { enabled: false, stop: vi.fn() };
  return {
    track,
    getAudioTracks: () => [track],
    getTracks: () => [track],
  };
}

function backend(overrides = {}) {
  return {
    sessionId: "local-session",
    providerSessionId: "provider-session",
    answerSdp: "v=0\n",
    systemPrompt: "You are a helpful speaking partner.",
    voiceId: "Tina",
    ...overrides,
  };
}

function installBrowserFakes() {
  const stream = makeStream();
  Object.defineProperty(navigator, "mediaDevices", {
    configurable: true,
    value: { getUserMedia: vi.fn(async () => stream) },
  });
  vi.stubGlobal("RTCPeerConnection", EdgePeer);
  vi.stubGlobal("WebSocket", EdgeWebSocket);
  return stream;
}

async function startClient(options = {}) {
  const client = createRealtimeClient(options);
  const promise = client.start();
  expect(client.start()).toBe(promise);
  await promise;
  return client;
}

beforeEach(() => {
  peerMode = {};
  channelMode = {};
  wsMode = "open";
  wsAckMode = "normal";
  createdPeers = [];
  createdSockets = [];
  vi.clearAllMocks();
  installBrowserFakes();
  mocks.getAccessToken.mockReturnValue("access-token");
  mocks.requestAuthenticated.mockResolvedValue(backend());
  mocks.getRealtimeIceConfiguration.mockResolvedValue({ turnEnabled: false, iceServers: [] });
  mocks.createRtcTelemetryMonitor.mockReturnValue({
    stateChanged: vi.fn(),
    connected: vi.fn(),
    stop: vi.fn(),
  });
  mocks.advanceIeltsPart2State.mockResolvedValue({ completed: false, controlInstruction: "Continue Part 2" });
  mocks.advanceIeltsDialogueState.mockResolvedValue({ completed: false, controlInstruction: "Next Part 3 question" });
  mocks.evaluateIeltsDialogueTurn.mockResolvedValue({ overallScore: 84 });
  mocks.completeCustomDialogue.mockResolvedValue({ evaluation: { finalScore: 86 } });
  mocks.getCustomDialogueEvaluation.mockResolvedValue({ finalScore: 80 });
  mocks.endInterview.mockResolvedValue({ status: "PROCESSING" });
  mocks.getInterviewReport.mockResolvedValue({ status: "PROCESSING" });
  mocks.createPcmWavSegmentRecorder.mockResolvedValue(undefined);
  vi.stubGlobal("RTCPeerConnection", EdgePeer);
  vi.stubGlobal("WebSocket", EdgeWebSocket);
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

describe("realtime client transport and lifecycle edges", () => {
  it("reports malformed provider input, forwards peer events, deduplicates messages, and cleans up", async () => {
    const events = [];
    const onRemoteStream = vi.fn();
    const remoteTrack = { stop: vi.fn() };
    const client = await startClient({ onEvent: (event) => events.push(event), onRemoteStream });
    const peer = createdPeers[0];
    const incoming = new EdgeChannel();
    peer.ontrack({ streams: [{ getTracks: () => [remoteTrack] }] });
    peer.onconnectionstatechange();
    peer.oniceconnectionstatechange();
    peer.ondatachannel({ channel: incoming });
    incoming.onmessage({ data: "{" });
    await client.handleEvent("not-json");
    expect(events.filter((event) => event.type === "local.error")).toHaveLength(2);
    expect(onRemoteStream).toHaveBeenCalledTimes(1);

    await client.handleEvent({ type: "session.updated", session: { instructions: "ready" } });
    await client.handleEvent({ type: "response.created" });
    await client.handleEvent({ type: "response.audio_transcript.done", item_id: "assistant-1", transcript: "Welcome" });
    await client.handleEvent({ type: "response.audio_transcript.done", item_id: "assistant-1", transcript: "Welcome again" });
    await client.handleEvent({ type: "response.text.done", text: "   " });
    const transcripts = events.filter((event) => event.type === "local.transcript.final");
    expect(transcripts).toHaveLength(1);
    expect(peer.channel.sent.map((event) => event.type)).toContain("response.create");

    expect(client.setMuted(true)).toBe(true);
    expect(client.setMuted(false)).toBe(false);
    await client.pause();
    await client.resume();
    await client.handleEvent({ type: "response.done" });
    expect(client.requestResponse()).toBe(true);
    expect(client.requestResponse()).toBe(false);
    await client.interrupt();
    await client.stop({ notifyBackend: false, reason: "edge_cleanup" });
    expect(peer.channel.close).toHaveBeenCalled();
    expect(peer.close).toHaveBeenCalled();
    expect(remoteTrack.stop).toHaveBeenCalled();
    expect(client.isActive()).toBe(false);
    expect(events.map((event) => event.type)).toEqual(expect.arrayContaining([
      "local.connection_state",
      "local.muted",
      "local.paused",
      "local.resumed",
      "local.interrupted",
      "local.ended",
    ]));
  });

  it("handles missing authentication and WebSocket failures during startup", async () => {
    mocks.getAccessToken.mockReturnValueOnce(null);
    const missingTokenEvents = [];
    const missingToken = createRealtimeClient({ onEvent: (event) => missingTokenEvents.push(event) });
    await expect(missingToken.start()).rejects.toThrow("请先登录");
    expect(missingTokenEvents.at(-1)).toMatchObject({ type: "local.error", code: "SESSION_WEBSOCKET_FAILED" });
    expect(missingToken.isActive()).toBe(false);

    wsMode = "error";
    const socketEvents = [];
    const socketFailure = createRealtimeClient({ onEvent: (event) => socketEvents.push(event) });
    await expect(socketFailure.start()).rejects.toThrow("会话 WebSocket 连接失败");
    expect(socketEvents.at(-1)).toMatchObject({ type: "local.error", code: "SESSION_WEBSOCKET_FAILED" });
    expect(createdSockets[0].close).toHaveBeenCalled();
  });

  it("cleans up after remote-description and session-activation failures", async () => {
    peerMode.setRemoteDescriptionError = true;
    const remoteEvents = [];
    const remoteFailure = createRealtimeClient({ onEvent: (event) => remoteEvents.push(event) });
    await expect(remoteFailure.start()).rejects.toThrow("answer rejected");
    expect(remoteEvents.at(-1)).toMatchObject({ type: "local.error", code: "WEBRTC_REMOTE_DESCRIPTION_FAILED" });
    expect(createdPeers[0].close).toHaveBeenCalled();

    peerMode = {};
    wsAckMode = "reject";
    const activationEvents = [];
    const activationFailure = createRealtimeClient({ onEvent: (event) => activationEvents.push(event) });
    await expect(activationFailure.start()).rejects.toThrow("activation rejected");
    expect(activationEvents.at(-1)).toMatchObject({ type: "local.error", code: "ACTIVATE_FAILED" });
    expect(createdPeers.at(-1).close).toHaveBeenCalled();
  });

  it("supports instruction acknowledgements, rejects update errors, and closes active sessions", async () => {
    const events = [];
    const client = await startClient({ onEvent: (event) => events.push(event) });
    await client.handleEvent({ type: "session.updated", session: { instructions: "ready" } });
    const update = client.updateInstructions("extra guidance");
    await vi.waitFor(() => expect(
      createdPeers[0].channel.sent.filter((event) => event.type === "session.update"),
    ).toHaveLength(2));
    await Promise.resolve();
    await client.handleEvent({ type: "session.updated", session: { instructions: "extra guidance" } });
    await expect(update).resolves.toBe(true);
    const sentUpdates = createdPeers[0].channel.sent.filter((event) => event.type === "session.update");
    expect(sentUpdates.at(-1).session.instructions).toContain("extra guidance");

    const failedUpdate = client.updateInstructions("will fail");
    await vi.waitFor(() => expect(
      createdPeers[0].channel.sent.filter((event) => event.type === "session.update"),
    ).toHaveLength(3));
    await Promise.resolve();
    await client.handleEvent({ type: "error", error: { message: "session.update rejected" } });
    await expect(failedUpdate).rejects.toThrow("session.update rejected");
    expect(events).toContainEqual(expect.objectContaining({ type: "error" }));
    await client.stop();
    expect(createdSockets[0].sent.map((message) => message.type)).toContain("end");
    expect(events.at(-1)).toMatchObject({ type: "local.ended", reason: "user_stop" });
  });

  it("times out a queued instruction update and continues with the next update", async () => {
    vi.useFakeTimers();
    const events = [];
    const client = await startClient({ onEvent: (event) => events.push(event) });
    await client.handleEvent({ type: "session.updated", session: { instructions: "ready" } });

    const timedOut = client.updateInstructions("never acknowledged");
    const timedOutAssertion = expect(timedOut).rejects.toThrow("等待实时会话指令更新确认超时");
    await vi.advanceTimersByTimeAsync(5_000);
    await timedOutAssertion;

    const next = client.updateInstructions("acknowledged next");
    await vi.waitFor(() => expect(
      createdPeers[0].channel.sent.filter((event) => event.type === "session.update"),
    ).toHaveLength(3));
    await client.handleEvent({ type: "session.updated", session: { instructions: "acknowledged next" } });
    await expect(next).resolves.toBe(true);
    vi.useRealTimers();
    await client.stop({ notifyBackend: false, reason: "instruction_timeout_cleanup" });
    expect(events).not.toContainEqual(expect.objectContaining({ type: "local.provider_warning" }));
  });

  it("handles a provider close racing a response send without throwing", async () => {
    const client = await startClient();
    await client.handleEvent({ type: "session.updated", session: { instructions: "ready" } });
    await client.handleEvent({ type: "response.done" });
    channelMode.sendErrorAndClose = true;
    expect(client.requestResponse()).toBe(false);
    await client.stop({ notifyBackend: false, reason: "response_race_cleanup" });
  });

  it("rejects IELTS controls unless the matching stage is active", async () => {
    const client = createRealtimeClient({ sceneId: "free-client", sceneType: "free-chat" });
    await expect(client.transitionIeltsPart2("PREPARATION_COMPLETE")).rejects.toThrow("当前会话不是 IELTS Part 2");
    await expect(client.forceIeltsPart3TurnTimeout()).rejects.toThrow("当前会话不是 IELTS Part 3");

    mocks.requestAuthenticated.mockResolvedValueOnce(backend({ currentStage: "PART_3" }));
    const part3 = await startClient({ sceneId: "part3-controls", sceneType: "ielts" });
    await expect(part3.transitionIeltsPart2("ANSWER_COMPLETE")).rejects.toThrow("当前会话不是 IELTS Part 2");
    await part3.stop({ notifyBackend: false, reason: "invalid_control_cleanup" });
  });

  it("recovers an IELTS Part 1 turn with the prepared-question fallback", async () => {
    mocks.requestAuthenticated.mockResolvedValueOnce(backend({
      currentStage: "PART_1",
      content: { part1: [{ question: "Only prepared question" }] },
    }));
    mocks.advanceIeltsDialogueState.mockRejectedValueOnce(new Error("state unavailable"));
    const events = [];
    const client = await startClient({
      sceneId: "part1-fallback-complete",
      sceneType: "ielts",
      onEvent: (event) => events.push(event),
    });
    await client.handleEvent({ type: "response.created" });
    await client.handleEvent({ type: "response.done" });
    await client.handleEvent({
      type: "conversation.item.input_audio_transcription.completed",
      item_id: "part1-answer",
      transcript: "My answer",
    });
    expect(events).toContainEqual(expect.objectContaining({
      type: "local.ielts_state_recovered",
      message: "state unavailable",
    }));
    expect(createdPeers[0].channel.sent).toContainEqual(expect.objectContaining({
      type: "response.create",
      response: expect.objectContaining({
        instructions: expect.stringContaining("PREPARED_QUESTIONS"),
      }),
    }));
    await client.stop({ notifyBackend: false, reason: "part1_fallback_cleanup" });
  });

  it("defers custom completion when the assistant asks a follow-up question", async () => {
    mocks.createPcmWavSegmentRecorder.mockResolvedValue({
      startSegment: vi.fn(),
      stopSegment: vi.fn(async () => new Blob(["custom"])),
      close: vi.fn(),
    });
    mocks.advanceCustomDialogueState.mockResolvedValueOnce({
      completed: true,
      controlInstruction: "Ask one final follow-up.",
    });
    const events = [];
    const client = await startClient({
      sceneId: "custom-follow-up",
      sceneType: "custom",
      onEvent: (event) => events.push(event),
    });
    await client.handleEvent({ type: "response.created" });
    await client.handleEvent({ type: "response.done" });
    await client.handleEvent({ type: "input_audio_buffer.speech_started", item_id: "custom-follow-up-input" });
    await client.handleEvent({
      type: "conversation.item.input_audio_transcription.completed",
      item_id: "custom-follow-up-input",
      transcript: "My final answer",
    });
    await vi.waitFor(() => expect(mocks.advanceCustomDialogueState).toHaveBeenCalled());
    await client.handleEvent({
      type: "response.audio_transcript.done",
      item_id: "follow-up-question",
      transcript: "Could you explain that further?",
    });
    await client.handleEvent({ type: "response.done" });
    expect(events).not.toContainEqual(expect.objectContaining({ type: "local.scenario_completed" }));
    expect(mocks.completeCustomDialogue).not.toHaveBeenCalled();
    await client.stop({ notifyBackend: false, reason: "custom_follow_up_cleanup" });
  });

  it("recovers a failed custom completion request with an idempotent evaluation query", async () => {
    mocks.completeCustomDialogue.mockRejectedValueOnce(new Error("completion interrupted"));
    mocks.getCustomDialogueEvaluation.mockResolvedValueOnce({ finalScore: 91 });
    const events = [];
    const client = await startClient({
      sceneId: "custom-1",
      sceneType: "custom",
      onEvent: (event) => events.push(event),
    });
    const completion = await client.stop({ reason: "user_stop" });
    expect(completion.evaluation).toEqual({ finalScore: 91 });
    expect(mocks.getCustomDialogueEvaluation).toHaveBeenCalledWith("custom-1", "local-session");
    expect(events).toContainEqual(expect.objectContaining({
      type: "local.session_evaluation",
      evaluation: { finalScore: 91 },
    }));
  });

  it("exercises IELTS Part 2 transitions and Part 3 timeout state", async () => {
    mocks.requestAuthenticated.mockResolvedValueOnce(backend({
      currentStage: "PART_2",
      content: { part3: [] },
    }));
    const part2Events = [];
    const part2 = await startClient({
      sceneId: "ielts-2",
      sceneType: "ielts",
      onEvent: (event) => part2Events.push(event),
    });
    await expect(part2.transitionIeltsPart2("PREPARATION_COMPLETE")).resolves.toMatchObject({ completed: false });
    await expect(part2.transitionIeltsPart2("ANSWER_COMPLETE")).resolves.toMatchObject({ completed: false });
    expect(mocks.advanceIeltsPart2State).toHaveBeenCalledWith("ielts-2", "local-session", "PREPARATION_COMPLETE");
    expect(part2Events.map((event) => event.type)).toContain("local.ielts_part2_state");
    await part2.stop({ notifyBackend: false });

    mocks.requestAuthenticated.mockResolvedValueOnce(backend({ currentStage: "PART_3" }));
    const part3 = await startClient({ sceneId: "ielts-3", sceneType: "ielts" });
    await expect(part3.forceIeltsPart3TurnTimeout()).resolves.toMatchObject({ completed: false });
    await expect(part3.forceIeltsPart3TurnTimeout()).resolves.toBeNull();
    expect(mocks.advanceIeltsDialogueState).toHaveBeenCalledWith("ielts-3", "local-session", 1, true);
    await part3.stop({ notifyBackend: false });
  });

  it("ends a quota-expired session and keeps stop idempotent", async () => {
    wsAckMode = "quota";
    const events = [];
    const client = await startClient({ onEvent: (event) => events.push(event) });
    await vi.waitFor(() => expect(events).toContainEqual(expect.objectContaining({ type: "local.quota_exhausted" })));
    await vi.waitFor(() => expect(client.isActive()).toBe(false));
    expect(events).toContainEqual(expect.objectContaining({ type: "local.ended", reason: "quota_exhausted" }));
    await client.stop({ notifyBackend: false });
  });

  it("covers data-channel open, close, error, and ICE failure branches during startup", async () => {
    channelMode.initialState = "connecting";
    const openClient = createRealtimeClient();
    const opening = openClient.start();
    await vi.waitFor(() => expect(createdPeers).toHaveLength(1));
    createdPeers[0].channel.readyState = "open";
    createdPeers[0].channel.dispatch("open");
    await expect(opening).resolves.toMatchObject({ sessionId: "local-session" });
    await openClient.stop({ notifyBackend: false });

    channelMode.initialState = "connecting";
    const closeEvents = [];
    const closeClient = createRealtimeClient({ onEvent: (event) => closeEvents.push(event) });
    const closing = closeClient.start();
    await vi.waitFor(() => expect(createdPeers).toHaveLength(2));
    createdPeers[1].channel.dispatch("close");
    await expect(closing).rejects.toThrow("实时会话启动已取消");
    expect(closeEvents).toEqual([{ type: "local.connecting" }]);

    channelMode.initialState = "connecting";
    const errorClient = createRealtimeClient();
    const errored = errorClient.start();
    await vi.waitFor(() => expect(createdPeers).toHaveLength(3));
    createdPeers[2].channel.dispatch("error");
    await expect(errored).rejects.toThrow("实时数据通道连接失败");

    channelMode.initialState = "connecting";
    const iceClient = createRealtimeClient();
    const iceFailure = iceClient.start();
    await vi.waitFor(() => expect(createdPeers).toHaveLength(4));
    createdPeers[3].iceConnectionState = "failed";
    createdPeers[3].dispatch("iceconnectionstatechange");
    await expect(iceFailure).rejects.toThrow("实时网络通道建立失败");
  });

  it("binds a provider session discovered after startup and handles a missing binding ack", async () => {
    mocks.requestAuthenticated.mockResolvedValueOnce(backend({ providerSessionId: undefined }));
    const events = [];
    const client = await startClient({ onEvent: (event) => events.push(event) });
    await client.handleEvent({ type: "session.created", session: { id: "late-provider-session" } });
    expect(createdSockets[0].sent).toContainEqual(expect.objectContaining({
      type: "bind",
      providerSessionId: "late-provider-session",
    }));

    await client.handleEvent({ type: "session.created" });
    expect(events).not.toContainEqual(expect.objectContaining({
      message: expect.stringContaining("服务商未返回 session.created"),
    }));
    await client.stop({ notifyBackend: false });
  });

  it("completes an interview through its closing response and end request", async () => {
    mocks.submitInterviewTurn.mockResolvedValueOnce({
      state: { shouldEnd: true, controlInstruction: "Thank the candidate and close." },
      reportStatus: "PROCESSING",
    });
    mocks.endInterview.mockResolvedValueOnce({ status: "PROCESSING" });
    const events = [];
    const onRemoteAudioDrain = vi.fn(async () => undefined);
    const client = await startClient({
      sceneId: "interview-final",
      sceneType: "interview",
      onEvent: (event) => events.push(event),
      onRemoteAudioDrain,
    });

    await client.handleEvent({ type: "session.updated", session: { instructions: "ready" } });
    await client.handleEvent({ type: "response.created" });
    await client.handleEvent({ type: "input_audio_buffer.speech_started", item_id: "interview-input" });
    await client.handleEvent({ type: "input_audio_buffer.speech_stopped" });
    await client.handleEvent({
      type: "conversation.item.input_audio_transcription.completed",
      item_id: "interview-input",
      transcript: "I led the project",
    });
    expect(mocks.submitInterviewTurn).toHaveBeenCalledWith(
      "interview-final",
      "local-session",
      1,
      expect.any(FormData),
    );
    expect(events).toContainEqual(expect.objectContaining({ type: "local.interview_closing" }));

    await client.handleEvent({
      type: "response.audio_transcript.done",
      item_id: "closing-message",
      transcript: "Thank you for your time.",
    });
    await client.handleEvent({ type: "response.done" });
    await vi.waitFor(() => expect(events).toContainEqual(expect.objectContaining({
      type: "local.interview_end_requested",
      reportStatus: "PROCESSING",
    })));
    await vi.waitFor(() => expect(events).toContainEqual(expect.objectContaining({
      type: "local.ended",
      reason: "state_machine",
    })));
    expect(onRemoteAudioDrain).toHaveBeenCalledWith({ fallbackMs: 2_500, timeoutMs: 10_000 });
    expect(mocks.endInterview).toHaveBeenCalledWith("interview-final", "local-session");
  });

  it("recovers an interrupted interview end request from its report", async () => {
    mocks.endInterview.mockRejectedValueOnce(new Error("interview end interrupted"));
    mocks.getInterviewReport.mockResolvedValueOnce({ status: "READY", report: { score: 92 } });
    const events = [];
    const client = await startClient({
      sceneId: "interview-recovery",
      sceneType: "interview",
      onEvent: (event) => events.push(event),
    });

    const completion = await client.stop();
    expect(completion).toMatchObject({
      sceneId: "interview-recovery",
      sessionId: "local-session",
      reportStatus: "READY",
      report: { score: 92 },
    });
    expect(mocks.getInterviewReport).toHaveBeenCalledWith("interview-recovery", "local-session");
    expect(events).toContainEqual(expect.objectContaining({
      type: "local.interview_state",
      reportStatus: "READY",
    }));
  });

  it("reports a failed interview end when report recovery also fails", async () => {
    mocks.endInterview.mockRejectedValueOnce(new Error("interview end failed"));
    mocks.getInterviewReport.mockRejectedValue(new Error("report unavailable"));
    const events = [];
    const client = await startClient({
      sceneId: "interview-failed",
      sceneType: "interview",
      onEvent: (event) => events.push(event),
    });

    await expect(client.stop({ awaitEvaluations: false })).rejects.toThrow("interview end failed");
    expect(events).toContainEqual(expect.objectContaining({
      type: "local.backend_warning",
      message: "interview end failed",
    }));
  });

  it("keeps a free-chat stop resolved when the backend end ack fails", async () => {
    wsAckMode = "reject-end";
    const events = [];
    const client = await startClient({ onEvent: (event) => events.push(event) });

    const completion = await client.stop();
    expect(completion).toBeNull();
    expect(events).toContainEqual(expect.objectContaining({
      type: "local.backend_warning",
      message: "end rejected",
    }));
    expect(events).toContainEqual(expect.objectContaining({
      type: "local.ended",
      reason: "user_stop",
    }));
  });

  it("recovers IELTS state with the exact fallback instruction and restores input after assistant output", async () => {
    mocks.requestAuthenticated.mockResolvedValueOnce(backend({
      currentStage: "PART_1",
      content: { part1: [{ question: 'What is your "home" like?' }] },
    }));
    mocks.advanceIeltsDialogueState.mockRejectedValueOnce(new Error("IELTS state unavailable"));
    const events = [];
    const client = await startClient({
      sceneId: "ielts-recovery",
      sceneType: "ielts",
      onEvent: (event) => events.push(event),
    });

    await client.handleEvent({ type: "session.updated", session: { instructions: "ready" } });
    await client.handleEvent({ type: "response.created" });
    await client.handleEvent({ type: "response.done" });
    await client.handleEvent({
      type: "conversation.item.input_audio_transcription.completed",
      item_id: "ielts-input",
      transcript: "I live near the city center",
    });
    const recovered = events.find((event) => event.type === "local.ielts_state_recovered");
    expect(recovered).toMatchObject({ message: "IELTS state unavailable" });
    const response = createdPeers[0].channel.sent.find(
      (event) => event.type === "response.create" && event.response?.instructions,
    );
    expect(response.response.instructions).toContain('"What is your \\"home\\" like?"');

    await client.handleEvent({
      type: "response.audio_transcript.done",
      item_id: "assistant-ielts",
      transcript: "Please answer the question.",
    });
    await new Promise((resolve) => setTimeout(resolve, 2_600));
    expect(events).toContainEqual(expect.objectContaining({
      type: "local.ielts_input_ready",
      source: "assistant_output_fallback",
    }));
    await client.stop({ notifyBackend: false });
  });

  it("handles IELTS Part 2 completion and ignores input after completion", async () => {
    mocks.requestAuthenticated.mockResolvedValueOnce(backend({ currentStage: "PART_2", content: { part3: [] } }));
    mocks.advanceIeltsPart2State.mockResolvedValueOnce({
      completed: true,
      controlInstruction: "Part 2 is complete.",
    });
    const events = [];
    const client = await startClient({
      sceneId: "ielts-complete",
      sceneType: "ielts",
      onEvent: (event) => events.push(event),
    });

    await client.transitionIeltsPart2("ANSWER_COMPLETE");
    await client.handleEvent({ type: "response.done" });
    await client.handleEvent({ type: "input_audio_buffer.speech_started", item_id: "late-input" });
    await client.handleEvent({
      type: "conversation.item.input_audio_transcription.completed",
      item_id: "late-input",
      transcript: "This answer arrived late",
    });
    expect(events).toContainEqual(expect.objectContaining({ type: "local.ielts_part2_completion_ready" }));
    expect(mocks.advanceIeltsPart2State).toHaveBeenCalledWith("ielts-complete", "local-session", "ANSWER_COMPLETE");
    expect(mocks.evaluateIeltsDialogueTurn).not.toHaveBeenCalled();
    await client.stop({ notifyBackend: false });
  });

  it("completes a custom scene after its final response audio drains", async () => {
    mocks.createPcmWavSegmentRecorder.mockResolvedValue({
      startSegment: vi.fn(),
      stopSegment: vi.fn(async () => new Blob(["custom"])),
      close: vi.fn(),
    });
    mocks.advanceCustomDialogueState.mockResolvedValueOnce({
      completed: true,
      controlInstruction: "Close the custom scene.",
    });
    const events = [];
    const client = await startClient({
      sceneId: "custom-complete",
      sceneType: "custom",
      onEvent: (event) => events.push(event),
      onRemoteAudioDrain: vi.fn(async () => undefined),
    });

    await client.handleEvent({ type: "session.updated", session: { instructions: "ready" } });
    await client.handleEvent({ type: "response.created" });
    await client.handleEvent({ type: "response.done" });
    await client.handleEvent({ type: "input_audio_buffer.speech_started", item_id: "custom-input" });
    await client.handleEvent({ type: "input_audio_buffer.speech_stopped" });
    await client.handleEvent({
      type: "conversation.item.input_audio_transcription.completed",
      item_id: "custom-input",
      transcript: "My final answer",
    });
    await vi.waitFor(() => expect(mocks.advanceCustomDialogueState).toHaveBeenCalled());
    expect(client.requestResponse()).toBe(true);
    await client.handleEvent({ type: "response.done" });
    await vi.waitFor(() => expect(events).toContainEqual(expect.objectContaining({
      type: "local.scenario_completed",
    })));
    await vi.waitFor(() => expect(events).toContainEqual(expect.objectContaining({
      type: "local.ended",
      reason: "state_machine",
    })));
    expect(mocks.completeCustomDialogue).toHaveBeenCalledWith(
      "custom-complete",
      "local-session",
      expect.any(String),
    );
  });

  it("retries provider binding and reports the final bind failure", async () => {
    wsAckMode = "reject-bind";
    mocks.requestAuthenticated.mockResolvedValueOnce(backend({ providerSessionId: undefined }));
    const events = [];
    const client = await startClient({ onEvent: (event) => events.push(event) });

    await client.handleEvent({
      type: "session.created",
      session: { id: "provider-bind-failure" },
    });

    expect(createdSockets[0].sent.filter((message) => message.type === "bind")).toHaveLength(2);
    expect(events).toContainEqual(expect.objectContaining({
      type: "local.backend_warning",
      message: "服务商会话绑定失败：bind rejected",
    }));

    // Keep teardown independent from the deliberately rejected binding.
    wsAckMode = "normal";
    await client.stop({ notifyBackend: false, reason: "bind_failure_cleanup" });
  });

  it("surfaces custom completionError after all evaluation recovery attempts fail", async () => {
    mocks.completeCustomDialogue.mockRejectedValueOnce(new Error("custom completion failed"));
    mocks.getCustomDialogueEvaluation.mockRejectedValue(new Error("evaluation still unavailable"));
    const events = [];
    const client = await startClient({
      sceneId: "custom-no-recovery",
      sceneType: "custom",
      onEvent: (event) => events.push(event),
    });

    await expect(client.stop({ awaitEvaluations: false })).rejects.toThrow("custom completion failed");
    expect(mocks.getCustomDialogueEvaluation).toHaveBeenCalledTimes(3);
    expect(events).toContainEqual(expect.objectContaining({
      type: "local.backend_warning",
      message: "custom completion failed",
    }));
    expect(client.isActive()).toBe(false);
  });

  it("surfaces interview completionError when no report can be recovered", async () => {
    mocks.endInterview.mockRejectedValueOnce(new Error("interview completion failed"));
    mocks.getInterviewReport.mockRejectedValue(new Error("report still unavailable"));
    const events = [];
    const client = await startClient({
      sceneId: "interview-no-recovery",
      sceneType: "interview",
      onEvent: (event) => events.push(event),
    });

    await expect(client.stop({ awaitEvaluations: false })).rejects.toThrow("interview completion failed");
    expect(mocks.getInterviewReport).toHaveBeenCalledTimes(3);
    expect(events).toContainEqual(expect.objectContaining({
      type: "local.backend_warning",
      message: "interview completion failed",
    }));
    expect(client.isActive()).toBe(false);
  });

  it("handles request and instruction updates when no session or channel exists", async () => {
    const events = [];
    const client = createRealtimeClient({ onEvent: (event) => events.push(event) });

    expect(client.isActive()).toBe(false);
    expect(client.requestResponse()).toBe(false);
    await expect(client.updateInstructions("before start")).rejects.toThrow("实时数据通道尚未连接");
    await expect(client.handleEvent({
      type: "response.audio_transcript.done",
      item_id: "orphan-assistant",
      transcript: "This cannot be persisted yet",
    })).rejects.toThrow("会话 ID 尚未建立");
    await client.interrupt();
    await client.stop({ reason: "no_session" });

    expect(events).toContainEqual(expect.objectContaining({
      type: "local.ended",
      reason: "no_session",
      completion: null,
    }));
    expect(client.isActive()).toBe(false);
  });

  it("rejects updates after transport shutdown and keeps requestResponse safe", async () => {
    const client = await startClient();
    await client.stop({ notifyBackend: false, reason: "request_cleanup" });

    expect(client.requestResponse()).toBe(false);
    await expect(client.updateInstructions("after stop")).rejects.toThrow("实时数据通道尚未连接");
  });

  it("surfaces provider send failures from request and instruction updates", async () => {
    const events = [];
    const client = await startClient({ onEvent: (event) => events.push(event) });
    channelMode.sendError = true;

    expect(() => client.requestResponse()).toThrow("data channel send failed");
    await expect(client.updateInstructions("send failure")).rejects.toThrow("data channel send failed");
    expect(events).toContainEqual(expect.objectContaining({ type: "local.connected" }));

    channelMode.sendError = false;
    await client.stop({ notifyBackend: false, reason: "send_failure_cleanup" });
  });
});
