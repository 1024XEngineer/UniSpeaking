import assert from "node:assert/strict";
import { paths, resolveRoute } from "../src/router.js";

const route = (pathname, search = "") => resolveRoute({ pathname, search });

const cases = [
  [paths.app.conversation, "conversation"],
  [paths.conversation.session("scene free/1"), "conversation"],
  [paths.app.scenes, "scenes"],
  [paths.scenes.word("custom_1"), "scenes"],
  [paths.scenes.phrase("custom_1"), "scenes"],
  [paths.scenes.sentence("custom_1"), "scenes"],
  [paths.scenes.session("custom_1", "session_1"), "scenes"],
  [paths.scenes.assets("custom_1"), "assets"],
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
assert.equal(route(paths.conversation.session("free chat/1")).conversationSessionId, "free chat/1");
assert.equal(route(paths.scenes.word("custom scene/1")).sceneId, "custom scene/1");
assert.equal(route(paths.scenes.phrase("custom_1")).training.stage, "phrase");
assert.equal(route(paths.scenes.sentence("custom_1")).training.initialStep, "read");
assert.equal(route(paths.scenes.session("custom_1", "session/1")).sessionId, "session/1");
assert.equal(route(paths.scenes.assets("custom_1")).assetSceneId, "custom_1");

console.log(`Route contract passed: ${cases.length + 14} assertions`);
