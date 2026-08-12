import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { getUserPreference, saveAuthSession } from "../src/infrastructure/http/apiClient.js";

test("a stale 401 must not clear a newer authentication session", async () => {
  const previousWindow = globalThis.window;
  const previousFetch = globalThis.fetch;
  const storage = new Map([["unispeaking.accessToken", "stale-token"]]);
  globalThis.window = {
    localStorage: {
      getItem: (key) => storage.get(key) || null,
      setItem: (key, value) => storage.set(key, value),
      removeItem: (key) => storage.delete(key),
    },
  };
  globalThis.fetch = async () => {
    // Simulate the bootstrap request returning after registration has saved a
    // fresh token for the same browser tab.
    saveAuthSession({ accessToken: "fresh-token" });
    return new Response(JSON.stringify({ success: false, code: "AUTHENTICATION_REQUIRED" }), {
      status: 401,
      headers: { "Content-Type": "application/json" },
    });
  };

  try {
    await assert.rejects(() => getUserPreference());
    assert.equal(storage.get("unispeaking.accessToken"), "fresh-token");
  } finally {
    globalThis.window = previousWindow;
    globalThis.fetch = previousFetch;
  }
});

test("bootstrap cleanup is bound to the token captured before its requests", async () => {
  const appSource = await readFile(new URL("../src/controller/App.jsx", import.meta.url), "utf8");
  assert.match(appSource, /const bootstrapToken = getAccessToken\(\);/);
  assert.match(appSource, /clearAuthSession\(bootstrapToken\);/);
});

test("the production entrypoint keeps the latest controller application", async () => {
  const indexSource = await readFile(new URL("../index.html", import.meta.url), "utf8");
  assert.match(indexSource, /src="\/src\/controller\/main\.jsx"/);
  assert.doesNotMatch(indexSource, /src="\/src\/main\.jsx"/);
});
