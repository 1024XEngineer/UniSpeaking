import { setTelemetryUser } from "../../telemetry/clientTelemetry.js";

const API_BASE = (import.meta.env?.VITE_BACKEND_URL || "").replace(/\/$/, "");
const ACCESS_TOKEN_KEY = "unispeaking.accessToken";
const refreshPath = "/api/auth/web/token/refresh";

let refreshPromise = null;

export function getAccessToken() {
  if (window.sessionStorage?.getItem) {
    return window.sessionStorage.getItem(ACCESS_TOKEN_KEY)
      || window.localStorage?.getItem?.(ACCESS_TOKEN_KEY)
      || null;
  }
  return window.localStorage?.getItem?.(ACCESS_TOKEN_KEY) || null;
}

export function saveAccessToken(authResponse) {
  if (!authResponse?.accessToken) throw new Error("登录响应缺少 access token");
  window.localStorage?.removeItem?.(ACCESS_TOKEN_KEY);
  if (window.sessionStorage?.setItem) {
    window.sessionStorage.setItem(ACCESS_TOKEN_KEY, authResponse.accessToken);
  } else {
    window.localStorage?.setItem?.(ACCESS_TOKEN_KEY, authResponse.accessToken);
  }
  setTelemetryUser(authResponse.user?.id || null);
}

export function clearAccessToken(expectedToken = null) {
  if (expectedToken !== null && getAccessToken() !== expectedToken) return;
  window.sessionStorage?.removeItem?.(ACCESS_TOKEN_KEY);
  window.localStorage?.removeItem?.(ACCESS_TOKEN_KEY);
  setTelemetryUser(null);
}

export async function refreshAccessToken() {
  if (!getAccessToken()) return null;
  if (refreshPromise) return refreshPromise;

  refreshPromise = (async () => {
    let response;
    try {
      response = await fetch(`${API_BASE}${refreshPath}`, {
        method: "POST",
        credentials: "include",
        headers: { Accept: "application/json" },
      });
    } catch (error) {
      throw error;
    }

    const body = await response.json().catch(() => null);
    if (!response.ok || body?.success === false) {
      if (response.status === 401 || response.status === 400) return null;
      throw new Error(body?.message || body?.error?.message || `刷新登录态失败（${response.status}）`);
    }
    const auth = body?.data || body;
    saveAccessToken(auth);
    return auth.accessToken;
  })().finally(() => {
    refreshPromise = null;
  });

  return refreshPromise;
}

export async function revokeWebSession() {
  try {
    await fetch(`${API_BASE}/api/auth/web/token/revoke`, {
      method: "POST",
      credentials: "include",
      headers: { Accept: "application/json" },
      keepalive: true,
    });
  } finally {
    clearAccessToken();
  }
}
