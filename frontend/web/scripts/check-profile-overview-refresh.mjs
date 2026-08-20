import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const appSource = await readFile(
  new URL("../src/controller/App.jsx", import.meta.url),
  "utf8",
);

assert.match(
  appSource,
  /const refreshProfileOverview = async \(month\) => \{/,
  "App must expose a shared profile overview refresh function",
);
assert.match(
  appSource,
  /profileOverviewRequestVersionRef/,
  "Profile refreshes must guard against stale responses",
);
assert.match(
  appSource,
  /if \(page !== \"profile\" \|\| !user\?\.id\) return;/,
  "Entering the personal overview must refresh its data",
);
assert.match(
  appSource,
  /void refreshProfileOverview\(\);\n  \}, \[page, user\?\.id\]\);/,
  "The personal overview refresh must react to page changes",
);
assert.match(
  appSource,
  /void refreshProfileOverview\(\);\n    void synchronizeAchievements\(\{ revealNotifications: true \}\);/,
  "Free conversation completion must refresh the profile overview",
);
assert.match(
  appSource,
  /const completeScenePractice = \(completed, evaluation = null, completedSessionId = null\) => \{[\s\S]*?showResult\(completed, evaluation, completedSessionId\);/,
  "Scene completion must use the shared result path",
);
assert.doesNotMatch(
  appSource,
  /if \(evaluation\) \{\s*void getProfileOverview\(\)\.then\(setProfileOverview\)/,
  "Profile refresh must not depend on an evaluation payload",
);

console.log("Profile overview refresh checks passed.");
