import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const auth = {
  token: "access-token",
  get: vi.fn(),
  refresh: vi.fn(),
  save: vi.fn(),
  clear: vi.fn(),
  revoke: vi.fn(),
};
const telemetry = { record: vi.fn(), user: vi.fn() };

vi.mock("../../auth/authTokenCoordinator.js", () => ({
  getAccessToken: auth.get,
  refreshAccessToken: auth.refresh,
  saveAccessToken: auth.save,
  clearAccessToken: auth.clear,
  revokeWebSession: auth.revoke,
}));
vi.mock("../../../telemetry/clientTelemetry.js", () => ({
  recordTelemetry: telemetry.record,
  setTelemetryUser: telemetry.user,
}));

const api = await import("../apiClient.js");

function jsonResponse(body, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });
}

beforeEach(() => {
  vi.clearAllMocks();
  auth.get.mockReturnValue(auth.token);
  auth.refresh.mockResolvedValue("refreshed-token");
  vi.stubGlobal("fetch", vi.fn(async () => jsonResponse({ success: true, data: { ok: true } })));
});
afterEach(() => vi.unstubAllGlobals());

describe("web API client request behavior", () => {
  it("sends JSON with auth, unwraps data, and delegates session helpers", async () => {
    await expect(api.requestAuthenticated("/api/test", { method: "POST", body: JSON.stringify({ hello: "world" }) })).resolves.toEqual({ ok: true });
    expect(fetch).toHaveBeenCalledWith("/api/test", expect.objectContaining({ credentials: "include", headers: expect.objectContaining({ "Content-Type": "application/json", Authorization: "Bearer access-token" }) }));
    expect(api.getAccessToken()).toBe("access-token");
    expect(api.hasAuthSession()).toBe(true);
    api.saveAuthSession({ accessToken: "new" });
    api.clearAuthSession("old");
    expect(auth.save).toHaveBeenCalledWith({ accessToken: "new" });
    expect(auth.clear).toHaveBeenCalledWith("old");
  });

  it("refreshes once on protected 401 and emits expiry when refresh is unavailable", async () => {
    fetch.mockResolvedValueOnce(jsonResponse({ message: "expired" }, 401)).mockResolvedValueOnce(jsonResponse({ success: true, data: "fresh" }));
    await expect(api.getProfileInsights()).resolves.toBe("fresh");
    expect(auth.refresh).toHaveBeenCalledTimes(1);
    expect(fetch).toHaveBeenLastCalledWith("/api/profile/insights", expect.objectContaining({ headers: expect.objectContaining({ Authorization: "Bearer refreshed-token" }) }));

    auth.refresh.mockResolvedValue(null);
    fetch.mockResolvedValueOnce(jsonResponse({ message: "expired" }, 401));
    const expired = vi.fn();
    window.addEventListener(api.AUTH_SESSION_EXPIRED_EVENT, expired, { once: true });
    await expect(api.getProfileInsights()).rejects.toThrow("expired");
    expect(auth.clear).toHaveBeenCalledWith("access-token");
    expect(expired).toHaveBeenCalled();
    expect(telemetry.user).not.toHaveBeenCalled();
  });

  it("handles network and expected-status failures", async () => {
    fetch.mockRejectedValueOnce(new Error("network down"));
    await expect(api.getProfileInsights()).rejects.toThrow("network down");
    expect(telemetry.record).toHaveBeenCalledWith("api.request", expect.objectContaining({ severity: "ERROR" }));
    fetch.mockResolvedValueOnce(new Response("accepted", { status: 202 }));
    await expect(api.requestAuthenticated("/api/async", { expectedStatuses: [202] })).resolves.toBe("accepted");
    fetch.mockResolvedValueOnce(jsonResponse({ success: false, error: { message: "bad request" } }, 422));
    await expect(api.requestAuthenticated("/api/bad")).rejects.toThrow("bad request");
    expect(telemetry.record).toHaveBeenCalledWith("api.request", expect.objectContaining({ severity: "ERROR" }));
  });

  it("does not refresh auth endpoints or skipped-token requests", async () => {
    fetch.mockResolvedValueOnce(jsonResponse({ message: "bad credentials" }, 401));
    await expect(api.requestAuthenticated("/api/auth/login", { method: "POST", body: "{}" })).rejects.toThrow("bad credentials");
    expect(auth.refresh).not.toHaveBeenCalled();

    auth.get.mockClear();
    fetch.mockResolvedValueOnce(jsonResponse({ success: true, data: { ok: true } }));
    await expect(api.requestAuthenticated("/api/public", {
      skipAccessToken: true,
      headers: { "X-Test": "yes" },
    })).resolves.toEqual({ ok: true });
    expect(auth.get).not.toHaveBeenCalled();
    expect(fetch).toHaveBeenLastCalledWith("/api/public", expect.objectContaining({
      headers: { "X-Test": "yes" },
    }));
  });

  it("propagates refresh failures and protects a newer session from stale cleanup", async () => {
    fetch.mockResolvedValueOnce(jsonResponse({ message: "expired" }, 401));
    auth.refresh.mockRejectedValueOnce(new Error("refresh failed"));
    await expect(api.getProfileInsights()).rejects.toThrow("refresh failed");
    expect(auth.clear).not.toHaveBeenCalled();

    auth.refresh.mockResolvedValueOnce(null);
    auth.clear.mockClear();
    auth.get.mockReturnValueOnce("access-token").mockReturnValue("newer-token");
    fetch.mockResolvedValueOnce(jsonResponse({ message: "expired" }, 401));
    await expect(api.getProfileInsights()).rejects.toThrow("expired");
    expect(auth.clear).not.toHaveBeenCalled();
  });

  it("covers async task failure, malformed results, and evaluation completion", async () => {
    fetch.mockResolvedValueOnce(jsonResponse({ taskId: "task-1", status: "FAILED", failureReason: "generation rejected" }));
    await expect(api.generateCustomScene("input")).rejects.toThrow("generation rejected");

    fetch.mockResolvedValueOnce(jsonResponse({ taskId: "task-2", status: "FAILED" }));
    await expect(api.generateCustomScene("input")).rejects.toThrow("场景生成失败，请稍后重试");

    fetch.mockResolvedValueOnce(jsonResponse({ taskId: "task-3", status: "PROCESSING" }));
    await expect(api.generateCustomScene("input")).rejects.toThrow("任务状态异常");

    fetch.mockResolvedValueOnce(jsonResponse({ taskId: "task-4", status: "COMPLETED", result: {
      sceneId: "scene",
      wordList: [],
      phraseList: ["phrase"],
      sentenceList: ["sentence"],
    } }));
    await expect(api.generateCustomScene("input")).rejects.toThrow("场景生成内容不完整");

    fetch.mockResolvedValueOnce(jsonResponse({ status: "COMPLETED", result: { score: 7 } }));
    await expect(api.generateIeltsEvaluation("ielts", "session")).resolves.toEqual({ score: 7 });
  });

  it("handles auth helpers, multipart requests, completion cleanup, and speech success", async () => {
    const authResponse = { accessToken: "new-token", user: { id: "u1" } };
    fetch.mockResolvedValueOnce(jsonResponse({ success: true, data: authResponse }));
    await expect(api.register({ username: "a", password: "b" })).resolves.toEqual(authResponse);
    expect(auth.save).toHaveBeenCalledWith(authResponse);

    fetch.mockResolvedValueOnce(jsonResponse({ success: true, data: authResponse }));
    await expect(api.login({ username: "a", password: "b" })).resolves.toEqual(authResponse);
    fetch.mockResolvedValueOnce(jsonResponse({ success: true, data: { id: "current" } }));
    await expect(api.getCurrentUser()).resolves.toEqual({ id: "current" });
    expect(telemetry.user).toHaveBeenCalledWith("current");

    fetch.mockResolvedValueOnce(jsonResponse({ success: true, data: { uploaded: true } }));
    await expect(api.uploadProfileAvatar(new Blob(["avatar"]))).resolves.toEqual({ uploaded: true });
    expect(fetch.mock.calls.at(-1)[1].headers).not.toHaveProperty("Content-Type");

    fetch.mockResolvedValueOnce(jsonResponse({ success: true, data: { completed: true } }));
    await expect(api.completeCustomDialogue("scene", "session", "now")).resolves.toEqual({ completed: true });
    expect(fetch.mock.calls.at(-1)[1].signal).toBeInstanceOf(AbortSignal);

    const audio = new Blob(["audio"]);
    fetch.mockResolvedValueOnce(new Response(audio, { status: 200 }));
    await expect(api.synthesizeSpeech("scene/a", "hello", "voice")).resolves.toBeInstanceOf(Blob);
  });

  it("handles media auth expiry and speech error fallbacks", async () => {
    fetch.mockResolvedValueOnce(new Response("expired", { status: 401 }));
    auth.refresh.mockResolvedValueOnce(null);
    await expect(api.fetchAuthenticatedMedia("/recording.wav")).rejects.toThrow("录音加载失败（401）");
    expect(auth.clear).toHaveBeenCalledWith("access-token");

    fetch.mockResolvedValueOnce(new Response("expired", { status: 401 }));
    await expect(api.fetchAuthenticatedMedia("https://cdn.example/recording.wav")).rejects.toThrow("录音加载失败（401）");
    expect(auth.refresh).toHaveBeenCalledTimes(1);

    fetch.mockResolvedValueOnce(jsonResponse({ code: "SPEECH_LIMIT" }, 429));
    await expect(api.synthesizeSpeech("scene", "hello")).rejects.toThrow("SPEECH_LIMIT");
    fetch.mockResolvedValueOnce(new Response("bad", { status: 500 }));
    await expect(api.synthesizeSpeech("scene", "hello")).rejects.toThrow("语音生成失败（500）");
  });

  it("constructs the profile, achievement, user, IELTS, interview and asset URLs", async () => {
    const calls = [
      () => api.getRealtimeIceConfiguration(true), () => api.getProfileOverview("2026-08"), () => api.getProfileInsights(),
      () => api.updateWeeklyLearningGoals({ count: 3 }), () => api.getAchievementOverview(), () => api.syncAchievementUnlocks(),
      () => api.acknowledgeAchievementUnlock("a/b"), () => api.updateProfile({ nickname: "N" }),
      () => api.changePassword({ currentPassword: "a", newPassword: "b" }), () => api.getIeltsTopics({ part: "PART_1", category: "CAT", keyword: " hi ", page: 2, pageSize: 5 }),
      () => api.getIeltsTraining("PART_2", "topic/1"), () => api.getIeltsSettings(), () => api.updateIeltsSettings({ targetScore: 7, examinerId: "daniel" }),
      () => api.generateIeltsScene({ mode: "RANDOM", part: "PART_1", topicId: "t" }), () => api.createIeltsSceneFlow("scene"), () => api.getUserPreference(),
      () => api.getDailyPicks(["a", "b"]), () => api.getDailyPicks(), () => api.updateUserPreference({ voice: "Tina" }),
      () => api.createCustomSceneFlow("scene"), () => api.advanceCustomSceneFlow("scene", "learn"),
      () => api.evaluateCustomDialogueTurn("scene/a", "session", 1, "hello"), () => api.evaluateIeltsDialogueTurn("i", "s", 2, "hi"),
      () => api.advanceIeltsDialogueState("i", "s", 2, true), () => api.advanceIeltsPart2State("i", "s", "START"), () => api.getIeltsEvaluationHistory(),
      () => api.advanceCustomDialogueState("scene", "session", 1, "hi"), () => api.getCustomDialogueEvaluation("scene", "session"), () => api.getCustomDialogueState("scene", "session"),
      () => api.prepareInterviewMaterials(new FormData()), () => api.getInterviewOcrAvailability(), () => api.generateInterviewScene({ material: {}, difficulty: "EASY" }),
      () => api.startInterviewSession("scene", { x: 1 }), () => api.submitInterviewTurn("scene", "session", 1, new FormData()), () => api.endInterview("scene", "session"),
      () => api.getInterviewReport("scene", "session"), () => api.retryInterviewReport("scene", "session"), () => api.getInterviewAssets(),
      () => api.getLearningAssets(), () => api.getLearningAsset("scene/a"), () => api.deleteLearningAsset("scene/a"),
      () => api.evaluateSentenceReading("scene", "sentence", new Blob(["a"])), () => api.translateSceneText("scene", "hello"), () => api.translateSessionText("session", "hello"),
    ];
    await Promise.all(calls.map((call) => call()));
    const urls = fetch.mock.calls.map(([url]) => url);
    expect(urls).toContain("/api/realtime/ice-configuration?forceRelay=true");
    expect(urls).toContain("/api/profile/overview?month=2026-08");
    expect(urls).toContain("/api/ielts/topics?part=PART_1&page=2&pageSize=5&category=CAT&keyword=hi");
    expect(urls).toContain("/api/daily-picks?exclude=a&exclude=b");
    expect(urls).toContain("/api/custom-scenes/scene%2Fa/sessions/session/turns/1/evaluation");
    expect(urls).toContain("/api/interview-scenes/scene/sessions/session/report/retry");
  });

  it("supports media requests, auth, multipart, speech errors, and async scene generation", async () => {
    const blob = new Blob(["audio"]);
    fetch.mockResolvedValueOnce(new Response(blob, { status: 200 }));
    await expect(api.fetchAuthenticatedMedia("/media.wav")).resolves.toBeInstanceOf(Blob);
    expect(fetch).toHaveBeenCalledWith("/media.wav", { headers: { Authorization: "Bearer access-token" } });
    fetch.mockResolvedValueOnce(new Response(blob, { status: 401 })).mockResolvedValueOnce(new Response(blob, { status: 200 }));
    await expect(api.fetchAuthenticatedMedia("/media.wav")).resolves.toBeInstanceOf(Blob);
    fetch.mockResolvedValueOnce(new Response("no", { status: 404 }));
    await expect(api.fetchAuthenticatedMedia("https://cdn.example/media.wav")).rejects.toThrow("录音加载失败（404）");
    fetch.mockResolvedValueOnce(new Response("bad", { status: 500 }));
    await expect(api.synthesizeSpeech("scene", "hi")).rejects.toThrow("语音生成失败（500）");
    fetch.mockResolvedValueOnce(jsonResponse({ message: "speech bad" }, 422));
    await expect(api.synthesizeSpeech("scene", "hi")).rejects.toThrow("speech bad");

    fetch.mockResolvedValueOnce(jsonResponse({ taskId: "task/1", status: "PROCESSING" })).mockResolvedValueOnce(jsonResponse({ taskId: "task/1", status: "COMPLETED", result: { sceneId: "scene", wordList: [1], phraseList: [2], sentenceList: [3] } }));
    await expect(api.generateCustomScene("input", { level: "B1" })).resolves.toMatchObject({ sceneId: "scene", wordList: [1] });
    fetch.mockResolvedValueOnce(jsonResponse({ taskId: "bad", status: "PROCESSING" })).mockResolvedValueOnce(jsonResponse({ status: "COMPLETED", result: {} }));
    await expect(api.generateCustomScene("input")).rejects.toThrow("缺少 sceneId");
  });
});
