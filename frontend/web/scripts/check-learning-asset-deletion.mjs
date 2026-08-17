import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { deleteLearningAsset } from "../src/infrastructure/http/apiClient.js";

test("learning asset deletion uses the authenticated DELETE endpoint", async () => {
  const previousWindow = globalThis.window;
  const previousFetch = globalThis.fetch;
  const requests = [];
  globalThis.window = {
    localStorage: {
      getItem: () => "asset-token",
      removeItem: () => {},
    },
  };
  globalThis.fetch = async (url, options) => {
    requests.push({ url, options });
    return new Response(JSON.stringify({ success: true, data: null }), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  };

  try {
    await deleteLearningAsset("custom/a b");
    assert.equal(requests[0].url, "/api/custom-scenes/custom%2Fa%20b/assets");
    assert.equal(requests[0].options.method, "DELETE");
    assert.equal(requests[0].options.headers.Authorization, "Bearer asset-token");
  } finally {
    globalThis.window = previousWindow;
    globalThis.fetch = previousFetch;
  }
});

test("asset screen waits for deletion and handles success and failure", async () => {
  const appSource = await readFile(new URL("../src/controller/App.jsx", import.meta.url), "utf8");
  assert.match(appSource, /await deleteLearningAsset\(deletedSceneId\)/);
  assert.match(appSource, /setRecords\(\(current\) => current\.filter/);
  assert.match(appSource, /setDeleteError\(error instanceof Error/);
  assert.match(appSource, /deleting \? "正在删除" : "确认删除"/);
});

test("unfinished learning assets open their detail and restart the full flow", async () => {
  const appSource = await readFile(new URL("../src/controller/App.jsx", import.meta.url), "utf8");
  assert.doesNotMatch(appSource, /disabled=\{!selected\.latestSessionId\}/);
  assert.match(appSource, /您还没有完成模拟对话/);
  assert.match(appSource, /function AssetPracticeButton\(\{ scene, onPractice \}\)/);
  assert.doesNotMatch(appSource, /asset-practice-menu__popover/);
  assert.match(appSource, /startTraining\(scene, "speak", \{ standaloneSpeak: false, returnPage: "assets" \}\)/);
  assert.match(appSource, /<nav className="stepper" aria-label="练习进度">/);
});
