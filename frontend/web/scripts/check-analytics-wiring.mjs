import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const appSource = await readFile(new URL("../src/controller/App.jsx", import.meta.url), "utf8");
const ieltsSource = await readFile(new URL("../src/component/ielts/IeltsModule.jsx", import.meta.url), "utf8");
const interviewSource = await readFile(new URL("../src/component/interview/InterviewModule.jsx", import.meta.url), "utf8");

const expectLifecycle = (source, trackerName) => {
  for (const method of ["attempt", "started", "fail", "complete", "abandon", "setVisible"]) {
    assert.match(source, new RegExp(`${trackerName}\\.current(?:\\?\\.|\\.)${method}\\(`));
  }
};

test("free chat and scene realtime lifecycles use the shared analytics adapter", () => {
  assert.match(appSource, /import \{ analytics \} from "\.\.\/analytics\/analyticsClient\.js";/);
  assert.match(appSource, /freeChatAnalyticsRef\.current = analytics\.training\(\{ mode: "FREE_CHAT", pageCode: "conversation" \}\)/);
  assert.match(appSource, /sceneAnalyticsRef\.current = analytics\.training\(\{ mode: "SCENE", pageCode: "scene-training" \}\)/);
  expectLifecycle(appSource, "freeChatAnalyticsRef");
  expectLifecycle(appSource, "sceneAnalyticsRef");
  assert.match(appSource, /freeChatAnalyticsRef\.current\?\.pause\(\)/);
  assert.match(appSource, /freeChatAnalyticsRef\.current\?\.resume\(\)/);
  assert.match(appSource, /sceneAnalyticsRef\.current\?\.pause\(\)/);
  assert.match(appSource, /sceneAnalyticsRef\.current\?\.resume\(\)/);
});

test("IELTS realtime lifecycle records start, terminal outcome, and active visibility", () => {
  assert.match(ieltsSource, /import \{ analytics \} from "\.\.\/\.\.\/analytics\/analyticsClient\.js";/);
  assert.match(ieltsSource, /ieltsAnalyticsRef\.current = analytics\.training\(\{ mode: "IELTS", pageCode: "ielts-training" \}\)/);
  expectLifecycle(ieltsSource, "ieltsAnalyticsRef");
});

test("interview realtime lifecycle records start, terminal outcome, pause, and visibility", () => {
  assert.match(interviewSource, /import \{ analytics \} from "\.\.\/\.\.\/analytics\/analyticsClient\.js";/);
  assert.match(interviewSource, /interviewAnalyticsRef\.current = analytics\.training\(\{ mode: "INTERVIEW", pageCode: "interview-training" \}\)/);
  expectLifecycle(interviewSource, "interviewAnalyticsRef");
  assert.match(interviewSource, /interviewAnalyticsRef\.current\?\.pause\(\)/);
  assert.match(interviewSource, /interviewAnalyticsRef\.current\?\.resume\(\)/);
});

test("explicit mode choices and learning asset opens are instrumented without custom page views", () => {
  for (const mode of ["SCENE", "FREE_CHAT", "INTERVIEW", "IELTS"]) {
    assert.match(appSource, new RegExp(`trackModeSelection\\(\\{ mode: "${mode}"`));
  }
  assert.match(appSource, /analytics\.trackLearningAsset\(\{ mode: "SCENE", pageCode: "scene-assets" \}, "REPORT"\)/);
  assert.match(appSource, /analytics\.trackLearningAsset\(\{ mode: "IELTS", pageCode: "ielts-assets" \}, "REPORT"\)/);
  assert.match(appSource, /analytics\.trackLearningAsset\(\{ mode: "INTERVIEW", pageCode: "interview-assets" \}, "REPORT"\)/);
  assert.match(appSource, /analytics\.trackPageView\(url\.pathname\)/);
  assert.match(appSource, /analytics\.trackPageView\(window\.location\.pathname\)/);
  assert.doesNotMatch(appSource, /track\(["']page_view["']/);
  assert.doesNotMatch(ieltsSource, /track\(["']page_view["']/);
  assert.doesNotMatch(interviewSource, /track\(["']page_view["']/);
});

test("authenticated web sessions set and clear the shared Umami Distinct ID", () => {
  assert.match(appSource, /analytics\.setDistinctId\(user\?\.id \|\| null\)/);
});

test("IELTS ignores a late realtime start after the session effect was cleaned up", () => {
  assert.match(ieltsSource, /\.then\(\(started\) => \{\s+if \(cancelled\) return;/);
});

test("free chat ignores a late realtime start after its client generation was cleaned up", () => {
  assert.match(appSource, /const startGeneration = clientGenerationRef\.current;/);
  assert.match(appSource, /if \(clientRef\.current !== client \|\| clientGenerationRef\.current !== startGeneration\) return;/);
});
