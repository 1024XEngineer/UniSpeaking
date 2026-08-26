import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  buildAuthApiUrl,
  issueEmailChallenge,
  issuePasswordResetChallenge,
  loginWithPassword,
  logoutUser,
  registerWithEmail,
  resetPasswordWithEmail,
  UserAuthApiError,
  validateRegistrationCredentials,
} from "../userAuthApi.js";

const authHelpers = vi.hoisted(() => ({
  save: vi.fn(),
  revoke: vi.fn(),
  clear: vi.fn(),
}));

vi.mock("../infrastructure/http/apiClient.js", () => ({
  saveAuthSession: authHelpers.save,
  revokeWebSession: authHelpers.revoke,
  clearAuthSession: authHelpers.clear,
}));

describe("user auth API", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    authHelpers.save.mockReset();
    authHelpers.revoke.mockReset();
    authHelpers.clear.mockReset();
  });

  it("normalizes URLs and validates registration credentials", () => {
    expect(buildAuthApiUrl("/api/test", "https://api.example.com/")).toBe("https://api.example.com/api/test");
    expect(validateRegistrationCredentials("", "long-password-123")).toBe("INVALID_EMAIL");
    expect(validateRegistrationCredentials("user@example.com", "short")).toBe("WEAK_PASSWORD");
    expect(validateRegistrationCredentials("user@example.com", "long-password-123", " ")).toBe("INVALID_NICKNAME");
    expect(validateRegistrationCredentials(" user@example.com ", "long-password-123", "User")).toBeNull();
  });

  it("sends challenge requests with JSON and maps server errors", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response(
      JSON.stringify({ success: true, data: { challengeId: "challenge-1" } }),
      { status: 200, headers: { "Content-Type": "application/json" } },
    ));
    await expect(issueEmailChallenge("user@example.com", "captcha-token")).resolves.toEqual({ challengeId: "challenge-1" });
    expect(fetchMock).toHaveBeenCalledWith("/api/auth/email/challenges", expect.objectContaining({
      method: "POST",
      credentials: "include",
      body: JSON.stringify({ email: "user@example.com", humanVerificationToken: "captcha-token" }),
    }));

    fetchMock.mockResolvedValueOnce(new Response(
      JSON.stringify({ success: false, error: { code: "WEAK_PASSWORD", message: "invalid" } }),
      { status: 400, headers: { "Content-Type": "application/json" } },
    ));
    await expect(loginWithPassword("user@example.com", "bad", "captcha-token"))
      .rejects.toMatchObject({ name: "UserAuthApiError", code: "WEAK_PASSWORD", message: "密码至少需要 12 位。" });
  });

  it("supports password reset and registration flows", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch");
    fetchMock
      .mockResolvedValueOnce(new Response(JSON.stringify({ success: true, data: { challengeId: "reset-1" } }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ success: true, data: { reset: true } }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ success: true, data: { accessToken: "registered" } }), { status: 200 }));

    await expect(issuePasswordResetChallenge("user@example.com", "human-token")).resolves.toEqual({ challengeId: "reset-1" });
    await expect(resetPasswordWithEmail({ email: "user@example.com", password: "long-password-123", challengeId: "reset-1", code: "123456" }))
      .resolves.toEqual({ reset: true });
    await expect(registerWithEmail({ email: "user@example.com", password: "long-password-123", nickname: "User", challengeId: "challenge-1", code: "654321" }))
      .resolves.toEqual({ accessToken: "registered" });
    expect(authHelpers.save).toHaveBeenCalledWith({ accessToken: "registered" });
    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual([
      "/api/auth/email/password-reset/challenges",
      "/api/auth/email/password-reset",
      "/api/auth/email/register/token",
    ]);
  });

  it("maps validation, known, unknown, malformed, and empty responses", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch");
    const response = (body, status = 400, headers = { "Content-Type": "application/json" }) =>
      new Response(body === null ? null : JSON.stringify(body), { status, headers });

    fetchMock.mockResolvedValueOnce(response({ success: false, error: { code: "VALIDATION_ERROR", message: "invalid password" } }));
    await expect(issueEmailChallenge("x", "y")).rejects.toMatchObject({ code: "VALIDATION_ERROR", message: "密码至少需要 12 位。" });
    fetchMock.mockResolvedValueOnce(response({ success: false, error: { code: "VALIDATION_ERROR", message: "invalid email" } }));
    await expect(issueEmailChallenge("x", "y")).rejects.toMatchObject({ message: "请输入有效邮箱地址。" });
    fetchMock.mockResolvedValueOnce(response({ success: false, error: { code: "HUMAN_VERIFICATION_REQUIRED" } }));
    await expect(issueEmailChallenge("x", "y")).rejects.toMatchObject({ message: "请先完成人机验证。" });
    fetchMock.mockResolvedValueOnce(response({ success: false, error: { code: "UNLISTED", message: "server detail" } }));
    await expect(issueEmailChallenge("x", "y")).rejects.toMatchObject({ code: "UNLISTED", message: "server detail" });
    fetchMock.mockResolvedValueOnce(response({ error: { code: "IDENTITY_NOT_FOUND" } }));
    await expect(issueEmailChallenge("x", "y")).rejects.toMatchObject({ message: "该邮箱尚未注册，请先创建账号。" });
    fetchMock.mockResolvedValueOnce(response("not-json", 500, { "Content-Type": "text/plain" }));
    await expect(issueEmailChallenge("x", "y")).rejects.toMatchObject({ code: "AUTH_REQUEST_FAILED", message: "请求失败，请稍后重试。" });
    fetchMock.mockResolvedValueOnce(response(null, 204));
    await expect(issueEmailChallenge("x", "y")).resolves.toBeUndefined();
    expect(validateRegistrationCredentials("user@example.com", 123)).toBe("WEAK_PASSWORD");
    expect(validateRegistrationCredentials("user@example.com", "x".repeat(201))).toBe("WEAK_PASSWORD");
  });

  it("always clears the session when logout succeeds or fails", async () => {
    authHelpers.revoke.mockResolvedValueOnce(undefined);
    await expect(logoutUser()).resolves.toBeUndefined();
    expect(authHelpers.clear).toHaveBeenCalledTimes(1);

    authHelpers.clear.mockClear();
    authHelpers.revoke.mockRejectedValueOnce(new Error("revoke failed"));
    await expect(logoutUser()).rejects.toThrow("revoke failed");
    expect(authHelpers.clear).toHaveBeenCalledTimes(1);
  });

  it("exposes a typed error for failed auth calls", () => {
    const error = new UserAuthApiError("CODE", "message");
    expect(error).toBeInstanceOf(Error);
    expect(error.name).toBe("UserAuthApiError");
    expect(error.code).toBe("CODE");
  });
});
