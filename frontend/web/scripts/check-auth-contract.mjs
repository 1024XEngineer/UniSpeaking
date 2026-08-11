import test from "node:test";
import assert from "node:assert/strict";
import {
  buildAuthApiUrl,
  issueEmailChallenge,
  registerWithEmail,
  validateRegistrationCredentials,
} from "../src/userAuthApi.js";

test("rejects registration credentials before issuing an email challenge", () => {
  assert.equal(validateRegistrationCredentials("person@example.com", "short"), "WEAK_PASSWORD");
  assert.equal(validateRegistrationCredentials("not-an-email", "correct-password"), "INVALID_EMAIL");
  assert.equal(validateRegistrationCredentials("person@example.com", "correct-password"), null);
});

test("turns backend password validation into a user-facing message", async () => {
  const previousFetch = globalThis.fetch;
  globalThis.fetch = async () => new Response(JSON.stringify({
    success: false,
    code: "VALIDATION_ERROR",
    message: "password 个数必须在12和200之间",
  }), { status: 400, headers: { "Content-Type": "application/json" } });
  try {
    await assert.rejects(
      () => issueEmailChallenge("person@example.com", "local-human-verified"),
      (error) => error.message === "密码至少需要 12 位。",
    );
  } finally {
    globalThis.fetch = previousFetch;
  }
});

test("recovers a completed registration when the one-time challenge is submitted again", async () => {
  const previousFetch = globalThis.fetch;
  const previousWindow = globalThis.window;
  const storage = new Map();
  globalThis.window = {
    localStorage: {
      setItem: (key, value) => storage.set(key, value),
      removeItem: (key) => storage.delete(key),
      getItem: (key) => storage.get(key) || null,
    },
  };
  globalThis.fetch = async (path) => {
    if (path === "/api/auth/email/register") {
      return new Response(JSON.stringify({ success: false, code: "CHALLENGE_INVALID", message: "CHALLENGE_INVALID" }), {
        status: 400,
        headers: { "Content-Type": "application/json" },
      });
    }
    if (path === "/api/auth/email/password/login") {
      return new Response(JSON.stringify({ success: true, data: { email: "person@example.com" } }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    }
    if (path === "/api/auth/login") {
      return new Response(JSON.stringify({ success: true, data: { accessToken: "legacy-token", user: { email: "person@example.com" } } }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    }
    throw new Error(`unexpected request ${path}`);
  };
  try {
    const result = await registerWithEmail({
      email: "person@example.com",
      password: "correct-password",
      challengeId: "00000000-0000-0000-0000-000000000001",
      code: "123456",
    });
    assert.equal(result.user.email, "person@example.com");
    assert.equal(storage.get("unispeaking.accessToken"), "legacy-token");
  } finally {
    globalThis.fetch = previousFetch;
    globalThis.window = previousWindow;
  }
});

test("email challenge uses the real auth endpoint and same-origin credentials", async () => {
  const previousFetch = globalThis.fetch;
  let request;
  globalThis.fetch = async (path, options) => {
    request = { path, options };
    return new Response(JSON.stringify({ data: { challengeId: "00000000-0000-0000-0000-000000000001" } }), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  };
  try {
    const result = await issueEmailChallenge("person@example.com", "aliyun-token");
    assert.equal(request.path, "/api/auth/email/challenges");
    assert.equal(request.options.credentials, "include");
    assert.match(request.options.body, /humanVerificationToken/);
    assert.equal(result.challengeId, "00000000-0000-0000-0000-000000000001");
  } finally {
    globalThis.fetch = previousFetch;
  }
});

test("email auth URLs honor the configured production backend prefix", () => {
  assert.equal(buildAuthApiUrl("/api/auth/email/challenges", "/backend"), "/backend/api/auth/email/challenges");
});
