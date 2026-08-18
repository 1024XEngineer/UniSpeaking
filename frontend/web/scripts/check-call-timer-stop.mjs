import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const appSource = await readFile(
  new URL("../src/controller/App.jsx", import.meta.url),
  "utf8",
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
  /<CallTimer paused=\{paused\} state=\{ended \|\| error \? "ended" : "active"\} stopped=\{ending\} \/>/,
  "The scene call timer must stop as soon as report generation begins",
);
assert.match(
  timerSource,
  /if \(stopped \|\| state === "ended"\) return undefined;/,
  "CallTimer must clear its interval when the call stops",
);
assert.match(
  timerSource,
  /\}, \[state, stopped\]\);/,
  "CallTimer must react when the stopped state changes",
);

console.log("Scene call timer completion check passed.");
