import assert from "node:assert/strict";
import {
  buildResponseCreateEvent,
  buildScenarioResponseRequest,
  buildRealtimeSessionConfig,
  buildRealtimeStartPayload,
  createTurnAudioCaptureController,
  buildProviderSessionBindingFrame,
  extractProviderSessionId,
  extractCompletedAssistantMessage,
  isActiveResponseConflict,
  normalizeProviderEvent,
  normalizeBaseUrl,
  websocketUrl,
} from "../src/websocket/realtimeClient.js";

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

console.log("Realtime event normalization checks passed.");
