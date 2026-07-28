import assert from "node:assert/strict";
import { paths, resolveRoute } from "../src/router.js";

const route = (pathname, search = "") => resolveRoute({ pathname, search });

const cases = [
  [paths.app.conversation, "conversation"],
  [paths.app.scenes, "scenes"],
  [paths.assets.latest, "assets"],
  [paths.ielts.root, "ielts"],
  [paths.ielts.assets.history, "ielts-assets"],
  [paths.ielts.step("part1", "home", "report"), "ielts"],
  [paths.ielts.step("mock", "random", "session"), "ielts"],
  [paths.interview.root, "interview"],
  [paths.interview.live, "interview"],
  [paths.interview.reportPartial, "interview"],
  [paths.interview.assets.trends, "interview-assets"],
  [paths.interview.assets.record("product-manager"), "interview-assets"],
];

for (const [pathname, expectedPage] of cases) {
  assert.equal(route(pathname).page, expectedPage, `${pathname} should resolve to ${expectedPage}`);
}

assert.equal(route("/training").canonicalPath, paths.scenes.training);
assert.equal(route("/result").canonicalPath, paths.scenes.result);
assert.equal(route("/ielts-assets").canonicalPath, paths.ielts.assets.root);
assert.equal(route("/interview/unknown").canonicalPath, paths.interview.root);
assert.equal(route("/unknown").canonicalPath, paths.root);
assert.equal(route(paths.interview.assets.record("岗位 1")).interviewRoute.record, "岗位 1");
assert.equal(route(paths.assets.latest).assetView, "detail");
assert.equal(route("/assets", "?view=detail").assetView, "detail");

console.log(`Route contract passed: ${cases.length + 8} assertions`);
