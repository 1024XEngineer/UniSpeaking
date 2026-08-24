import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const appSource = await readFile(
  new URL("../src/controller/App.jsx", import.meta.url),
  "utf8",
);
const interviewSource = await readFile(
  new URL("../src/component/interview/InterviewModule.jsx", import.meta.url),
  "utf8",
);
const ieltsSource = await readFile(
  new URL("../src/component/ielts/IeltsModule.jsx", import.meta.url),
  "utf8",
);

const freeConversationStart = appSource.indexOf("function Conversation(");
const freeConversationEnd = appSource.indexOf(
  "function SceneCategoryTag(",
  freeConversationStart,
);

assert.notEqual(freeConversationStart, -1, "Conversation must exist");
assert.notEqual(freeConversationEnd, -1, "Conversation boundary must exist");

const freeConversationSource = appSource.slice(
  freeConversationStart,
  freeConversationEnd,
);

const conversationStart = appSource.indexOf("function CustomSceneConversation(");
const conversationEnd = appSource.indexOf(
  "const sentenceWordPattern",
  conversationStart,
);

assert.notEqual(conversationStart, -1, "CustomSceneConversation must exist");
assert.notEqual(conversationEnd, -1, "CustomSceneConversation boundary must exist");

const conversationSource = appSource.slice(conversationStart, conversationEnd);
const timerStart = appSource.indexOf("function CallTimer(");
const timerEnd = appSource.indexOf("function CallControls(", timerStart);

assert.notEqual(timerStart, -1, "CallTimer must exist");
assert.notEqual(timerEnd, -1, "CallTimer boundary must exist");

const timerSource = appSource.slice(timerStart, timerEnd);

assert.match(
  conversationSource,
  /<CallTimer paused=\{paused\} state=\{ended \? "ended" : error \? "error" : realtimeState\} stopped=\{ending\} \/>/,
  "The scene call timer must stop as soon as report generation begins",
);
assert.match(
  timerSource,
  /const terminal = stopped \|\| state === "ended" \|\| state === "error";/,
  "CallTimer must treat realtime failures as terminal",
);
assert.match(
  timerSource,
  /const startedAt = useRef\(null\);[\s\S]*?const running = !terminal && state !== "connecting";/,
  "CallTimer must wait for a successful realtime connection before recording its start time",
);
assert.match(
  timerSource,
  /if \(!running\) return undefined;[\s\S]*?startedAt\.current \?\?= Date\.now\(\);/,
  "CallTimer must start only after realtime leaves the connecting state",
);
assert.match(
  timerSource,
  /return \(\) => window\.clearInterval\(interval\);/,
  "CallTimer must clear its interval when the call stops or fails",
);
assert.match(
  freeConversationSource,
  /setCallState\("error"\);[\s\S]*?<CallTimer state=\{callState\}/,
  "Free conversation must pass realtime failure state to CallTimer",
);
assert.match(
  conversationSource,
  /setRealtimeState\("connecting"\);[\s\S]*?event\.type === "local\.connected"[\s\S]*?setRealtimeState\("connected"\);/,
  "Custom scene timing must begin only after local.connected",
);
assert.match(
  interviewSource,
  /const running = state !== "connecting" && state !== "ended" && state !== "error";[\s\S]*?if \(!running\) return undefined;/,
  "InterviewTimer must wait for a successful realtime connection",
);
assert.match(
  interviewSource,
  /event\.type === "local\.connected"[\s\S]*?setRealtimeState\("connected"\);[\s\S]*?<InterviewTimer paused=\{paused\} state=\{ending \? "ended" : error \? "error" : realtimeState\} \/>/,
  "Interview session must start its timer from local.connected",
);
assert.match(
  ieltsSource,
  /setRealtimeState\("connected"\);[\s\S]*?if \(ending \|\| realtimeState !== "connected"\) return undefined;/,
  "IELTS timer must run only after realtime connects",
);

console.log("Realtime call timer stop checks passed.");
