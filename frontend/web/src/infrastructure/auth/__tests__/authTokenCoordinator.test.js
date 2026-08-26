import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  clearAccessToken,
  getAccessToken,
  refreshAccessToken,
  revokeWebSession,
  saveAccessToken,
} from "../authTokenCoordinator.js";

function createStorage() {
  const values = new Map();
  return {
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
    removeItem: (key) => values.delete(key),
    has: (key) => values.has(key),
  };
}

describe("auth token coordinator", () => {
  beforeEach(() => {
    window.sessionStorage.clear();
    window.localStorage.clear();
    vi.restoreAllMocks();
  });

  it("prefers session storage and falls back to local storage", () => {
    window.localStorage.setItem("unispeaking.accessToken", "local-token");
    expect(getAccessToken()).toBe("local-token");
    window.sessionStorage.setItem("unispeaking.accessToken", "session-token");
    expect(getAccessToken()).toBe("session-token");
  });

  it("saves, clears, and conditionally preserves tokens", () => {
    saveAccessToken({ accessToken: "token-1", user: { id: "user-1" } });
    expect(getAccessToken()).toBe("token-1");
    clearAccessToken("stale-token");
    expect(getAccessToken()).toBe("token-1");
    clearAccessToken("token-1");
    expect(getAccessToken()).toBeNull();
    expect(() => saveAccessToken({})).toThrow("登录响应缺少 access token");
  });

  it("refreshes once for concurrent callers and handles expected expiry", async () => {
    window.sessionStorage.setItem("unispeaking.accessToken", "old-token");
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response(
      JSON.stringify({ success: true, data: { accessToken: "new-token" } }),
      { status: 200, headers: { "Content-Type": "application/json" } },
    ));
    const [first, second] = await Promise.all([refreshAccessToken(), refreshAccessToken()]);
    expect(first).toBe("new-token");
    expect(second).toBe("new-token");
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenCalledWith("/api/auth/web/token/refresh", expect.objectContaining({ method: "POST", credentials: "include" }));

    fetchMock.mockResolvedValueOnce(new Response(JSON.stringify({ success: false }), { status: 401 }));
    expect(await refreshAccessToken()).toBeNull();
  });

  it("falls back to local storage when session storage is unavailable", () => {
    const original = Object.getOwnPropertyDescriptor(window, "sessionStorage");
    Object.defineProperty(window, "sessionStorage", {
      configurable: true,
      value: { getItem: undefined, setItem: undefined, removeItem: undefined },
    });

    try {
      saveAccessToken({ accessToken: "local-only", user: { id: "local-user" } });
      expect(getAccessToken()).toBe("local-only");
      clearAccessToken();
      expect(window.localStorage.getItem("unispeaking.accessToken")).toBeNull();
    } finally {
      Object.defineProperty(window, "sessionStorage", original);
    }
  });

  it("returns early without a token and propagates refresh network failures", async () => {
    expect(await refreshAccessToken()).toBeNull();

    window.sessionStorage.setItem("unispeaking.accessToken", "old-token");
    const fetchMock = vi.spyOn(globalThis, "fetch").mockRejectedValue(new Error("refresh offline"));
    await expect(refreshAccessToken()).rejects.toThrow("refresh offline");
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("handles refresh server errors and direct auth response shapes", async () => {
    window.sessionStorage.setItem("unispeaking.accessToken", "old-token");
    const fetchMock = vi.spyOn(globalThis, "fetch");

    fetchMock.mockResolvedValueOnce(new Response(JSON.stringify({ success: false }), { status: 400 }));
    expect(await refreshAccessToken()).toBeNull();

    fetchMock.mockResolvedValueOnce(new Response(JSON.stringify({ error: { message: "refresh failed" } }), {
      status: 503,
      headers: { "Content-Type": "application/json" },
    }));
    await expect(refreshAccessToken()).rejects.toThrow("refresh failed");

    fetchMock.mockResolvedValueOnce(new Response("not json", { status: 502 }));
    await expect(refreshAccessToken()).rejects.toThrow("刷新登录态失败（502）");

    fetchMock.mockResolvedValueOnce(new Response(JSON.stringify({ accessToken: "direct-token" }), { status: 200 }));
    await expect(refreshAccessToken()).resolves.toBe("direct-token");
  });

  it("always clears the token after revoking, including a failed request", async () => {
    window.sessionStorage.setItem("unispeaking.accessToken", "token-to-revoke");
    const fetchMock = vi.spyOn(globalThis, "fetch").mockRejectedValue(new Error("revoke offline"));

    await expect(revokeWebSession()).rejects.toThrow("revoke offline");
    expect(fetchMock).toHaveBeenCalledWith("/api/auth/web/token/revoke", expect.objectContaining({
      method: "POST",
      credentials: "include",
      keepalive: true,
    }));
    expect(getAccessToken()).toBeNull();
  });
});
