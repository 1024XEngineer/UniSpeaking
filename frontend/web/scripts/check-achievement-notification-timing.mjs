import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const appSource = await readFile(new URL("../src/App.jsx", import.meta.url), "utf8");
const notificationSource = await readFile(
  new URL("../src/AchievementNotifications.jsx", import.meta.url),
  "utf8",
);

function sourceBetween(source, startMarker, endMarker) {
  const start = source.indexOf(startMarker);
  const end = source.indexOf(endMarker, start + startMarker.length);
  assert.notEqual(start, -1, `Missing start marker: ${startMarker}`);
  assert.notEqual(end, -1, `Missing end marker: ${endMarker}`);
  return source.slice(start, end);
}

assert.doesNotMatch(
  appSource,
  /onAchievementSync/,
  "Business components must not expose process-level achievement popup triggers",
);

const sceneGeneration = sourceBetween(
  appSource,
  "const generate = async",
  "const startGeneratedTraining = async",
);
const turnEvaluation = sourceBetween(
  appSource,
  'event.type === "local.turn_evaluation"',
  'event.type === "local.scenario_state"',
);
const sentenceEvaluation = sourceBetween(
  appSource,
  "const submitGeneratedRead = async",
  "const playReadDemo =",
);

for (const [name, workflow] of [
  ["scene generation", sceneGeneration],
  ["turn evaluation", turnEvaluation],
  ["sentence evaluation", sentenceEvaluation],
]) {
  assert.doesNotMatch(
    workflow,
    /synchronizeAchievements|revealNotifications/,
    `${name} must not reveal achievement notifications before session completion`,
  );
}

const revealCalls = appSource.match(
  /synchronizeAchievements\(\{ revealNotifications: true \}\)/g,
) || [];
assert.equal(
  revealCalls.length,
  2,
  "Only the scenario report and free-conversation end callbacks may reveal notifications",
);

const showResult = sourceBetween(
  appSource,
  "const showResult =",
  "const setMainPage =",
);
assert.match(showResult, /synchronizeAchievements\(\{ revealNotifications: true \}\)/);
assert.match(
  appSource,
  /onSessionEnded=\{\(\) => \{[^\n]*synchronizeAchievements\(\{ revealNotifications: true \}\)/,
  "Free-conversation completion must release pending notifications",
);

assert.match(
  notificationSource,
  /synchronizeAchievements = useCallback\(\(\{ revealNotifications = false \} = \{\}\)/,
  "Achievement synchronization must remain silent by default",
);
assert.match(
  notificationSource,
  /if \(shouldRevealNotifications\) \{/,
  "Pending notifications must only enter the popup queue after an explicit reveal",
);

console.log("Achievement notification timing checks passed.");
