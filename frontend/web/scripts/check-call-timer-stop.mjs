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
  /<CallTimer paused=\{paused\} state=\{ended \? "ended" : error \? "error" : "active"\} stopped=\{ending\} \/>/,
  "The scene call timer must stop as soon as report generation begins",
);
assert.match(
  timerSource,
  /const terminal = stopped \|\| state === "ended" \|\| state === "error";/,
  "CallTimer must treat realtime failures as terminal",
);
assert.match(
  timerSource,
  /if \(terminal\) return undefined;/,
  "CallTimer must clear its interval when the call stops or fails",
);
assert.match(
  freeConversationSource,
  /setCallState\("error"\);[\s\S]*?<CallTimer state=\{callState\}/,
  "Free conversation must pass realtime failure state to CallTimer",
);
assert.match(
  interviewSource,
  /if \(state === "ended" \|\| state === "error"\) return undefined;/,
  "InterviewTimer must clear its interval after realtime failure",
);
assert.match(
  interviewSource,
  /<InterviewTimer paused=\{paused\} state=\{ending \? "ended" : error \? "error" : "active"\} \/>/,
  "Interview session must pass realtime failure state to InterviewTimer",
);
assert.match(
  ieltsSource,
  /setRealtimeState\("error"\);[\s\S]*?if \(ending \|\| realtimeState === "error"\) return undefined;/,
  "IELTS timer must stop after realtime connection failure",
);

console.log("Realtime call timer stop checks passed.");
