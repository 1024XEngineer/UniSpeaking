const API_BASE = (import.meta.env?.VITE_UNISPEAKING_API_BASE || "").replace(/\/$/, "");
const ACCESS_TOKEN_KEY = "unispeaking.accessToken";

async function unwrap(response) {
  const contentType = response.headers.get("content-type") || "";
  const body = contentType.includes("application/json") ? await response.json() : await response.text();
  if (!response.ok || (body && typeof body === "object" && body.success === false)) {
    const message = body?.message || body?.code || `请求失败（${response.status}）`;
    throw new Error(message);
  }
  return body && typeof body === "object" && "success" in body ? body.data : body;
}

async function request(path, options = {}) {
  const token = getAccessToken();
  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers: {
      ...(options.body ? { "Content-Type": "application/json" } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers,
    },
  });
  if (response.status === 401 && !path.startsWith("/api/auth/")) {
    clearAuthSession();
  }
  return unwrap(response);
}

export function getAccessToken() {
  return window.localStorage.getItem(ACCESS_TOKEN_KEY);
}

export function hasAuthSession() {
  return Boolean(getAccessToken());
}

export function saveAuthSession(authResponse) {
  window.localStorage.setItem(ACCESS_TOKEN_KEY, authResponse.accessToken);
}

export function clearAuthSession() {
  window.localStorage.removeItem(ACCESS_TOKEN_KEY);
}

export async function register({ username, password, nickname = null }) {
  const auth = await request("/api/auth/register", {
    method: "POST",
    body: JSON.stringify({ username, password, nickname }),
  });
  saveAuthSession(auth);
  return auth;
}

export async function login({ username, password }) {
  const auth = await request("/api/auth/login", {
    method: "POST",
    body: JSON.stringify({ username, password }),
  });
  saveAuthSession(auth);
  return auth;
}

export function getCurrentUser() {
  return request("/api/auth/me");
}

export function getUserPreference() {
  return request("/api/user-preferences");
}

export function updateUserPreference(preference) {
  return request("/api/user-preferences", {
    method: "PUT",
    body: JSON.stringify(preference),
  });
}

export function translateText(text) {
  return request("/api/translations", {
    method: "POST",
    body: JSON.stringify({ text }),
  });
}
