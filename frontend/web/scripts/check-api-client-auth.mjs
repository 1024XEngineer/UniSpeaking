import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import {
  getAccessToken,
  getUserPreference,
  saveAuthSession,
} from "../src/infrastructure/http/apiClient.js";

test("a stale 401 must not clear a newer authentication session", async () => {
  const previousWindow = globalThis.window;
  const previousFetch = globalThis.fetch;
  const localStorage = new Map();
  const sessionStorage = new Map([["unispeaking.accessToken", "stale-token"]]);
  globalThis.window = {
    localStorage: {
      getItem: (key) => localStorage.get(key) || null,
      setItem: (key, value) => localStorage.set(key, value),
      removeItem: (key) => localStorage.delete(key),
    },
    sessionStorage: {
      getItem: (key) => sessionStorage.get(key) || null,
      setItem: (key, value) => sessionStorage.set(key, value),
      removeItem: (key) => sessionStorage.delete(key),
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
    assert.equal(sessionStorage.get("unispeaking.accessToken"), "fresh-token");
  } finally {
    globalThis.window = previousWindow;
    globalThis.fetch = previousFetch;
  }
});

test("web authentication uses tab storage and removes legacy persistent tokens", () => {
  const previousWindow = globalThis.window;
  const localStorage = new Map([["unispeaking.accessToken", "legacy-token"]]);
  const sessionStorage = new Map();
  globalThis.window = {
    localStorage: {
      getItem: (key) => localStorage.get(key) || null,
      setItem: (key, value) => localStorage.set(key, value),
      removeItem: (key) => localStorage.delete(key),
    },
    sessionStorage: {
      getItem: (key) => sessionStorage.get(key) || null,
      setItem: (key, value) => sessionStorage.set(key, value),
      removeItem: (key) => sessionStorage.delete(key),
    },
  };

  try {
    assert.equal(getAccessToken(), null);
    assert.equal(localStorage.has("unispeaking.accessToken"), false);

    saveAuthSession({ accessToken: "tab-token" });
    assert.equal(sessionStorage.get("unispeaking.accessToken"), "tab-token");
    assert.equal(localStorage.has("unispeaking.accessToken"), false);
  } finally {
    globalThis.window = previousWindow;
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
