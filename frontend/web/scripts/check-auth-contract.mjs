import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import {
  buildAuthApiUrl,
  issueEmailChallenge,
  issuePasswordResetChallenge,
  loginWithPassword,
  registerWithEmail,
  resetPasswordWithEmail,
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

test("does not turn an invalid registration challenge into a password login", async () => {
  const previousFetch = globalThis.fetch;
  const requests = [];
  globalThis.fetch = async (path) => {
    requests.push(path);
    return new Response(JSON.stringify({ success: false, code: "CHALLENGE_INVALID", message: "CHALLENGE_INVALID" }), {
      status: 400,
      headers: { "Content-Type": "application/json" },
    });
  };
  try {
    await assert.rejects(() => registerWithEmail({
      email: "person@example.com",
      password: "correct-password",
      challengeId: "00000000-0000-0000-0000-000000000001",
      code: "123456",
    }));
    assert.deepEqual(requests, ["/api/auth/email/register/token"]);
  } finally {
    globalThis.fetch = previousFetch;
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

test("password login sends the human verification token", async () => {
  const previousFetch = globalThis.fetch;
  let loginRequest;
  globalThis.fetch = async (path, options) => {
    if (path === "/api/auth/email/password/login/token") {
      loginRequest = { path, options };
      return new Response(JSON.stringify({ success: false, code: "INVALID_CREDENTIALS" }), {
        status: 401,
        headers: { "Content-Type": "application/json" },
      });
    }
    throw new Error(`unexpected request ${path}`);
  };
  try {
    await assert.rejects(() => loginWithPassword(
      "person@example.com",
      "correct-password",
      "aliyun-login-token",
    ));
    assert.deepEqual(JSON.parse(loginRequest.options.body), {
      email: "person@example.com",
      password: "correct-password",
      humanVerificationToken: "aliyun-login-token",
    });
  } finally {
    globalThis.fetch = previousFetch;
  }
});

test("email auth URLs honor the configured production backend prefix", () => {
  assert.equal(buildAuthApiUrl("/api/auth/email/challenges", "/backend"), "/backend/api/auth/email/challenges");
});

test("password reset challenge uses the dedicated endpoint and human verification token", async () => {
  const previousFetch = globalThis.fetch;
  let request;
  globalThis.fetch = async (path, options) => {
    request = { path, options };
    return new Response(JSON.stringify({ data: { challengeId: "00000000-0000-0000-0000-000000000002" } }), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  };
  try {
    const result = await issuePasswordResetChallenge("person@example.com", "aliyun-reset-token");
    assert.equal(request.path, "/api/auth/email/password-reset/challenges");
    assert.equal(request.options.credentials, "include");
    assert.deepEqual(JSON.parse(request.options.body), {
      email: "person@example.com",
      humanVerificationToken: "aliyun-reset-token",
    });
    assert.equal(result.challengeId, "00000000-0000-0000-0000-000000000002");
  } finally {
    globalThis.fetch = previousFetch;
  }
});

test("password reset submits the challenge code and new password", async () => {
  const previousFetch = globalThis.fetch;
  let request;
  globalThis.fetch = async (path, options) => {
    request = { path, options };
    return new Response(null, { status: 204 });
  };
  try {
    await resetPasswordWithEmail({
      email: "person@example.com",
      password: "correct-new-password",
      challengeId: "00000000-0000-0000-0000-000000000002",
      code: "123456",
    });
    assert.equal(request.path, "/api/auth/email/password-reset");
    assert.equal(request.options.credentials, "include");
    assert.deepEqual(JSON.parse(request.options.body), {
      email: "person@example.com",
      password: "correct-new-password",
      challengeId: "00000000-0000-0000-0000-000000000002",
      code: "123456",
    });
  } finally {
    globalThis.fetch = previousFetch;
  }
});

test("password reset translates an unknown identity into a user-facing recovery message", async () => {
  const previousFetch = globalThis.fetch;
  globalThis.fetch = async () => new Response(JSON.stringify({
    success: false,
    code: "IDENTITY_NOT_FOUND",
    message: "IDENTITY_NOT_FOUND",
  }), { status: 400, headers: { "Content-Type": "application/json" } });
  try {
    await assert.rejects(
      () => resetPasswordWithEmail({
        email: "missing@example.com",
        password: "correct-new-password",
        challengeId: "00000000-0000-0000-0000-000000000002",
        code: "123456",
      }),
      (error) => error.message === "该邮箱尚未注册，请先创建账号。",
    );
  } finally {
    globalThis.fetch = previousFetch;
  }
});

test("login UI exposes the verified email password reset flow", async () => {
  const source = await readFile(new URL("../src/controller/App.jsx", import.meta.url), "utf8");
  for (const expected of [
    'setMode("reset")',
    'reset-email-challenge',
    '重置密码',
    '确认新密码',
    'autoComplete="new-password"',
    '密码已重置，请使用新密码登录。',
  ]) {
    assert.match(source, new RegExp(expected.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")));
  }
});

test("login UI routes password login through human verification", async () => {
  const source = await readFile(new URL("../src/controller/App.jsx", import.meta.url), "utf8");
  assert.match(source, /<HumanVerification buttonId=\{captchaButtonId\} onVerify=\{verifyAndIssueChallenge\} \/>/);
  assert.match(source, /loginWithPassword\(normalizedEmail, password, captchaVerifyParam\)/);
});
