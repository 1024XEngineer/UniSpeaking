import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import {
  buildResponseCreateEvent,
  buildScenarioResponseRequest,
  buildRealtimeSessionConfig,
  buildRealtimeStartPayload,
  createTurnAudioCaptureController,
  buildProviderSessionBindingFrame,
  assistantResponseInvitesReply,
  extractProviderSessionId,
  extractCompletedAssistantMessage,
  isActiveResponseConflict,
  isRealtimeChannelOpen,
  normalizeProviderEvent,
  normalizeBaseUrl,
  normalizeIceTransportPolicy,
  realtimeIceTransportPolicy,
  realtimePeerConnectionConfig,
  realtimeTurnEnabled,
  resolveRealtimePeerConnectionConfig,
  collectIceConnectionDiagnostics,
  releaseRealtimeTransport,
  waitForIceGathering,
  websocketUrl,
} from "../src/websocket/realtimeClient.js";

assert.equal(
  assistantResponseInvitesReply("Understood. Just the steak and vegetables?"),
  true,
);
assert.equal(
  assistantResponseInvitesReply('Would you like anything else?"'),
  true,
);
assert.equal(assistantResponseInvitesReply("Have a safe trip!"), false);

assert.equal(isRealtimeChannelOpen({ readyState: "open" }), true);
assert.equal(isRealtimeChannelOpen({ readyState: "closing" }), false);
assert.equal(isRealtimeChannelOpen(null), false);

assert.deepEqual(
  buildProviderSessionBindingFrame("local-session-1", {
    type: "session.created",
    session: { id: "sess_qwen_1" },
  }),
  {
    type: "bind",
    sessionId: "local-session-1",
    providerSessionId: "sess_qwen_1",
  },
);
assert.equal(
  buildProviderSessionBindingFrame("local-session-1", { type: "session.created", session: {} }),
  null,
);
assert.equal(
  extractProviderSessionId({ providerSessionId: "rti_qiniu_1" }),
  "rti_qiniu_1",
);
assert.equal(
  extractProviderSessionId({ type: "session.created", session: { id: "sess_qwen_1" } }),
  "sess_qwen_1",
);
assert.deepEqual(
  buildRealtimeStartPayload("offer-sdp"),
  {
    offerSdp: "offer-sdp",
    voice: "Tina",
    translationEnabled: true,
  },
);
assert.deepEqual(
  buildRealtimeStartPayload("ielts-offer", { ielts: true }),
  {
    offerSdp: "ielts-offer",
    voiceId: "Tina",
    translationEnabled: true,
  },
);
assert.deepEqual(
  buildRealtimeStartPayload("teacher-offer", { voice: "Harvey" }),
  {
    offerSdp: "teacher-offer",
    voice: "Harvey",
    translationEnabled: true,
  },
);
assert.deepEqual(
  buildRealtimeStartPayload("examiner-offer", { ielts: true, voice: "Mione" }),
  {
    offerSdp: "examiner-offer",
    voiceId: "Mione",
    translationEnabled: true,
  },
);

assert.deepEqual(
  buildResponseCreateEvent({
    id: "turn-2",
    instructions: "Ask exactly: Where do you live?",
  }),
  {
    event_id: "turn-2",
    type: "response.create",
    response: {
      instructions: "Ask exactly: Where do you live?",
      modalities: ["text", "audio"],
    },
  },
);

assert.deepEqual(
  buildScenarioResponseRequest({
    completed: false,
    controlInstruction: "Ask for the payment method.",
  }),
  {
    closing: false,
    instructions: "Ask for the payment method.",
  },
);
assert.deepEqual(
  buildScenarioResponseRequest({
    completed: true,
    controlInstruction: "Give one concise closing response.",
  }),
  {
    closing: true,
    instructions: "Give one concise closing response.",
  },
);

assert.equal(normalizeBaseUrl("/backend"), "/backend");
assert.equal(normalizeBaseUrl("https://api.example.com/backend/"), "https://api.example.com/backend");
assert.equal(
  websocketUrl("/backend", "signed-token", "https://app.example.com"),
  "wss://app.example.com/backend/ws/session-messages?access_token=signed-token",
);
assert.equal(realtimeIceTransportPolicy(), "all");
assert.equal(realtimeTurnEnabled(), false);
assert.equal(normalizeIceTransportPolicy("relay"), "relay");
assert.equal(normalizeIceTransportPolicy("invalid"), "all");
assert.equal(realtimePeerConnectionConfig().iceTransportPolicy, "all");

let turnConfigurationRequests = 0;
const unchangedPeerConfiguration = await resolveRealtimePeerConnectionConfig({
  turnEnabled: false,
  loadConfiguration: async () => {
    turnConfigurationRequests += 1;
    throw new Error("must not load");
  },
});
assert.equal(turnConfigurationRequests, 0);
assert.equal(unchangedPeerConfiguration.iceTransportPolicy, "all");

const grayPeerConfiguration = await resolveRealtimePeerConnectionConfig({
  turnEnabled: true,
  loadConfiguration: async (forceRelay) => {
    assert.equal(forceRelay, false);
    return {
      turnEnabled: true,
      iceServers: [{
        urls: ["turn:turn.example.cn:443?transport=udp"],
        username: "temporary-user",
        credential: "temporary-credential",
      }],
    };
  },
});
assert.equal(grayPeerConfiguration.iceTransportPolicy, "all");
assert.equal(grayPeerConfiguration.iceServers.at(-1).username, "temporary-user");

const relayPeerConfiguration = await resolveRealtimePeerConnectionConfig({
  turnEnabled: true,
  forceRelay: true,
  loadConfiguration: async () => ({
    turnEnabled: true,
    iceServers: [{
      urls: "turn:turn.example.cn:443?transport=udp",
      username: "temporary-user",
      credential: "temporary-credential",
    }],
  }),
});
assert.equal(relayPeerConfiguration.iceTransportPolicy, "relay");
assert.equal(relayPeerConfiguration.iceServers.length, 1);

await assert.rejects(
  resolveRealtimePeerConnectionConfig({
    forceRelay: true,
    loadConfiguration: async () => ({ turnEnabled: false, iceServers: [] }),
  }),
  (error) => error.code === "WEBRTC_TURN_NOT_CONFIGURED",
);

const fallbackPeerConfiguration = await resolveRealtimePeerConnectionConfig({
  turnEnabled: true,
  loadConfiguration: async () => { throw new Error("TURN endpoint unavailable"); },
});
assert.equal(fallbackPeerConfiguration.iceTransportPolicy, "all");

assert.deepEqual(
  extractCompletedAssistantMessage({
    type: "response.audio_transcript.done",
    item_id: "item-audio",
    transcript: "This is the complete audio transcript.",
  }),
  {
    id: "item-audio",
    text: "This is the complete audio transcript.",
  },
);

assert.deepEqual(
  extractCompletedAssistantMessage({
    type: "response.done",
    response: {
      id: "response-1",
      output: [{
        id: "item-response",
        role: "assistant",
        content: [
          { type: "audio", transcript: "Complete " },
          { type: "text", text: "fallback." },
        ],
      }],
    },
  }),
  {
    id: "item-response",
    text: "Complete fallback.",
  },
);

assert.equal(
  extractCompletedAssistantMessage({
    type: "response.audio_transcript.delta",
    item_id: "item-delta",
    delta: "partial",
  }),
  null,
);

assert.equal(
  isActiveResponseConflict({
    type: "error",
    error: { message: "Conversation already has an active response" },
  }),
  true,
);
assert.equal(
  isActiveResponseConflict({
    type: "error",
    error: { message: "Invalid session configuration" },
  }),
  false,
);

assert.deepEqual(
  normalizeProviderEvent({
    type: "rtid.error",
    message: "upstream realtime failed",
  }),
  {
    type: "error",
    providerEventType: "rtid.error",
    message: "upstream realtime failed",
    error: { message: "upstream realtime failed" },
  },
);
assert.equal(normalizeProviderEvent({ type: "session.updated" }).type, "session.updated");
assert.equal(
  normalizeProviderEvent({ type: "rtid.error", error: "quota exhausted" }).error.message,
  "quota exhausted",
);

const defaultQiniuSession = buildRealtimeSessionConfig({
  systemPrompt: "Coach the learner.",
});
assert.equal(defaultQiniuSession.voice, "Tina");

const slowerTina = buildRealtimeSessionConfig({
  systemPrompt: "Coach the learner.",
  voice: "Tina",
  model: "qwen3.5-omni-plus-realtime",
  speechSpeed: "SLOWER",
});
const managedCherry = buildRealtimeSessionConfig({
  systemPrompt: "Coach the learner.",
  voice: "Cherry",
  includeVoice: false,
});
const fasterHarvey = buildRealtimeSessionConfig({
  systemPrompt: "Coach the learner.",
  voice: "Harvey",
  model: "qwen3.5-omni-flash-realtime",
  speechSpeed: "FASTER",
});
const deterministicIeltsPart = buildRealtimeSessionConfig({
  systemPrompt: "Ask only the prepared IELTS question.",
  model: "qwen3.5-omni-flash-realtime",
  automaticTurnResponses: false,
  silenceDurationMs: 3_000,
  turnDetectionType: "server_vad",
});
const partTwoSession = buildRealtimeSessionConfig({
  systemPrompt: "Conduct IELTS Part 2.",
  model: "qwen3.5-omni-flash-realtime",
  automaticTurnResponses: false,
  silenceDurationMs: 3_000,
  interruptResponse: false,
});

assert.equal(slowerTina.voice, "Tina");
assert.equal("voice" in managedCherry, false);
assert.match(managedCherry.instructions, /Coach the learner/);
assert.match(slowerTina.instructions, /70 English words per minute/);
assert.equal(fasterHarvey.voice, "Harvey");
assert.match(fasterHarvey.instructions, /210 English words per minute/);
assert.equal(fasterHarvey.input_audio_transcription.model, "qwen3-asr-flash-realtime");
assert.equal(fasterHarvey.turn_detection.type, "semantic_vad");
assert.equal(fasterHarvey.turn_detection.silence_duration_ms, 600);
assert.equal(deterministicIeltsPart.turn_detection.type, "server_vad");
assert.equal(deterministicIeltsPart.turn_detection.silence_duration_ms, 3_000);
assert.equal(deterministicIeltsPart.turn_detection.create_response, false);
assert.equal(partTwoSession.turn_detection.create_response, false);
assert.equal(partTwoSession.turn_detection.interrupt_response, false);

const interviewSession = buildRealtimeSessionConfig({
  systemPrompt: "Conduct a structured interview.",
  model: "qwen3.5-omni-flash-realtime",
  automaticTurnResponses: false,
  silenceDurationMs: 3_000,
  interruptResponse: true,
  vadThreshold: 0.8,
  prefixPaddingMs: 1_000,
});
assert.equal(interviewSession.turn_detection.type, "semantic_vad");
assert.equal(interviewSession.turn_detection.silence_duration_ms, 3_000);
assert.equal(interviewSession.turn_detection.create_response, false);
assert.equal(interviewSession.turn_detection.interrupt_response, true);
assert.equal(interviewSession.turn_detection.threshold, 0.8);
assert.equal(interviewSession.turn_detection.prefix_padding_ms, 1_000);

let segmentStartCount = 0;
let segmentStopCount = 0;
const expectedAudio = { type: "audio/wav" };
const turnAudioCapture = createTurnAudioCaptureController({
  startSegment() {
    segmentStartCount += 1;
  },
  async stopSegment() {
    segmentStopCount += 1;
    return expectedAudio;
  },
});

assert.equal(turnAudioCapture.start(), true);
assert.equal(turnAudioCapture.start(), false);
assert.equal(segmentStartCount, 1);
assert.equal(turnAudioCapture.stop(), true);
assert.equal(turnAudioCapture.start(), false);
assert.equal(await turnAudioCapture.take(), expectedAudio);
assert.equal(turnAudioCapture.start(), true);
assert.equal(segmentStartCount, 2);
assert.equal(await turnAudioCapture.take(), expectedAudio);
assert.equal(segmentStopCount, 2);

const transportCalls = [];
releaseRealtimeTransport({
  audioSender: {
    replaceTrack(track) {
      transportCalls.push(["sender", track]);
      return Promise.resolve();
    },
  },
  localStream: {
    getTracks: () => [{ stop: () => transportCalls.push(["local-track"]) }],
  },
  remoteStreams: [{
    getTracks: () => [{ stop: () => transportCalls.push(["remote-track"]) }],
  }],
  channels: [{
    onopen: () => {},
    onmessage: () => {},
    onerror: () => {},
    onclose: () => {},
    close: () => transportCalls.push(["channel"]),
  }],
  peer: {
    ontrack: () => {},
    ondatachannel: () => {},
    onconnectionstatechange: () => {},
    close: () => transportCalls.push(["peer"]),
  },
});
assert.deepEqual(transportCalls, [
  ["sender", null],
  ["local-track"],
  ["remote-track"],
  ["channel"],
  ["peer"],
]);

const previousWindow = globalThis.window;
globalThis.window = {
  setTimeout: globalThis.setTimeout,
  clearTimeout: globalThis.clearTimeout,
};
try {
  const partialGathering = await waitForIceGathering({
    iceGatheringState: "gathering",
    signalingState: "stable",
    localDescription: {
      sdp: "v=0\r\na=candidate:1 1 UDP 1 192.0.2.1 5000 typ host\r\n",
    },
    addEventListener: () => {},
    removeEventListener: () => {},
  }, { timeoutMs: 5 });
  assert.equal(partialGathering.complete, false);
  assert.equal(partialGathering.timedOut, true);
  assert.equal(partialGathering.candidates.host, 1);
  assert.equal(partialGathering.candidates.protocols.udp, 1);

  await assert.rejects(
    waitForIceGathering({
      iceGatheringState: "gathering",
      signalingState: "stable",
      localDescription: { sdp: "v=0\r\n" },
      addEventListener: () => {},
      removeEventListener: () => {},
    }, { timeoutMs: 5 }),
    (error) => {
      assert.equal(error.code, "WEBRTC_ICE_GATHERING_NO_CANDIDATE");
      assert.deepEqual(Object.keys(error.realtimeDiagnostics).sort(), ["candidates", "iceGatheringState"]);
      return true;
    },
  );
} finally {
  globalThis.window = previousWindow;
}

const statsDiagnostics = await collectIceConnectionDiagnostics({
  async getStats() {
    return new Map([
      ["local-1", {
        id: "local-1",
        type: "local-candidate",
        candidateType: "relay",
        protocol: "udp",
        relayProtocol: "udp",
      }],
      ["remote-1", {
        id: "remote-1",
        type: "remote-candidate",
        candidateType: "host",
      }],
      ["pair-1", {
        type: "candidate-pair",
        state: "succeeded",
        nominated: true,
        localCandidateId: "local-1",
        remoteCandidateId: "remote-1",
      }],
    ]);
  },
}, {
  host: 0,
  srflx: 0,
  prflx: 0,
  relay: 1,
  unknown: 0,
  protocols: { udp: 1, tcp: 0, unknown: 0 },
});
assert.deepEqual(statsDiagnostics.selectedCandidatePair, {
  state: "succeeded",
  nominated: true,
  localCandidateType: "relay",
  localProtocol: "udp",
  relayProtocol: "udp",
  remoteCandidateType: "host",
});
assert.equal(statsDiagnostics.relayProtocols.udp, 1);

const realtimeSource = await readFile(
  new URL("../src/websocket/realtimeClient.js", import.meta.url),
  "utf8",
);
const stopSource = realtimeSource.slice(
  realtimeSource.indexOf("async function performStop"),
  realtimeSource.indexOf("function stop(options"),
);
assert.ok(
  stopSource.indexOf("releaseCurrentTransport();")
    < stopSource.indexOf("await waitForPendingOperations"),
  "realtime transport must be released before business operations drain",
);
assert.match(realtimeSource, /let lifecycleState = "idle";/);
assert.match(realtimeSource, /if \(startPromise\) return startPromise;/);
assert.match(
  realtimeSource,
  /const manualTurnResponses = Boolean\(ieltsSceneId \|\| interviewSceneId\);/,
  "custom scenes must use provider-managed VAD responses",
);
assert.match(
  realtimeSource,
  /automaticTurnResponses: !manualTurnResponses/,
  "custom scene VAD must create responses without waiting for state advancement",
);
assert.match(
  realtimeSource,
  /vadThreshold: interviewSceneId \? 0\.8 : customSceneId \? 0\.4 : 0\.5/,
  "custom scene VAD must retain short contextual answers",
);
assert.match(
  realtimeSource,
  /prefixPaddingMs: interviewSceneId \|\| customSceneId \? 1_000 : 500/,
  "custom scene VAD must preserve the leading audio of short turns",
);
assert.match(
  realtimeSource,
  /inputReady = !customSceneId && \(!manualTurnResponses \|\| interviewSceneId\);/,
  "custom scene scoring capture must stay closed before the opening response",
);
assert.match(
  realtimeSource,
  /if \(!customSceneId \|\| !responsePending\) turnAudioCapture\?\.start\(\);/,
  "custom scene barge-in must not record mixed assistant audio for scoring",
);
const customSceneTurnSource = realtimeSource.slice(
  realtimeSource.indexOf("if (customSceneId && persisted)"),
  realtimeSource.indexOf("if (interviewSceneId && persisted)"),
);
assert.doesNotMatch(
  customSceneTurnSource,
  /await\s+turnAudioCapture\?\.take/,
  "custom scene state advancement must not wait for WAV capture",
);
assert.doesNotMatch(
  customSceneTurnSource,
  /await\s+stateOperation/,
  "custom scene provider responses must not wait for the state machine",
);
assert.match(
  customSceneTurnSource,
  /applyScenarioState\(scenarioState, \{ requestResponse: false \}\)/,
  "custom scene state observations must not create normal turn responses",
);
assert.match(
  customSceneTurnSource,
  /if \(turnNo !== learnerTurnNo \|\| customSceneTurnPending\) return;/,
  "scene completion must wait until state analysis reaches the latest user turn",
);
assert.doesNotMatch(
  customSceneTurnSource,
  /requestTurnResponse/,
  "custom scene completion must not append a second provider closing response",
);
assert.doesNotMatch(realtimeSource, /console\.warn\([^\n]*(?:offerSdp|answerSdp|accessToken|credential)/);

const appSource = await readFile(new URL("../src/controller/App.jsx", import.meta.url), "utf8");
const freeChatStopSource = appSource.slice(
  appSource.indexOf("const stopConversation = async ("),
  appSource.indexOf("useEffect(() =>", appSource.indexOf("const stopConversation = async (")),
);
assert.ok(
  freeChatStopSource.indexOf("await client?.stop") < freeChatStopSource.indexOf("setInCall(false)"),
  "free chat must remain non-restartable until client.stop completes",
);
assert.match(freeChatStopSource, /stopPromiseRef\.current/);
assert.match(freeChatStopSource, /detachRemoteAudio\(\)/);
assert.match(
  realtimeSource,
  /await waitForChannel\(channel, peer\);[\s\S]*?await sendSessionFrame\("activate"\);[\s\S]*?scheduleQuotaDeadline/,
  "daily quota must start only after the WebRTC data channel connects",
);
assert.match(
  realtimeSource,
  /type: "local\.quota_exhausted"[\s\S]*?stop\(\{ reason: "quota_exhausted" \}\)/,
  "the client must reuse the normal session completion path at the quota deadline",
);
assert.match(
  realtimeSource,
  /const operation = \["activate", "bind", "end"\]\.includes\(type\) \? type : "message";/,
  "session activation acknowledgements must be correlated independently",
);
assert.match(
  realtimeSource,
  /pending\.reject\(createRealtimeError\([\s\S]*?ack\.code \|\| "SESSION_SOCKET_ERROR"/,
  "session WebSocket business error codes must reach the realtime startup failure",
);

assert.match(appSource, /const detachSceneRemoteAudio = \(\) => \{/);
assert.match(appSource, /sceneAnalyticsRef\.current\?\.abandon\("COMPONENT_UNMOUNT"\);\s+detachSceneRemoteAudio\(\);/);
const customSceneConversationSource = appSource.slice(
  appSource.indexOf("function CustomSceneConversation("),
  appSource.indexOf("const sentenceWordPattern"),
);
assert.match(
  customSceneConversationSource,
  /const \[connectionFailed, setConnectionFailed\] = useState\(false\);/,
  "custom scene must track retryable connection failures",
);
assert.match(
  customSceneConversationSource,
  /if \(!connectionFailed \|\| reconnecting \|\| !client\) return;\s+void connectRealtime\(client, \{ retry: true \}\);/,
  "custom scene reconnect must reuse the current realtime client and prevent duplicate attempts",
);
assert.match(
  customSceneConversationSource,
  /\{reconnecting \? "正在重新连接" : "重新连接"\}/,
  "custom scene must expose an explicit reconnect action after startup failure",
);
assert.match(
  customSceneConversationSource,
  /event\.type === "local\.quota_exhausted"[\s\S]*?endConversation\("quota_exhausted"\)/,
  "custom scenes must finish through their existing report flow when quota expires",
);
assert.doesNotMatch(
  customSceneConversationSource,
  /setTimeout\([^)]*connectRealtime/,
  "custom scene reconnect must remain user initiated",
);

const ieltsSource = await readFile(
  new URL("../src/component/ielts/IeltsModule.jsx", import.meta.url),
  "utf8",
);
assert.match(ieltsSource, /const finishingRef = useRef\(false\);/);
assert.match(ieltsSource, /if \(finishingRef\.current\) return;\s+finishingRef\.current = true;/);
assert.match(ieltsSource, /ieltsAnalyticsRef\.current\?\.abandon\("COMPONENT_UNMOUNT"\);[\s\S]*?detachIeltsRemoteAudio\(\);[\s\S]*?clientRef\.current = null;/);
assert.match(ieltsSource, /local\.quota_exhausted[\s\S]*?finishRef\.current\?\.\("quota_exhausted"\)/);

const interviewSource = await readFile(
  new URL("../src/component/interview/InterviewModule.jsx", import.meta.url),
  "utf8",
);
assert.match(interviewSource, /const detachInterviewRemoteAudio = \(\) => \{/);
assert.match(interviewSource, /interviewAnalyticsRef\.current\?\.abandon\("COMPONENT_UNMOUNT"\);\s+detachInterviewRemoteAudio\(\);/);
assert.match(interviewSource, /const abandon = async \(\) => \{\s+if \(endingRef\.current\) return;\s+endingRef\.current = true;/);
assert.match(interviewSource, /local\.quota_exhausted[\s\S]*?endConversation\("quota_exhausted"\)/);

console.log("Realtime event normalization checks passed.");
