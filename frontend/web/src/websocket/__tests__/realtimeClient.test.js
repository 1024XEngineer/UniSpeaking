import { afterEach, describe, expect, it, vi } from "vitest";
import {
  assistantResponseInvitesReply,
  buildProviderSessionBindingFrame,
  buildRealtimeSessionConfig,
  buildRealtimeStartPayload,
  buildResponseCreateEvent,
  buildScenarioResponseRequest,
  collectIceConnectionDiagnostics,
  createTurnAudioCaptureController,
  defaultIceServers,
  extractCompletedAssistantMessage,
  extractProviderSessionId,
  isActiveResponseConflict,
  isMicFailure,
  isRealtimeChannelOpen,
  micFailureMessage,
  normalizeBaseUrl,
  normalizeIceTransportPolicy,
  normalizeProviderEvent,
  realtimeFailureMessage,
  realtimePeerConnectionConfig,
  realtimeTurnEnabled,
  releaseRealtimeTransport,
  resolveRealtimePeerConnectionConfig,
  waitForIceGathering,
  websocketUrl,
} from "../realtimeClient.js";

afterEach(() => vi.useRealTimers());

describe("realtime client pure contracts", () => {
  it("maps microphone and realtime failures to actionable messages", () => {
    const cases = [
      ["NotAllowedError", "权限被拒绝"], ["SecurityError", "权限被拒绝"],
      ["NotFoundError", "未检测到"], ["DevicesNotFoundError", "未检测到"],
      ["NotReadableError", "被其他应用占用"], ["AudioCaptureError", "被其他应用占用"],
      ["OverconstrainedError", "不满足采集要求"],
    ];
    cases.forEach(([name, text]) => {
      const error = { name };
      expect(isMicFailure(error)).toBe(true);
      expect(micFailureMessage(error)).toContain(text);
    });
    expect(isMicFailure({ name: "Other" })).toBe(false);
    expect(micFailureMessage({ name: "Other" })).toBeNull();
    expect(realtimeFailureMessage(new Error("AllocationQuota.FreeTierOnly"))).toContain("免费额度");
    expect(realtimeFailureMessage(new Error("QWEN_SIGNALING_FAILED"))).toContain("拒绝了连接");
    expect(realtimeFailureMessage(new Error("ICE connection failed"))).toContain("实时网络通道");
    expect(realtimeFailureMessage()).toBe("无法开始实时对话");
  });

  it("normalizes URLs, ICE policies, payloads, and session configuration", () => {
    expect(normalizeBaseUrl()).toBe("");
    expect(normalizeBaseUrl(" /backend/ ")).toBe("/backend");
    expect(normalizeBaseUrl("https://api.example.com/")).toBe("https://api.example.com");
    expect(() => normalizeBaseUrl("ftp://example.com")).toThrow("HTTP 或 HTTPS");
    expect(websocketUrl("/backend", "token", "https://app.example.com")).toBe("wss://app.example.com/backend/ws/session-messages?access_token=token");
    expect(websocketUrl("https://api.example.com/base/", "")).toBe("wss://api.example.com/base/ws/session-messages");
    expect(normalizeIceTransportPolicy(" RELAY ")).toBe("relay");
    expect(normalizeIceTransportPolicy("bad")).toBe("all");
    expect(realtimeTurnEnabled()).toBe(false);
    expect(realtimePeerConnectionConfig([{ urls: "stun:test" }], "relay")).toEqual({ iceServers: [{ urls: "stun:test" }], iceTransportPolicy: "relay" });
    expect(buildRealtimeStartPayload("offer", { ielts: false, voice: "  " })).toEqual({ offerSdp: "offer", voice: "Tina", translationEnabled: true });
    expect(buildRealtimeStartPayload("offer", { ielts: true, voice: "Harvey" })).toEqual({ offerSdp: "offer", voiceId: "Harvey", translationEnabled: true });
    expect(buildResponseCreateEvent({ id: "event-1", instructions: "  say hi  " })).toEqual({ event_id: "event-1", type: "response.create", response: { instructions: "say hi", modalities: ["text", "audio"] } });
    expect(buildResponseCreateEvent()).toMatchObject({ type: "response.create" });
    expect(buildScenarioResponseRequest(null)).toEqual({ closing: false, instructions: "" });
    expect(buildScenarioResponseRequest({ completed: 1, controlInstruction: " next " })).toEqual({ closing: true, instructions: "next" });
    expect(buildRealtimeSessionConfig({ systemPrompt: "system", voice: "Tina", speechSpeed: "slower", silenceDurationMs: 100, prefixPaddingMs: -1 })).toMatchObject({ voice: "Tina", instructions: expect.stringContaining("system"), turn_detection: { type: "semantic_vad", silence_duration_ms: 600, prefix_padding_ms: 0 } });
    expect(buildRealtimeSessionConfig({ topic: "topic", includeVoice: false, model: "other-model", automaticTurnResponses: false, turnDetectionType: "server_vad", vadThreshold: 0 })).toMatchObject({ instructions: expect.stringContaining("topic"), turn_detection: { type: "server_vad", threshold: 0.5, create_response: false }, });
  });

  it("handles provider IDs, assistant message variants, and normalized errors", () => {
    expect(extractProviderSessionId({ providerSessionId: " p " })).toBe("p");
    expect(extractProviderSessionId({ session: { id: "s" } })).toBe("s");
    expect(extractProviderSessionId({})).toBeNull();
    expect(buildProviderSessionBindingFrame(" local ", { session: { id: "provider" } })).toEqual({ type: "bind", sessionId: "local", providerSessionId: "provider" });
    expect(buildProviderSessionBindingFrame("", { providerSessionId: "p" })).toBeNull();
    expect(extractCompletedAssistantMessage({ type: "response.audio_transcript.done", item_id: "a", transcript: "hello" })).toEqual({ id: "a", text: "hello" });
    expect(extractCompletedAssistantMessage({ type: "response.text.done", response_id: "b", text: "text" })).toEqual({ id: "b", text: "text" });
    expect(extractCompletedAssistantMessage({ type: "response.content_part.done", event_id: "c", part: { text: "part" } })).toEqual({ id: "c", text: "part" });
    expect(extractCompletedAssistantMessage({ type: "response.output_item.done", item: { id: "d", role: "assistant", content: [{ text: " first " }, { transcript: "second" }] } })).toEqual({ id: "d", text: "first second" });
    expect(extractCompletedAssistantMessage({ type: "response.done", response: { output: [{ role: "user", content: [] }] } })).toBeNull();
    expect(extractCompletedAssistantMessage({ type: "other" })).toBeNull();
    expect(assistantResponseInvitesReply("Are you ready?\" ")).toBe(true);
    expect(assistantResponseInvitesReply("No, thanks.")).toBe(false);
    expect(isActiveResponseConflict({ type: "error", error: { message: "conversation already has an active response" } })).toBe(true);
    expect(isActiveResponseConflict({ type: "other", message: "conversation already has an active response" })).toBe(false);
    expect(normalizeProviderEvent({ type: "rtid.error", error: "bad" })).toMatchObject({ type: "error", providerEventType: "rtid.error", error: { message: "bad" } });
    const normal = { type: "ready" };
    expect(normalizeProviderEvent(normal)).toBe(normal);
  });

  it("coordinates turn audio capture state", async () => {
    const recorder = { startSegment: vi.fn(), stopSegment: vi.fn(async () => "audio") };
    const capture = createTurnAudioCaptureController(recorder);
    expect(capture.start()).toBe(true);
    expect(capture.start()).toBe(false);
    expect(capture.stop()).toBe(true);
    expect(capture.stop()).toBe(false);
    await expect(capture.take()).resolves.toBe("audio");
    expect(capture.start()).toBe(true);
    await expect(capture.take()).resolves.toBe("audio");
    expect(createTurnAudioCaptureController(null).start()).toBe(false);
    expect(recorder.startSegment).toHaveBeenCalledTimes(2);
  });

  it("summarizes ICE gathering and safe transport cleanup", async () => {
    const completePeer = { iceGatheringState: "complete", localDescription: { sdp: "a=candidate:1 1 udp 1 127.0.0.1 1 typ host\r\n" } };
    await expect(waitForIceGathering(completePeer)).resolves.toMatchObject({ complete: true, candidates: { host: 1 } });

    vi.useFakeTimers();
    const listeners = {};
    const peer = {
      iceGatheringState: "gathering",
      signalingState: "stable",
      localDescription: { sdp: "" },
      addEventListener: vi.fn((type, fn) => { listeners[type] = fn; }),
      removeEventListener: vi.fn(),
    };
    const pending = waitForIceGathering(peer, { timeoutMs: 100 });
    listeners.icecandidate({ candidate: { type: "relay", protocol: "udp" } });
    peer.iceGatheringState = "complete";
    listeners.icegatheringstatechange({});
    await expect(pending).resolves.toMatchObject({ complete: true, candidates: { relay: 1, protocols: { udp: 1 } } });

    const timeoutPeer = { iceGatheringState: "gathering", localDescription: { sdp: "" }, addEventListener() {}, removeEventListener() {} };
    const noCandidate = waitForIceGathering(timeoutPeer, { timeoutMs: 10 });
    vi.advanceTimersByTime(10);
    await expect(noCandidate).rejects.toMatchObject({ code: "WEBRTC_ICE_GATHERING_NO_CANDIDATE" });
    const candidateTimeoutPeer = { iceGatheringState: "gathering", localDescription: { sdp: "" }, addEventListener(type, fn) { if (type === "icecandidate") fn({ candidate: "candidate:1 1 UDP 1 1 1 typ host" }); }, removeEventListener() {} };
    const timedOut = waitForIceGathering(candidateTimeoutPeer, { timeoutMs: 10 });
    vi.advanceTimersByTime(10);
    await expect(timedOut).resolves.toMatchObject({ timedOut: true, candidates: { host: 1 } });

    const track = { stop: vi.fn() };
    const channel = { close: vi.fn(), onopen: vi.fn(), onmessage: vi.fn(), onerror: vi.fn(), onclose: vi.fn() };
    const peerToClose = { close: vi.fn(), ontrack: vi.fn(), ondatachannel: vi.fn(), onconnectionstatechange: vi.fn() };
    const sender = { replaceTrack: vi.fn(() => Promise.resolve()) };
    releaseRealtimeTransport({ peer: peerToClose, channels: [channel], localStream: { getTracks: () => [track] }, remoteStreams: [{ getTracks: () => [track] }], audioSender: sender });
    expect(sender.replaceTrack).toHaveBeenCalledWith(null);
    expect(track.stop).toHaveBeenCalledTimes(2);
    expect(channel.close).toHaveBeenCalled();
    expect(peerToClose.close).toHaveBeenCalled();
    expect(isRealtimeChannelOpen({ readyState: "open" })).toBe(true);
    expect(isRealtimeChannelOpen({ readyState: "closed" })).toBe(false);
  });

  it("collects selected candidate diagnostics and falls back safely", async () => {
    const reports = [
      { type: "local-candidate", id: "l", candidateType: "relay", protocol: "udp", relayProtocol: "udp" },
      { type: "remote-candidate", id: "r", candidateType: "srflx" },
      { type: "candidate-pair", state: "succeeded", nominated: true, localCandidateId: "l", remoteCandidateId: "r" },
      { type: "candidate-pair", state: "waiting", nominated: false },
    ];
    const result = await collectIceConnectionDiagnostics({ getStats: async () => reports }, { relay: 1 });
    expect(result).toMatchObject({ selectedCandidatePair: { localCandidateType: "relay", remoteCandidateType: "srflx", relayProtocol: "udp" }, relayProtocols: { udp: 1 } });
    await expect(collectIceConnectionDiagnostics({ getStats: async () => { throw new Error("stats"); } })).resolves.toMatchObject({ selectedCandidatePair: null });
    expect(defaultIceServers()).toEqual(expect.any(Array));
    await expect(resolveRealtimePeerConnectionConfig({ turnEnabled: false, forceRelay: false })).resolves.toEqual(realtimePeerConnectionConfig());
    await expect(resolveRealtimePeerConnectionConfig({ forceRelay: true, loadConfiguration: async () => ({ turnEnabled: false, iceServers: [] }) })).rejects.toMatchObject({ code: "WEBRTC_TURN_NOT_CONFIGURED" });
    await expect(resolveRealtimePeerConnectionConfig({ turnEnabled: true, forceRelay: false, loadConfiguration: async () => { throw new Error("not configured"); } })).resolves.toEqual(realtimePeerConnectionConfig());
  });
});
