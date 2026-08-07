import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const appSource = await readFile(new URL("../src/controller/App.jsx", import.meta.url), "utf8");
const stylesSource = await readFile(new URL("../src/common/styles.css", import.meta.url), "utf8");

const achievementStart = appSource.indexOf("function AchievementSystem()");
const achievementEnd = appSource.indexOf("function Overview(", achievementStart);
assert.notEqual(achievementStart, -1, "AchievementSystem must exist");
assert.notEqual(achievementEnd, -1, "AchievementSystem boundary must exist");
const achievementSource = appSource.slice(achievementStart, achievementEnd);

assert.match(
  achievementSource,
  /const \[expandedSeriesId, setExpandedSeriesId\] = useState\(null\)/,
  "Achievement overview must track a single expanded series",
);
assert.match(
  achievementSource,
  /const currentMilestone = itemMilestones\.find/,
  "Collapsed cards must derive the current highest milestone",
);
assert.match(
  achievementSource,
  /aria-expanded=\{expanded\}/,
  "Series cards must expose their expanded state",
);
assert.match(
  achievementSource,
  /aria-controls=\{detailId\}/,
  "Series cards must reference the matching level panel",
);
assert.match(
  achievementSource,
  /\{expanded && \([\s\S]*className="achievement-level-panel"/,
  "The complete milestone list must only mount after expansion",
);
assert.equal(
  (achievementSource.match(/itemMilestones\.map/g) || []).length,
  1,
  "Milestones must not also render inside collapsed overview cards",
);
assert.doesNotMatch(
  achievementSource,
  /achievement-milestones/,
  "The previous always-visible vertical milestone list must stay removed",
);
assert.match(
  achievementSource,
  /setFilter\(item\); setExpandedSeriesId\(null\)/,
  "Changing filters must close the expanded series",
);

for (let level = 0; level <= 5; level += 1) {
  assert.match(
    stylesSource,
    new RegExp(`\\.achievement-level-${level} \\{`),
    `Level ${level} must have a distinct visual token`,
  );
}

assert.match(
  stylesSource,
  /\.achievement-level-track \{[^}]*overflow-x: auto/,
  "Narrow screens must be able to scroll the horizontal level track",
);
assert.match(
  stylesSource,
  /\.achievement-level-track ol \{[^}]*display: flex/,
  "All milestone levels must remain on one horizontal row",
);
assert.match(stylesSource, /\.achievement-level-track li\.is-current/);
assert.match(stylesSource, /\.achievement-level-track li\.is-next/);
assert.match(stylesSource, /\.achievement-level-track li\.is-locked/);
assert.match(
  stylesSource,
  /\.achievement-level-panel \{[^}]*grid-column: 1 \/ -1/,
  "Expanded milestone panels must span the full achievement grid",
);

console.log("Achievement page layout checks passed.");
