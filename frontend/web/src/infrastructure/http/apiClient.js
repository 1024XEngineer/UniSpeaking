import { recordTelemetry, setTelemetryUser } from "../../telemetry/clientTelemetry.js";

const API_BASE = (import.meta.env?.VITE_BACKEND_URL || "").replace(/\/$/, "");
const ACCESS_TOKEN_KEY = "unispeaking.accessToken";
export const AUTH_SESSION_EXPIRED_EVENT = "unispeaking:auth-session-expired";

async function unwrap(response) {
  const contentType = response.headers.get("content-type") || "";
  const body = contentType.includes("application/json") ? await response.json() : await response.text();
  if (!response.ok || (body && typeof body === "object" && body.success === false)) {
    const error = body?.error || body;
    const message = error?.message || error?.code || `请求失败（${response.status}）`;
    throw new Error(message);
  }
  return body && typeof body === "object" && "success" in body ? body.data : body;
}

async function request(path, options = {}) {
  const { expectedStatuses = [], ...fetchOptions } = options;
  const token = getAccessToken();
  const formDataBody = typeof FormData !== "undefined" && fetchOptions.body instanceof FormData;
  const startedAt = performance.now();
  const apiPath = path.split("?", 1)[0];
  let response;
  try {
    response = await fetch(`${API_BASE}${path}`, {
      ...fetchOptions,
      credentials: "include",
      headers: {
        ...(fetchOptions.body && !formDataBody ? { "Content-Type": "application/json" } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...fetchOptions.headers,
      },
    });
  } catch (error) {
    recordTelemetry("api.request", {
      severity: "ERROR",
      message: error instanceof Error ? error.message : "Network request failed",
      attributes: {
        api_path: apiPath,
        api_method: fetchOptions.method || "GET",
        outcome: "network_error",
        duration_ms: performance.now() - startedAt,
      },
    });
    throw error;
  }
  if (response.status === 401 && !path.startsWith("/api/auth/")) {
    // Bind cleanup to the token captured by this request so a delayed 401
    // cannot erase a newer session established in the same browser tab.
    if (token && getAccessToken() === token) {
      clearAuthSession(token);
      window.dispatchEvent(new Event(AUTH_SESSION_EXPIRED_EVENT));
    }
  }
  try {
    const result = await unwrap(response);
    recordTelemetry("api.request", {
      attributes: {
        api_path: apiPath,
        api_method: fetchOptions.method || "GET",
        http_status: response.status,
        outcome: "success",
        duration_ms: performance.now() - startedAt,
      },
    });
    return result;
  } catch (error) {
    // Authentication expiry is an expected client state for every protected
    // endpoint, even when the response races with navigation to /login.
    const expectedFailure = response.status === 401 || expectedStatuses.includes(response.status);
    recordTelemetry("api.request", {
      severity: expectedFailure ? "INFO" : "ERROR",
      message: error instanceof Error ? error.message : "API request failed",
      attributes: {
        api_path: apiPath,
        api_method: fetchOptions.method || "GET",
        http_status: response.status,
        outcome: expectedFailure ? "unauthenticated" : "error",
        duration_ms: performance.now() - startedAt,
      },
    });
    throw error;
  }
}

export async function fetchAuthenticatedMedia(pathOrUrl) {
  const absolute = /^https?:\/\//i.test(pathOrUrl);
  const target = absolute ? pathOrUrl : `${API_BASE}${pathOrUrl}`;
  const token = getAccessToken();
  const response = await fetch(target, {
    headers: {
      ...(!absolute && token ? { Authorization: `Bearer ${token}` } : {}),
    },
  });
  if (response.status === 401 && !absolute) clearAuthSession(token);
  if (!response.ok) throw new Error(`录音加载失败（${response.status}）`);
  return response.blob();
}

export function getAccessToken() {
  return window.localStorage.getItem(ACCESS_TOKEN_KEY);
}

export function hasAuthSession() {
  return Boolean(getAccessToken());
}

export function saveAuthSession(authResponse) {
  window.localStorage.setItem(ACCESS_TOKEN_KEY, authResponse.accessToken);
  setTelemetryUser(authResponse.user?.id || null);
}

export function clearAuthSession(expectedToken = null) {
  if (expectedToken !== null && getAccessToken() !== expectedToken) return;
  window.localStorage.removeItem(ACCESS_TOKEN_KEY);
  setTelemetryUser(null);
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

export async function getCurrentUser() {
  const user = await request("/api/auth/me", { expectedStatuses: [401] });
  setTelemetryUser(user?.id || null);
  return user;
}

export function getRealtimeIceConfiguration(forceRelay = false) {
  const query = forceRelay ? "?forceRelay=true" : "";
  return request(`/api/realtime/ice-configuration${query}`);
}

export function getProfileOverview(month) {
  const query = month ? `?month=${encodeURIComponent(month)}` : "";
  return request(`/api/profile/overview${query}`);
}

export function getProfileInsights() {
  return request("/api/profile/insights");
}

export function updateWeeklyLearningGoals(goals) {
  return request("/api/profile/insights/goals", {
    method: "PUT",
    body: JSON.stringify(goals),
  });
}

export function getAchievementOverview() {
  return request("/api/achievements");
}

export function syncAchievementUnlocks() {
  return request("/api/achievement-unlocks", {
    method: "POST",
  });
}

export function acknowledgeAchievementUnlock(achievementId) {
  return request(
    `/api/achievement-unlocks/${encodeURIComponent(achievementId)}`,
    {
      method: "PATCH",
      body: JSON.stringify({ acknowledged: true }),
    },
  );
}

export function updateProfile({ nickname }) {
  return request("/api/profile", {
    method: "PATCH",
    body: JSON.stringify({ nickname }),
  });
}

export function uploadProfileAvatar(file) {
  const formData = new FormData();
  formData.append("avatar", file);
  return request("/api/profile/avatar", {
    method: "POST",
    body: formData,
  });
}

export function changePassword({ currentPassword, newPassword }) {
  return request("/api/auth/password", {
    method: "PUT",
    body: JSON.stringify({ currentPassword, newPassword }),
  });
}

export function getIeltsTopics({
  part,
  category = null,
  keyword = null,
  page = 1,
  pageSize = 10,
}) {
  const params = new URLSearchParams({ part, page: String(page), pageSize: String(pageSize) });
  if (category && category !== "ALL") params.set("category", category);
  if (keyword?.trim()) params.set("keyword", keyword.trim());
  return request(`/api/ielts/topics?${params.toString()}`);
}

export function getIeltsTraining(part, topicId = null) {
  const params = new URLSearchParams({ part });
  if (topicId) params.set("topicId", topicId);
  return request(`/api/ielts/training?${params.toString()}`);
}

export function getIeltsSettings() {
  return request("/api/ielts/settings", { cache: "no-store" });
}

export function updateIeltsSettings({ targetScore = null, examinerId = null }) {
  return request("/api/ielts/settings", {
    method: "PUT",
    body: JSON.stringify({ targetScore, examinerId }),
  });
}

export function generateIeltsScene({ mode, part, topicId = null }) {
  return request("/api/ielts/generate", {
    method: "POST",
    body: JSON.stringify({ mode, part, topicId }),
  });
}

export function createIeltsSceneFlow(sceneId) {
  return request("/api/ielts/flows", {
    method: "POST",
    body: JSON.stringify({ sceneId }),
  });
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

export async function generateCustomScene(sceneInput, userPreference = null) {
  const scene = await request("/api/custom-scenes/generate", {
    method: "POST",
    body: JSON.stringify({ sceneInput, userPreference }),
  });
  if (!scene || typeof scene !== "object" || !scene.sceneId) {
    throw new Error("场景生成响应缺少 sceneId");
  }
  const normalized = {
    ...scene,
    wordList: Array.isArray(scene.wordList) ? scene.wordList : [],
    phraseList: Array.isArray(scene.phraseList) ? scene.phraseList : [],
    sentenceList: Array.isArray(scene.sentenceList) ? scene.sentenceList : [],
  };
  if (!normalized.wordList.length || !normalized.phraseList.length || !normalized.sentenceList.length) {
    throw new Error("场景生成内容不完整，请重新生成");
  }
  return normalized;
}

export function createCustomSceneFlow(sceneId) {
  return request("/api/custom-scenes/flows", {
    method: "POST",
    body: JSON.stringify({ sceneId }),
  });
}

export function advanceCustomSceneFlow(sceneId, stage) {
  return request("/api/custom-scenes/flows/advance", {
    method: "POST",
    body: JSON.stringify({ sceneId, stage }),
  });
}

export function evaluateCustomDialogueTurn(
  sceneId,
  sessionId,
  turnNo,
  transcript,
  wavAudio,
) {
  const formData = new FormData();
  formData.append("transcript", transcript);
  if (wavAudio) formData.append("audio", wavAudio, `turn-${turnNo}.wav`);
  return request(
    `/api/custom-scenes/${encodeURIComponent(sceneId)}/sessions/${encodeURIComponent(sessionId)}/turns/${turnNo}/evaluation`,
    {
      method: "POST",
      body: formData,
    },
  );
}

export function evaluateIeltsDialogueTurn(
  ieltsId,
  sessionId,
  turnNo,
  transcript,
  wavAudio,
) {
  const formData = new FormData();
  formData.append("transcript", transcript);
  if (wavAudio) formData.append("audio", wavAudio, `ielts-turn-${turnNo}.wav`);
  return request(
    `/api/ielts/${encodeURIComponent(ieltsId)}/sessions/${encodeURIComponent(sessionId)}/turns/${turnNo}/evaluation`,
    {
      method: "POST",
      body: formData,
    },
  );
}

export function advanceIeltsDialogueState(
  ieltsId,
  sessionId,
  turnNo,
  timedOut = false,
) {
  return request(
    `/api/ielts/${encodeURIComponent(ieltsId)}/sessions/${encodeURIComponent(sessionId)}/turns/${turnNo}/state${timedOut ? "?timedOut=true" : ""}`,
    { method: "POST" },
  );
}

export function advanceIeltsPart2State(
  ieltsId,
  sessionId,
  event,
) {
  return request(
    `/api/ielts/${encodeURIComponent(ieltsId)}/sessions/${encodeURIComponent(sessionId)}/part2/state`,
    {
      method: "POST",
      body: JSON.stringify({ event }),
    },
  );
}

export function generateIeltsEvaluation(ieltsId, sessionId) {
  return request(
    `/api/ielts/${encodeURIComponent(ieltsId)}/sessions/${encodeURIComponent(sessionId)}/evaluation`,
    { method: "POST" },
  );
}

export function getIeltsEvaluationHistory() {
  return request("/api/ielts/evaluations");
}

export function advanceCustomDialogueState(
  sceneId,
  sessionId,
  turnNo,
  transcript,
) {
  return request(
    `/api/custom-scenes/${encodeURIComponent(sceneId)}/sessions/${encodeURIComponent(sessionId)}/turns/${turnNo}/state`,
    {
      method: "POST",
      body: JSON.stringify({ transcript }),
    },
  );
}

export async function completeCustomDialogue(sceneId, sessionId, stopTime) {
  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), 25_000);
  try {
    return await request(
      `/api/custom-scenes/${encodeURIComponent(sceneId)}/sessions/${encodeURIComponent(sessionId)}/complete`,
      {
        method: "POST",
        body: JSON.stringify({ stopTime }),
        signal: controller.signal,
      },
    );
  } finally {
    window.clearTimeout(timeout);
  }
}

export function getCustomDialogueEvaluation(sceneId, sessionId) {
  return request(
    `/api/custom-scenes/${encodeURIComponent(sceneId)}/sessions/${encodeURIComponent(sessionId)}/evaluation`,
  );
}

export function getCustomDialogueState(sceneId, sessionId) {
  return request(
    `/api/custom-scenes/${encodeURIComponent(sceneId)}/sessions/${encodeURIComponent(sessionId)}/state`,
  );
}

export function prepareInterviewMaterials(formData) {
  return request("/api/interview-scenes/prepare-materials", {
    method: "POST",
    body: formData,
  });
}

export function getInterviewOcrAvailability() {
  return request("/api/interview-scenes/ocr/availability", {
    cache: "no-store",
  });
}

export function generateInterviewScene({ material, difficulty }) {
  return request("/api/interview-scenes", {
    method: "POST",
    body: JSON.stringify({ material, difficulty }),
  });
}

export function startInterviewSession(sceneId, payload) {
  return request(
    `/api/interview-scenes/${encodeURIComponent(sceneId)}/sessions`,
    {
      method: "POST",
      body: JSON.stringify(payload),
    },
  );
}

export function submitInterviewTurn(sceneId, sessionId, turnNo, formData) {
  return request(
    `/api/interview-scenes/${encodeURIComponent(sceneId)}/sessions/${encodeURIComponent(sessionId)}/turns/${turnNo}`,
    {
      method: "POST",
      body: formData,
    },
  );
}

export function endInterview(sceneId, sessionId) {
  return request(
    `/api/interview-scenes/${encodeURIComponent(sceneId)}/sessions/${encodeURIComponent(sessionId)}/end`,
    { method: "POST" },
  );
}

export function getInterviewReport(sceneId, sessionId) {
  return request(
    `/api/interview-scenes/${encodeURIComponent(sceneId)}/sessions/${encodeURIComponent(sessionId)}/report`,
  );
}

export function retryInterviewReport(sceneId, sessionId) {
  return request(
    `/api/interview-scenes/${encodeURIComponent(sceneId)}/sessions/${encodeURIComponent(sessionId)}/report/retry`,
    { method: "POST" },
  );
}

export function getInterviewAssets() {
  return request("/api/interview-scenes/assets");
}

export function getLearningAssets() {
  return request("/api/custom-scenes/assets");
}

export function getLearningAsset(sceneId) {
  return request(
    `/api/custom-scenes/${encodeURIComponent(sceneId)}/assets`,
  );
}

export function deleteLearningAsset(sceneId) {
  return request(
    `/api/custom-scenes/${encodeURIComponent(sceneId)}/assets`,
    { method: "DELETE" },
  );
}

export async function synthesizeSpeech(sceneId, text, model = null) {
	const token = getAccessToken();
	const response = await fetch(
		`${API_BASE}/api/custom-scenes/${encodeURIComponent(sceneId)}/speech`,
		{
		method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
			body: JSON.stringify({ text, model }),
		},
	);
  if (!response.ok) {
    const contentType = response.headers.get("content-type") || "";
    const body = contentType.includes("application/json") ? await response.json() : null;
    throw new Error(body?.message || body?.code || `语音生成失败（${response.status}）`);
  }
  return response.blob();
}

export function evaluateSentenceReading(sceneId, sentenceId, wavAudio) {
  const formData = new FormData();
  formData.append("audio", wavAudio, `${sentenceId}.wav`);
  return request(
    `/api/custom-scenes/${encodeURIComponent(sceneId)}/sentences/${encodeURIComponent(sentenceId)}/evaluation`,
    {
      method: "POST",
      body: formData,
    },
  );
}

export function translateSceneText(sceneId, text) {
	return request(`/api/custom-scenes/${encodeURIComponent(sceneId)}/translations`, {
		method: "POST",
		body: JSON.stringify({ text }),
	});
}

export function translateSessionText(sessionId, text) {
	return request(`/api/scene-sessions/${encodeURIComponent(sessionId)}/translations`, {
		method: "POST",
		body: JSON.stringify({ text }),
	});
}
