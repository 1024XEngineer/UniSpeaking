import { clearAuthSession, revokeWebSession, saveAuthSession } from "./infrastructure/http/apiClient.js";

const API_BASE = (import.meta.env?.VITE_BACKEND_URL || "").replace(/\/$/, "");

export function buildAuthApiUrl(path, base = API_BASE) {
  return `${String(base || "").replace(/\/$/, "")}${path}`;
}

class UserAuthApiError extends Error {
  constructor(code, message, options) {
    super(message, options);
    this.name = "UserAuthApiError";
    this.code = code;
  }
}

const messages = {
  HUMAN_VERIFICATION_REQUIRED: "请先完成人机验证。",
  INVALID_CREDENTIALS: "邮箱或密码错误。",
  CHALLENGE_INVALID: "验证码无效或已过期，请重新获取。",
  IDENTITY_ALREADY_BOUND: "该邮箱已注册，请直接登录。",
  IDENTITY_NOT_FOUND: "该邮箱尚未注册，请先创建账号。",
  WEAK_PASSWORD: "密码至少需要 12 位。",
  AUTH_SESSION_SYNC_FAILED: "邮箱认证已通过，但学习服务账号同步失败，请稍后重试。",
};

export function validateRegistrationCredentials(email, password, nickname = null) {
  const normalizedEmail = String(email || "").trim();
  if (!normalizedEmail || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(normalizedEmail)) {
    return "INVALID_EMAIL";
  }
  if (typeof password !== "string" || password.length < 12 || password.length > 200) {
    return "WEAK_PASSWORD";
  }
  if (nickname !== null && !String(nickname || "").trim()) {
    return "INVALID_NICKNAME";
  }
  return null;
}

async function request(path, options = {}) {
  const response = await fetch(buildAuthApiUrl(path), {
    credentials: "include",
    headers: { "Content-Type": "application/json", ...(options.headers || {}) },
    ...options,
  });
  const body = response.status === 204 ? null : await response.json().catch(() => null);
  if (!response.ok || body?.success === false) {
    const error = body?.error || body;
    const code = error?.code || "AUTH_REQUEST_FAILED";
    const fallbackMessage = error?.message || "请求失败，请稍后重试。";
    const message = code === "VALIDATION_ERROR" && /password/i.test(fallbackMessage)
      ? messages.WEAK_PASSWORD
      : code === "VALIDATION_ERROR" && /email/i.test(fallbackMessage)
        ? "请输入有效邮箱地址。"
        : messages[code] || fallbackMessage;
    throw new UserAuthApiError(code, message);
  }
  return body?.data;
}

export function issueEmailChallenge(email, humanVerificationToken) {
  return request("/api/auth/email/challenges", {
    method: "POST",
    body: JSON.stringify({ email, humanVerificationToken }),
  });
}

export function issuePasswordResetChallenge(email, humanVerificationToken) {
  return request("/api/auth/email/password-reset/challenges", {
    method: "POST",
    body: JSON.stringify({ email, humanVerificationToken }),
  });
}

export function resetPasswordWithEmail({ email, password, challengeId, code }) {
  return request("/api/auth/email/password-reset", {
    method: "POST",
    body: JSON.stringify({ email, password, challengeId, code }),
  });
}

export async function registerWithEmail({ email, password, nickname, challengeId, code }) {
  const auth = await request("/api/auth/email/register/token", {
    method: "POST",
    body: JSON.stringify({ email, password, nickname, challengeId, code }),
  });
  saveAuthSession(auth);
  return auth;
}

export async function loginWithPassword(email, password, humanVerificationToken) {
  const auth = await request("/api/auth/email/password/login/token", {
    method: "POST",
    body: JSON.stringify({ email, password, humanVerificationToken }),
  });
  saveAuthSession(auth);
  return auth;
}

export async function logoutUser() {
  try {
    await revokeWebSession();
  } finally {
    clearAuthSession();
  }
}

export { UserAuthApiError };
