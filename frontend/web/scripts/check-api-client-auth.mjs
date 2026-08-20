import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import {
  getCurrentUser,
  getUserPreference,
  saveAuthSession,
} from "../src/infrastructure/http/apiClient.js";
import { flushTelemetry } from "../src/telemetry/clientTelemetry.js";

test("an unauthenticated auth probe is informational telemetry", async () => {
  const previousWindow = globalThis.window;
  const previousFetch = globalThis.fetch;
  const localStorage = new Map([["unispeaking.accessToken", "expired-token"]]);
  const sessionStorage = new Map();
  const telemetryRequests = [];
  globalThis.window = {
    location: { href: "http://localhost/login", origin: "http://localhost" },
    localStorage: {
      getItem: (key) => localStorage.get(key) || null,
      setItem: (key, value) => localStorage.set(key, value),
      removeItem: (key) => localStorage.delete(key),
    },
    sessionStorage: {
      getItem: (key) => sessionStorage.get(key) || null,
      setItem: (key, value) => sessionStorage.set(key, value),
    },
  };
  globalThis.fetch = async (url, options) => {
    if (String(url).endsWith("/api/telemetry/events")) {
      telemetryRequests.push(JSON.parse(options.body));
      return new Response(null, { status: 202 });
    }
    return new Response(JSON.stringify({ success: false, code: "AUTHENTICATION_REQUIRED" }), {
      status: 401,
      headers: { "Content-Type": "application/json" },
    });
  };

  try {
    await assert.rejects(() => getCurrentUser());
    await flushTelemetry();

    const event = telemetryRequests.flatMap((request) => request.events)
      .find((candidate) => candidate.attributes.api_path === "/api/auth/me");
    assert.equal(event.severity, "INFO");
    assert.equal(event.attributes.http_status, 401);
    assert.equal(event.attributes.outcome, "unauthenticated");
  } finally {
    globalThis.window = previousWindow;
    globalThis.fetch = previousFetch;
  }
});

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
  assert.match(
    appSource,
    /const currentUser = await getCurrentUser\(\);\s*const \[preference, profile\] = await Promise\.all/,
  );
  assert.match(appSource, /clearAuthSession\(bootstrapToken\);/);
});

test("the production entrypoint keeps the latest controller application", async () => {
  const indexSource = await readFile(new URL("../index.html", import.meta.url), "utf8");
  assert.match(indexSource, /src="\/src\/controller\/main\.jsx"/);
  assert.doesNotMatch(indexSource, /src="\/src\/main\.jsx"/);
});
