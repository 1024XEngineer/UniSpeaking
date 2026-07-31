import assert from "node:assert/strict";
import {
  buildRealtimeSessionConfig,
  extractCompletedAssistantMessage,
  isActiveResponseConflict,
  normalizeBaseUrl,
  websocketUrl,
} from "../src/realtimeClient.js";

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

assert.equal(slowerKaterina.voice, "Katerina");
assert.match(slowerKaterina.instructions, /70 English words per minute/);
assert.equal(fasterHarvey.voice, "Harvey");
assert.match(fasterHarvey.instructions, /210 English words per minute/);
assert.equal(fasterHarvey.input_audio_transcription.model, "qwen3-asr-flash-realtime");
assert.equal(fasterHarvey.turn_detection.type, "semantic_vad");
assert.equal(fasterHarvey.turn_detection.silence_duration_ms, 600);

console.log("Realtime event normalization checks passed.");
