import assert from "node:assert/strict";
import {
  buildResponseCreateEvent,
  buildRealtimeSessionConfig,
  createTurnAudioCaptureController,
  buildProviderSessionBindingFrame,
  extractCompletedAssistantMessage,
  isActiveResponseConflict,
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

const slowerKaterina = buildRealtimeSessionConfig({
  systemPrompt: "Coach the learner.",
  voice: "Katerina",
  model: "qwen3.5-omni-flash-realtime",
  speechSpeed: "SLOWER",
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

assert.equal(slowerKaterina.voice, "Katerina");
assert.match(slowerKaterina.instructions, /70 English words per minute/);
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
