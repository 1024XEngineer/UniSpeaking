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

class FakeChannel {
  static OPEN = 1;

  constructor() {
    this.readyState = "open";
    this.sent = [];
    this.listeners = {};
    this.close = vi.fn(() => { this.readyState = "closed"; this.listeners.close?.forEach((fn) => fn()); });
  }

  addEventListener(type, handler) {
    (this.listeners[type] ||= []).push(handler);
  }

  removeEventListener(type, handler) {
    this.listeners[type] = (this.listeners[type] || []).filter((fn) => fn !== handler);
  }

  send(payload) {
    this.sent.push(JSON.parse(payload));
  }
}

class FakePeer {
  constructor(configuration) {
    this.configuration = configuration;
    this.iceGatheringState = "new";
    this.iceConnectionState = "connected";
    this.connectionState = "connected";
    this.signalingState = "stable";
    this.listeners = {};
    this.channel = null;
    this.localStream = null;
    this.close = vi.fn(() => { this.signalingState = "closed"; });
  }

  addEventListener(type, handler) { (this.listeners[type] ||= []).push(handler); }
  removeEventListener(type, handler) { this.listeners[type] = (this.listeners[type] || []).filter((fn) => fn !== handler); }
  addTrack(track, stream) {
    this.localStream = stream;
    return { track: null, replaceTrack: vi.fn(async (nextTrack) => { this.senderTrack = nextTrack; }) };
  }
  createDataChannel() { this.channel = new FakeChannel(); return this.channel; }
  async createOffer() { return { type: "offer", sdp: "v=0\n" }; }
  async setLocalDescription(offer) {
    this.localDescription = { ...offer, sdp: "v=0\r\na=candidate:1 1 UDP 1 127.0.0.1 9 typ host\r\n" };
    this.iceGatheringState = "complete";
  }
  async setRemoteDescription(description) { this.remoteDescription = description; }
  async getStats() { return []; }
}

class FakeWebSocket {
  static OPEN = 1;
  static CONNECTING = 0;

  constructor(url) {
    this.url = url;
    this.readyState = FakeWebSocket.CONNECTING;
    this.listeners = {};
    this.sent = [];
    queueMicrotask(() => {
      this.readyState = FakeWebSocket.OPEN;
      this.onopen?.();
      this.listeners.open?.forEach((fn) => fn());
    });
  }

  addEventListener(type, handler) { (this.listeners[type] ||= []).push(handler); }
  removeEventListener(type, handler) { this.listeners[type] = (this.listeners[type] || []).filter((fn) => fn !== handler); }
  send(payload) {
    const message = JSON.parse(payload);
    this.sent.push(message);
    if (["activate", "bind", "end", "message"].includes(message.type)) {
      queueMicrotask(() => this.onmessage?.({ data: JSON.stringify({ type: `session.${message.type}.accepted`, success: true }) }));
    }
  }
  close = vi.fn(() => { this.readyState = 3; this.onclose?.(); });
}

function makeStream() {
  const track = { enabled: false, stop: vi.fn() };
  return { track, getAudioTracks: () => [track], getTracks: () => [track] };
}

function installBrowserFakes() {
  const stream = makeStream();
  Object.defineProperty(navigator, "mediaDevices", { configurable: true, value: { getUserMedia: vi.fn(async () => stream) } });
  vi.stubGlobal("RTCPeerConnection", FakePeer);
  vi.stubGlobal("WebSocket", FakeWebSocket);
  return stream;
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

beforeEach(() => {
  vi.clearAllMocks();
  installBrowserFakes();
  mocks.requestAuthenticated.mockResolvedValue(backend());
  mocks.getRealtimeIceConfiguration.mockResolvedValue({ turnEnabled: false, iceServers: [] });
  mocks.createRtcTelemetryMonitor.mockReturnValue({ stateChanged: vi.fn(), connected: vi.fn(), stop: vi.fn() });
  mocks.completeCustomDialogue.mockResolvedValue({ evaluation: { finalScore: 86 } });
  mocks.endInterview.mockResolvedValue({ status: "PROCESSING" });
  mocks.getCustomDialogueEvaluation.mockResolvedValue({ finalScore: 80 });
  mocks.getInterviewReport.mockResolvedValue({ status: "PROCESSING" });
  mocks.advanceCustomDialogueState.mockResolvedValue({ completed: false, controlInstruction: "Ask the next question" });
  mocks.advanceIeltsDialogueState.mockResolvedValue({ completed: false, controlInstruction: "Ask the next IELTS question" });
  mocks.advanceIeltsPart2State.mockResolvedValue({ completed: false, controlInstruction: "Begin the answer" });
  mocks.evaluateCustomDialogueTurn.mockResolvedValue({ overallScore: 82 });
  mocks.evaluateIeltsDialogueTurn.mockResolvedValue({ overallScore: 84 });
  mocks.submitInterviewTurn.mockResolvedValue({ state: { shouldEnd: false, currentTopic: "experience" } });
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("createRealtimeClient main transport flow", () => {
  it("starts free chat over WebRTC and session WebSocket, handles provider events, and stops cleanly", async () => {
    const events = [];
    const remoteStream = { getTracks: () => [{ stop: vi.fn() }] };
    const client = createRealtimeClient({ onEvent: (event) => events.push(event), onRemoteStream: vi.fn() });
    const started = await client.start({ voice: "Tina", speechSpeed: "NATURAL" });
    expect(started).toMatchObject({ sessionId: "local-session" });
    expect(mocks.requestAuthenticated).toHaveBeenCalledWith("/api/scene-sessions", expect.objectContaining({ method: "POST" }));
    expect(events.map((event) => event.type)).toContain("local.connected");
    expect(client.isActive()).toBe(true);

    const peer = globalThis.RTCPeerConnection.instances?.[0];
    // The fake exposes the active transport through the constructor mock's instances only
    // when a test needs it; find it from the constructor call instead.
    const activePeer = vi.mocked(RTCPeerConnection).mock?.instances?.[0] || peer;
    activePeer?.ontrack?.({ streams: [remoteStream] });
    await client.handleEvent({ type: "session.updated", session: { instructions: "ready" } });
    await client.handleEvent({ type: "session.created", session: { id: "provider-session" } });
    await client.handleEvent({ type: "response.audio_transcript.done", item_id: "assistant-1", transcript: "How are you?" });
    expect(events.some((event) => event.type === "local.transcript.final")).toBe(true);

    await client.pause();
    await client.resume();
    await client.interrupt();
    expect(events.map((event) => event.type)).toEqual(expect.arrayContaining(["local.paused", "local.resumed", "local.interrupted"]));
    await client.stop({ notifyBackend: false, reason: "test_stop" });
    expect(events.at(-1)).toMatchObject({ type: "local.ended", reason: "test_stop" });
    expect(client.isActive()).toBe(false);
  });

  it("persists custom scene turns, advances state, records audio, and completes automatically", async () => {
    const events = [];
    const recorder = { startSegment: vi.fn(), stopSegment: vi.fn(async () => new Blob(["wav"])), close: vi.fn() };
    mocks.createPcmWavSegmentRecorder.mockResolvedValue(recorder);
    mocks.advanceCustomDialogueState.mockResolvedValueOnce({ completed: false, controlInstruction: "Continue" }).mockResolvedValueOnce({ completed: true, controlInstruction: "Close the scene" });
    const onRemoteAudioDrain = vi.fn(async () => undefined);
    const client = createRealtimeClient({ sceneId: "scene-1", sceneType: "custom", onEvent: (event) => events.push(event), onRemoteAudioDrain });
    await client.start();
    await client.handleEvent({ type: "session.updated", session: { instructions: "ready" } });
    await client.handleEvent({ type: "response.created" });
    await client.handleEvent({ type: "response.done" });
    await client.handleEvent({ type: "input_audio_buffer.speech_started", item_id: "input-1" });
    await client.handleEvent({ type: "input_audio_buffer.speech_stopped" });
    await client.handleEvent({ type: "conversation.item.input_audio_transcription.completed", item_id: "input-1", transcript: "I need help" });
    await vi.waitFor(() => expect(mocks.advanceCustomDialogueState).toHaveBeenCalledWith("scene-1", "local-session", 1, "I need help"));
    await vi.waitFor(() => expect(mocks.evaluateCustomDialogueTurn).toHaveBeenCalledWith(
      "scene-1",
      "local-session",
      1,
      "I need help",
      expect.any(Blob),
    ));
    await client.handleEvent({ type: "response.done" });
    expect(events).toEqual(expect.arrayContaining([expect.objectContaining({ type: "local.scenario_state" })]));
    expect(recorder.startSegment).toHaveBeenCalled();
    await client.stop({ notifyBackend: false, reason: "test_stop" });
    expect(recorder.close).toHaveBeenCalled();
  });

  it("covers IELTS and interview startup-specific requests and input events", async () => {
    const ieltsEvents = [];
    const ieltsRecorder = { startSegment: vi.fn(), stopSegment: vi.fn(async () => new Blob(["ielts"])), close: vi.fn() };
    mocks.createPcmWavSegmentRecorder.mockResolvedValue(ieltsRecorder);
    mocks.requestAuthenticated.mockResolvedValue(backend({ currentStage: "PART_1", content: { part1: [{ question: "Where do you live?" }] } }));
    const ielts = createRealtimeClient({ sceneId: "ielts-1", sceneType: "ielts", onEvent: (event) => ieltsEvents.push(event) });
    await ielts.start({ voice: "Harvey", speechSpeed: "SLOWER" });
    await ielts.handleEvent({ type: "session.updated", session: { instructions: "ready" } });
    await ielts.handleEvent({ type: "response.created" });
    await ielts.handleEvent({ type: "input_audio_buffer.speech_started", item_id: "i-1" });
    await ielts.handleEvent({ type: "input_audio_buffer.speech_stopped" });
    await ielts.handleEvent({ type: "conversation.item.input_audio_transcription.completed", item_id: "i-1", transcript: "I live in Shanghai" });
    await vi.waitFor(() => expect(mocks.advanceIeltsDialogueState).toHaveBeenCalled());
    expect(mocks.evaluateIeltsDialogueTurn).toHaveBeenCalled();
    await ielts.stop({ notifyBackend: false, reason: "test_stop" });

    const interviewEvents = [];
    mocks.requestAuthenticated.mockResolvedValue(backend());
    const interview = createRealtimeClient({ sceneId: "interview-1", sceneType: "interview", onEvent: (event) => interviewEvents.push(event) });
    await interview.start();
    await interview.handleEvent({ type: "session.updated", session: { instructions: "ready" } });
    await interview.handleEvent({ type: "response.created" });
    await interview.handleEvent({ type: "input_audio_buffer.speech_started", item_id: "interview-input" });
    await interview.handleEvent({ type: "input_audio_buffer.speech_stopped" });
    await interview.handleEvent({ type: "conversation.item.input_audio_transcription.completed", item_id: "interview-input", transcript: "I led the project" });
    await vi.waitFor(() => expect(mocks.submitInterviewTurn).toHaveBeenCalled());
    expect(interviewEvents).toEqual(expect.arrayContaining([expect.objectContaining({ type: "local.interview_state" })]));
    await interview.stop({ notifyBackend: false, reason: "test_stop" });
  });

  it("emits actionable errors when microphone setup fails", async () => {
    navigator.mediaDevices.getUserMedia.mockRejectedValueOnce(Object.assign(new Error("denied"), { name: "NotAllowedError" }));
    const events = [];
    const client = createRealtimeClient({ onEvent: (event) => events.push(event) });
    await expect(client.start()).rejects.toThrow();
    expect(events).toEqual(expect.arrayContaining([expect.objectContaining({ type: "local.mic_error" })]));
    expect(client.isActive()).toBe(false);
  });
});
