const API_BASE = (import.meta.env?.VITE_BACKEND_URL || "").replace(/\/$/, "");
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
  const hasJsonBody = typeof options.body === "string";
  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers: {
      ...(hasJsonBody ? { "Content-Type": "application/json" } : {}),
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

export function getProfileOverview(yearMonth) {
  const query = yearMonth ? `?yearMonth=${encodeURIComponent(yearMonth)}` : "";
  return request(`/api/profile/overview${query}`);
}

export function updateAccountProfile({ nickname }) {
  return request("/api/account/profile", {
    method: "PATCH",
    body: JSON.stringify({ nickname }),
  });
}

export function uploadAvatar(file) {
  const body = new FormData();
  body.append("avatar", file);
  return request("/api/account/avatar", {
    method: "POST",
    body,
  });
}

export function deleteAvatar() {
  return request("/api/account/avatar", {
    method: "DELETE",
  });
}

export function changePassword({ currentPassword, newPassword }) {
  return request("/api/auth/password", {
    method: "PUT",
    body: JSON.stringify({ currentPassword, newPassword }),
  });
}

export function requestAccountDeletion({ currentPassword }) {
  return request("/api/account", {
    method: "DELETE",
    body: JSON.stringify({ currentPassword }),
  });
}

export function reactivateAccount({ username, password }) {
  return request("/api/auth/reactivate", {
    method: "POST",
    body: JSON.stringify({ username, password }),
  });
}

export function translateText(text) {
  return request("/api/translations", {
    method: "POST",
    body: JSON.stringify({ text }),
  });
}
