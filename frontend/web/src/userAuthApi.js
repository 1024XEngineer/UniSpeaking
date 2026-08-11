import { clearAuthSession, login as loginLegacy, register as registerLegacy } from "./infrastructure/http/apiClient.js";

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

export function validateRegistrationCredentials(email, password) {
  const normalizedEmail = String(email || "").trim();
  if (!normalizedEmail || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(normalizedEmail)) {
    return "INVALID_EMAIL";
  }
  if (typeof password !== "string" || password.length < 12 || password.length > 200) {
    return "WEAK_PASSWORD";
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

async function syncBusinessIdentity(email, password) {
  try {
    return await loginLegacy({ username: email, password });
  } catch (loginError) {
    try {
      return await registerLegacy({ username: email, password });
    } catch (registerError) {
      try {
        return await loginLegacy({ username: email, password });
      } catch {
        throw new UserAuthApiError(
          "AUTH_SESSION_SYNC_FAILED",
          messages.AUTH_SESSION_SYNC_FAILED,
          { cause: registerError || loginError },
        );
      }
    }
  }
}

export async function registerWithEmail({ email, password, challengeId, code }) {
  try {
    await request("/api/auth/email/register", {
      method: "POST",
      body: JSON.stringify({ email, password, challengeId, code }),
    });
  } catch (registrationError) {
    // A one-time challenge can be submitted twice when the first request
    // already created the account but the browser stayed on a stale tab.
    // Recover only when the same credentials can perform a real password login;
    // a wrong code or password still returns the original challenge error.
    if (registrationError?.code !== "CHALLENGE_INVALID") throw registrationError;
    try {
      return await loginWithPassword(email, password);
    } catch {
      throw registrationError;
    }
  }
  return syncBusinessIdentity(email, password);
}

export async function loginWithPassword(email, password) {
  await request("/api/auth/email/password/login", {
    method: "POST",
    body: JSON.stringify({ email, password }),
  });
  return syncBusinessIdentity(email, password);
}

export async function logoutUser() {
  try {
    await request("/api/auth/logout", { method: "POST" });
  } finally {
    clearAuthSession();
  }
}

export { UserAuthApiError };
