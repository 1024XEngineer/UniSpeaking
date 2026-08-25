import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const stylesSource = await readFile(new URL("../src/common/styles.css", import.meta.url), "utf8");

assert.match(stylesSource, /@media \(max-width: 760px\)[\s\S]*?\.auth-layout \{[\s\S]*?grid-template-columns: 1fr;/, "Authentication must collapse to one column on mobile");
assert.match(stylesSource, /\.auth-panel \{[\s\S]*?width: min\(100%, 520px\);/, "Authentication panel must fit narrow screens");
assert.match(stylesSource, /\.auth-panel input \{[\s\S]*?font-size: 16px;/, "Authentication inputs must avoid mobile browser zoom");
assert.match(stylesSource, /env\(safe-area-inset-bottom/, "Authentication must respect the device safe area");
assert.doesNotMatch(stylesSource, /\.mobile-tabbar/, "Authenticated application navigation must remain desktop-only");
assert.doesNotMatch(stylesSource, /Phase-two mobile web/, "Authenticated personal pages must not have dedicated mobile overrides");

console.log("Mobile authentication responsive contract passed: 6 assertions");
