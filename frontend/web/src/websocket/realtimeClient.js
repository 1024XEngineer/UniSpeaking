import { getAccessToken } from "../infrastructure/http/apiClient.js";
import {
  advanceCustomDialogueState,
  advanceIeltsDialogueState,
  advanceIeltsPart2State,
  completeCustomDialogue,
  endInterview,
  evaluateCustomDialogueTurn,
  evaluateIeltsDialogueTurn,
  getCustomDialogueEvaluation,
  getInterviewReport,
  submitInterviewTurn,
} from "../infrastructure/http/apiClient.js";
import { createPcmWavSegmentRecorder } from "../infrastructure/audio/audioRecorder.js";

const DEFAULT_API_BASE = "";
const DEFAULT_VOICE = "Katerina";
const DEFAULT_MODEL = "qwen3.5-omni-flash-realtime";
const DATA_CHANNEL_LABEL = "oai-events";
const DEFAULT_SPEECH_SPEED = "NATURAL";
const DEFAULT_ICE_SERVERS = [
  { urls: "stun:stun.aliyun.com:3478" },
  { urls: "stun:stun.l.google.com:19302" },
];
const SCENARIO_CLOSING_TIMEOUT_MS = 20_000;
const SCENARIO_AUDIO_DRAIN_MS = 1_200;
const SESSION_UPDATE_TIMEOUT_MS = 5_000;
const IELTS_STATE_TIMEOUT_MS = 5_000;
const IELTS_INPUT_RECOVERY_MS = 2_500;
const DEFAULT_INTERVIEW_CLOSING = "The interview is complete. Give a brief, natural closing and thank the candidate for their time. Do not ask more questions.";
const SPEECH_SPEED_INSTRUCTIONS = {
  SLOWER: "Voice delivery rule: speak distinctly and very slowly, around 70 English words per minute, with clear pauses between short phrases.",
  MODERATE: "Voice delivery rule: speak at a calm moderate pace, around 120 English words per minute, with clear pauses between ideas.",
  NATURAL: "Voice delivery rule: speak at a natural conversational pace, around 165 English words per minute.",
  FASTER: "Voice delivery rule: speak quickly but clearly, around 210 English words per minute, without dropping or slurring words.",
};

const eventId = (prefix) => `${prefix}_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;

export function micFailureMessage(error) {
  const name = error?.name || "";
  if (name === "NotAllowedError" || name === "SecurityError") {
    return "麦克风权限被拒绝，请在浏览器地址栏允许访问麦克风后重试";
  }
  if (name === "NotFoundError" || name === "DevicesNotFoundError") {
    return "未检测到麦克风设备，请连接麦克风后重试";
  }
  if (name === "NotReadableError" || name === "AudioCaptureError") {
    return "麦克风正被其他应用占用，请关闭后重试";
  }
  if (name === "OverconstrainedError") {
    return "当前麦克风不满足采集要求，请更换设备后重试";
  }
  return null;
}

export function isMicFailure(error) {
  return ["NotAllowedError", "SecurityError", "NotFoundError",
    "DevicesNotFoundError", "NotReadableError", "AudioCaptureError", "OverconstrainedError"]
    .includes(error?.name);
}

export function realtimeFailureMessage(error) {
  const rawMessage = error instanceof Error ? error.message : String(error || "");
  if (/AllocationQuota\.FreeTierOnly|free quota exhausted|free tier only/i.test(rawMessage)) {
    return "Qwen 实时服务的免费额度已用尽，请在阿里云百炼控制台充值或关闭“仅使用免费额度”后重试";
  }
  if (/QWEN_SIGNALING_FAILED|Qwen signaling returned 403/i.test(rawMessage)) {
    return "Qwen 实时服务拒绝了连接，请检查模型权限、Workspace 配置和账户额度";
  }
  if (/ICE (?:connection )?(?:failed|disconnected)|ICE 候选|DataChannel/i.test(rawMessage)) {
    return "实时网络通道建立失败，请检查当前网络是否允许 WebRTC；必要时配置可用的 TURN 服务器后重试";
  }
  return rawMessage || "无法开始实时对话";
}

export function defaultIceServers() {
  const configured = import.meta.env?.VITE_ICE_SERVERS;
  if (configured) {
    try {
      const parsed = JSON.parse(configured);
      if (Array.isArray(parsed)) return parsed;
    } catch {
      /* ignore malformed env value */
    }
  }
  return DEFAULT_ICE_SERVERS;
}

export function buildResponseCreateEvent({ id, instructions = "" } = {}) {
  const turnInstructions = String(instructions || "").trim();
  return {
    event_id: id || eventId("response"),
    type: "response.create",
    ...(turnInstructions
      ? {
        response: {
          instructions: turnInstructions,
          modalities: ["text", "audio"],
        },
      }
      : {}),
  };
}

export function normalizeBaseUrl(baseUrl) {
  if (!baseUrl) return "";
  const value = String(baseUrl).trim().replace(/\/$/, "");
  if (value.startsWith("/") && !value.startsWith("//")) {
    return value;
  }
  const url = new URL(value);
  if (url.protocol !== "http:" && url.protocol !== "https:") {
    throw new Error("后端地址必须使用 HTTP 或 HTTPS");
  }
  return url.toString().replace(/\/$/, "");
}

export function websocketUrl(
  baseUrl,
  accessToken,
  pageOrigin = globalThis.window?.location?.origin,
) {
  if (!pageOrigin && (!baseUrl || String(baseUrl).startsWith("/"))) {
    throw new Error("无法确定 WebSocket 页面来源");
  }
  const url = new URL(baseUrl || "/", pageOrigin);
  const basePath = url.pathname.replace(/\/$/, "");
  url.protocol = url.protocol === "https:" ? "wss:" : "ws:";
  url.pathname = `${basePath}/ws/session-messages`.replace(/\/{2,}/g, "/");
  url.search = "";
  if (accessToken) {
    url.searchParams.set("access_token", accessToken);
  }
  url.hash = "";
  return url.toString();
}

export function buildProviderSessionBindingFrame(localSessionId, event) {
  const providerSessionId = String(event?.session?.id || "").trim();
  if (!String(localSessionId || "").trim() || !providerSessionId) return null;
  return {
    type: "bind",
    sessionId: String(localSessionId).trim(),
    providerSessionId,
  };
}

async function unwrapResponse(response) {
  const contentType = response.headers.get("content-type") || "";
  const body = contentType.includes("application/json") ? await response.json() : await response.text();
  if (!response.ok) {
    const message = body?.message || body?.error || body?.code || `后端请求失败（${response.status}）`;
    throw new Error(message);
  }
  if (body && typeof body === "object" && "success" in body) {
    if (!body.success) throw new Error(body.message || body.code || "后端请求失败");
    return body.data ?? null;
  }
  return body;
}

function normalizeSdp(sdp) {
  const normalized = String(sdp || "").trim().replace(/\r?\n/g, "\r\n");
  return normalized.endsWith("\r\n") ? normalized : `${normalized}\r\n`;
}

function waitForIceGathering(peer) {
  if (peer.iceGatheringState === "complete") return Promise.resolve();
  return new Promise((resolve, reject) => {
    const timer = window.setTimeout(() => reject(new Error("ICE 候选收集超时")), 10_000);
    const previous = peer.onicegatheringstatechange;
    peer.onicegatheringstatechange = () => {
      previous?.();
      if (peer.iceGatheringState === "complete") {
        window.clearTimeout(timer);
        resolve();
      }
    };
  });
}

function waitForChannel(channel, peer) {
  if (channel.readyState === "open") return Promise.resolve();
  return new Promise((resolve, reject) => {
    let settled = false;
    const cleanup = () => {
      window.clearTimeout(timer);
      channel.removeEventListener?.("open", handleOpen);
      channel.removeEventListener?.("error", handleError);
      peer?.removeEventListener?.("iceconnectionstatechange", handleIceState);
    };
    const finish = (error) => {
      if (settled) return;
      settled = true;
      cleanup();
      error ? reject(error) : resolve();
    };
    const handleOpen = () => finish();
    const handleError = () => finish(new Error("实时数据通道连接失败"));
    const handleIceState = () => {
      const state = peer?.iceConnectionState;
      if (state === "failed" || state === "disconnected") {
        finish(new Error(`ICE connection ${state}`));
      }
    };
    const timer = window.setTimeout(() => {
      const iceState = peer?.iceConnectionState || "unknown";
      finish(new Error(`实时数据通道连接超时（ICE ${iceState}）`));
    }, 20_000);
    channel.onopen = () => {
      handleOpen();
    };
    channel.onerror = () => {
      handleError();
    };
    channel.addEventListener?.("open", handleOpen);
    channel.addEventListener?.("error", handleError);
    peer?.addEventListener?.("iceconnectionstatechange", handleIceState);
    handleIceState();
  });
}

function normalizedSpeechSpeed(value) {
  const speed = String(value || "").trim().toUpperCase();
  return SPEECH_SPEED_INSTRUCTIONS[speed] ? speed : DEFAULT_SPEECH_SPEED;
}

export function extractCompletedAssistantMessage(event) {
  if (event.type === "response.audio_transcript.done") {
    return {
      id: event.item_id || event.response_id || event.event_id,
      text: event.transcript || event.text,
    };
  }
  if (event.type === "response.text.done") {
    return {
      id: event.item_id || event.response_id || event.event_id,
      text: event.text || event.transcript,
    };
  }
  if (event.type === "response.content_part.done") {
    return {
      id: event.item_id || event.response_id || event.event_id,
      text: event.part?.transcript || event.part?.text,
    };
  }
  const outputItems = event.type === "response.output_item.done"
    ? [event.item]
    : event.type === "response.done"
      ? event.response?.output
      : null;
  if (!Array.isArray(outputItems)) return null;
  const item = outputItems.find((candidate) => candidate?.role === "assistant" && Array.isArray(candidate.content));
  if (!item) return null;
  const text = item.content
    .map((part) => part?.transcript || part?.text || "")
    .join("")
    .trim();
  return {
    id: item.id || event.response_id || event.response?.id || event.event_id,
    text,
  };
}

export function isActiveResponseConflict(event) {
  if (event?.type !== "error") return false;
  const message = event.error?.message || event.message || "";
  return /conversation already has an active response/i.test(message);
}

export function buildRealtimeSessionConfig({
  systemPrompt = "",
  topic = "",
  voice = DEFAULT_VOICE,
  model = DEFAULT_MODEL,
  speechSpeed = DEFAULT_SPEECH_SPEED,
  automaticTurnResponses = true,
  silenceDurationMs = 600,
  turnDetectionType = null,
  interruptResponse = true,
} = {}) {
  const selectedSpeechSpeed = normalizedSpeechSpeed(speechSpeed);
  return {
    modalities: ["text", "audio"],
    voice: voice || DEFAULT_VOICE,
    instructions: [
      systemPrompt || topic || "",
      SPEECH_SPEED_INSTRUCTIONS[selectedSpeechSpeed],
    ].filter(Boolean).join("\n\n"),
    input_audio_format: "pcm",
    output_audio_format: "pcm",
    input_audio_transcription: { model: "qwen3-asr-flash-realtime" },
    smooth_output: false,
    turn_detection: {
      type: turnDetectionType
        || (String(model || DEFAULT_MODEL).startsWith("qwen3.5-omni-") ? "semantic_vad" : "server_vad"),
      threshold: 0.5,
      prefix_padding_ms: 500,
      silence_duration_ms: Math.max(600, Number(silenceDurationMs) || 600),
      create_response: Boolean(automaticTurnResponses),
      interrupt_response: Boolean(interruptResponse),
    },
  };
}

export function createTurnAudioCaptureController(recorder) {
  let active = false;
  let finalized = false;
  let audioPromise = Promise.resolve(null);

  const stop = () => {
    if (!recorder || !active) return false;
    audioPromise = recorder.stopSegment();
    active = false;
    finalized = true;
    return true;
  };

  return {
    start() {
      if (!recorder || active || finalized) return false;
      recorder.startSegment();
      active = true;
      return true;
    },
    stop,
    async take() {
      if (active) stop();
      const audio = await audioPromise;
      audioPromise = Promise.resolve(null);
      finalized = false;
      return audio;
    },
  };
}

export function createRealtimeClient({
  apiBase = import.meta.env?.VITE_BACKEND_URL || DEFAULT_API_BASE,
  sceneId = null,
  sceneType = "free-chat",
  onEvent = () => {},
  onRemoteStream = () => {},
} = {}) {
  const base = normalizeBaseUrl(apiBase);
  const customSceneId = sceneType === "custom" ? sceneId : null;
  const ieltsSceneId = sceneType === "ielts" ? sceneId : null;
  const interviewSceneId = sceneType === "interview" ? sceneId : null;
  const manualTurnResponses = Boolean(customSceneId || ieltsSceneId || interviewSceneId);
  let peer = null;
  let channel = null;
  let sessionSocket = null;
  let localStream = null;
  let audioSender = null;
  let sessionId = null;
  let sessionConfig = null;
  let pendingAcks = [];
  let persistedMessageIds = new Set();
  let muted = false;
  let paused = false;
  let started = false;
  let inputReady = false;
  let sessionUpdateAcknowledged = false;
  let sessionUpdateRetryTimer = null;
  let initialResponseStarted = false;
  let initialResponseFallbackTimer = null;
  let stopPromise = null;
  let segmentRecorder = null;
  let learnerTurnNo = 0;
  let pendingOperations = new Set();
  let pendingEvaluationOperations = new Set();
  let baseSessionInstructions = "";
  let scenarioCompletionPending = false;
  let scenarioCompletionEmitted = false;
  let scenarioCompletionTimer = null;
  let scenarioAudioDrainTimer = null;
  let responsePending = false;
  let closingResponseRequested = false;
  let statePipeline = Promise.resolve();
  let ieltsActivePart = null;
  let ieltsPreparedQuestions = [];
  let ieltsDialogueCompleted = false;
  let ieltsInputRecoveryTimer = null;
  let turnAudioCapture = null;
  let pendingInstructionUpdate = null;
  let instructionUpdateQueue = Promise.resolve(true);
  let activeInputItemId = null;
  let ieltsTimedOutTurn = null;
  let closingInstructions = "";
  let pendingInterviewReportStatus = null;
  let providerSessionId = null;
  let providerSessionBound = false;

  const emit = (event) => onEvent(event);

  function setTrackEnabled() {
    const track = localStream?.getAudioTracks?.()[0];
    const enabled = started && inputReady && !muted && !paused;
    if (track) track.enabled = enabled;
    if (enabled && (customSceneId || ieltsSceneId || interviewSceneId)) turnAudioCapture?.start();
  }

  function isDeterministicIeltsPart() {
    return ieltsActivePart === "PART_1" || ieltsActivePart === "PART_3";
  }

  function clearIeltsInputRecovery() {
    if (!ieltsInputRecoveryTimer) return;
    window.clearTimeout(ieltsInputRecoveryTimer);
    ieltsInputRecoveryTimer = null;
  }

  function releaseIeltsInput(source) {
    clearIeltsInputRecovery();
    if (!isDeterministicIeltsPart()
      || ieltsDialogueCompleted
      || !started
      || paused
      || muted) {
      return;
    }
    responsePending = false;
    inputReady = true;
    setTrackEnabled();
    emit({ type: "local.ielts_input_ready", source });
  }

  function scheduleIeltsInputRecovery() {
    if (!isDeterministicIeltsPart() || ieltsDialogueCompleted) return;
    clearIeltsInputRecovery();
    ieltsInputRecoveryTimer = window.setTimeout(() => {
      ieltsInputRecoveryTimer = null;
      releaseIeltsInput("assistant_output_fallback");
    }, IELTS_INPUT_RECOVERY_MS);
  }

  function withTimeout(operation, timeoutMs, message) {
    let timer = null;
    return Promise.race([
      operation,
      new Promise((_, reject) => {
        timer = window.setTimeout(() => reject(new Error(message)), timeoutMs);
      }),
    ]).finally(() => {
      if (timer) window.clearTimeout(timer);
    });
  }

  function exactIeltsInstruction(utterance, completed) {
    const partLabel = ieltsActivePart?.replace("_", " ") || "PART";
    return `# IELTS ${partLabel} — Runtime State: ${completed ? "FINISHED" : "PREPARED_QUESTIONS"}\n\n`
      + "The question sequence is controlled by the application. Never greet, evaluate, encourage, "
      + "explain, add a transition, ask a follow-up, or repeat an earlier question. Keep the examiner "
      + "turn as short as possible. Your entire spoken response must be exactly this sentence, with no "
      + `words before or after it:\n\n\"${String(utterance || "").replaceAll("\\", "\\\\").replaceAll('"', '\\"')}\"`;
  }

  function fallbackIeltsState(turnNo) {
    const totalQuestions = ieltsPreparedQuestions.length;
    if (!totalQuestions) return null;
    const isPart1 = ieltsActivePart === "PART_1";
    const answeredQuestions = Math.min(
      totalQuestions,
      Math.max(0, isPart1 ? turnNo - 1 : turnNo),
    );
    const completed = answeredQuestions >= totalQuestions;
    const utterance = completed
      ? ieltsActivePart === "PART_3"
        ? "Thank you. That is the end of the speaking test."
        : "Thank you. That is the end of Part 1."
      : ieltsPreparedQuestions[answeredQuestions];
    return {
      sceneId: ieltsSceneId,
      sessionId,
      part: ieltsActivePart,
      openingCompleted: !isPart1 || turnNo >= 1,
      answeredQuestions,
      totalQuestions,
      completed,
      controlInstruction: exactIeltsInstruction(utterance, completed),
      fallback: true,
    };
  }

  function rejectPendingAcks(error) {
    pendingAcks.forEach(({ timer, reject }) => {
      window.clearTimeout(timer);
      reject(error);
    });
    pendingAcks = [];
  }

  function handleSessionAck(message) {
    let ack;
    try {
      ack = JSON.parse(message.data);
    } catch {
      return;
    }
    const operation = String(ack.type || "").split(".")[1];
    const index = pendingAcks.findIndex((pending) => pending.operation === operation);
    if (index < 0) return;
    const [pending] = pendingAcks.splice(index, 1);
    window.clearTimeout(pending.timer);
    if (ack.success) {
      pending.resolve(ack);
    } else {
      pending.reject(new Error(ack.message || ack.code || "会话消息处理失败"));
    }
  }

  function connectSessionSocket() {
    if (sessionSocket?.readyState === WebSocket.OPEN) return Promise.resolve(sessionSocket);
    if (sessionSocket?.readyState === WebSocket.CONNECTING) {
      return new Promise((resolve, reject) => {
        sessionSocket.addEventListener("open", () => resolve(sessionSocket), { once: true });
        sessionSocket.addEventListener("error", () => reject(new Error("会话 WebSocket 连接失败")), { once: true });
      });
    }
    const accessToken = getAccessToken();
    if (!accessToken) {
      return Promise.reject(new Error("请先登录后再建立会话 WebSocket"));
    }
    sessionSocket = new WebSocket(websocketUrl(base, accessToken));
    sessionSocket.onmessage = handleSessionAck;
    sessionSocket.onclose = () => {
      rejectPendingAcks(new Error("会话 WebSocket 已关闭"));
    };
    return new Promise((resolve, reject) => {
      const timer = window.setTimeout(() => reject(new Error("会话 WebSocket 连接超时")), 5_000);
      sessionSocket.onopen = () => {
        window.clearTimeout(timer);
        resolve(sessionSocket);
      };
      sessionSocket.onerror = () => {
        window.clearTimeout(timer);
        reject(new Error("会话 WebSocket 连接失败"));
      };
    });
  }

  async function sendSessionFrame(type, message = null, stopTime = null, providerSessionId = null) {
    if (!sessionId) throw new Error("会话 ID 尚未建立");
    const socket = await connectSessionSocket();
    const operation = type === "end" ? "end" : type === "bind" ? "bind" : "message";
    const ack = new Promise((resolve, reject) => {
      const timer = window.setTimeout(() => {
        pendingAcks = pendingAcks.filter((pending) => pending.resolve !== resolve);
        reject(new Error(`等待 session.${operation}.accepted 超时`));
      }, 5_000);
      pendingAcks.push({ operation, resolve, reject, timer });
    });
    socket.send(JSON.stringify({
      type,
      sessionId,
      message,
      stopTime,
      providerSessionId,
    }));
    return ack;
  }

  async function ensureProviderSessionBinding(nextProviderSessionId) {
    const normalized = String(nextProviderSessionId || "").trim();
    if (!normalized) return false;
    providerSessionId = normalized;
    if (providerSessionBound) return true;
    for (let attempt = 0; attempt < 2; attempt += 1) {
      try {
        await sendSessionFrame("bind", null, null, normalized);
        providerSessionBound = true;
        return true;
      } catch (error) {
        if (attempt === 1) {
          emit({ type: "local.backend_warning", message: `服务商会话绑定失败：${error.message}` });
        }
      }
    }
    return false;
  }

  async function addSessionMessage(owner, content, providerMessageId) {
    const text = String(content || "").trim();
    if (!text) return false;
    const messageKey = providerMessageId ? `${owner}:${providerMessageId}` : null;
    if (messageKey && persistedMessageIds.has(messageKey)) return false;
    if (messageKey) persistedMessageIds.add(messageKey);
    emit({
      type: "local.transcript.final",
      owner,
      itemId: providerMessageId || eventId("transcript"),
      text,
    });
    const operation = sendSessionFrame("message", {
      owner,
      content: text,
      audio: null,
    }).catch((error) => {
      if (messageKey) persistedMessageIds.delete(messageKey);
      emit({ type: "local.backend_warning", message: error.message });
      throw error;
    });
    pendingOperations.add(operation);
    try {
      await operation;
      return true;
    } finally {
      pendingOperations.delete(operation);
    }
  }

  function sendProviderEvent(event) {
    if (!channel || channel.readyState !== "open") {
      throw new Error("实时数据通道尚未连接");
    }
    channel.send(JSON.stringify(event));
  }

  function clearInitializationTimers() {
    if (sessionUpdateRetryTimer) {
      window.clearTimeout(sessionUpdateRetryTimer);
      sessionUpdateRetryTimer = null;
    }
    if (initialResponseFallbackTimer) {
      window.clearTimeout(initialResponseFallbackTimer);
      initialResponseFallbackTimer = null;
    }
  }

  function sendSessionUpdate() {
    sendProviderEvent({
      event_id: eventId("config"),
      type: "session.update",
      session: sessionConfig,
    });
  }

  function sendInstructionUpdateAndWait() {
    return new Promise((resolve, reject) => {
      const timer = window.setTimeout(() => {
        if (pendingInstructionUpdate?.resolve === resolve) {
          pendingInstructionUpdate = null;
        }
        reject(new Error("等待实时会话指令更新确认超时"));
      }, SESSION_UPDATE_TIMEOUT_MS);
      pendingInstructionUpdate = {
        resolve,
        reject,
        timer,
        expectedInstructions: sessionConfig.instructions,
      };
      try {
        sendProviderEvent({
          event_id: eventId("instructions"),
          type: "session.update",
          session: { instructions: sessionConfig.instructions },
        });
      } catch (error) {
        window.clearTimeout(timer);
        pendingInstructionUpdate = null;
        reject(error);
      }
    });
  }

  function settleInstructionUpdate(error = null, appliedInstructions = null) {
    if (!pendingInstructionUpdate) return false;
    const pending = pendingInstructionUpdate;
    pendingInstructionUpdate = null;
    window.clearTimeout(pending.timer);
    if (error) pending.reject(error);
    else pending.resolve(true);
    return true;
  }

  async function waitForInstructionUpdate() {
    try {
      await instructionUpdateQueue;
    } catch (error) {
      emit({
        type: "local.provider_warning",
        message: error instanceof Error ? error.message : "实时会话指令更新失败",
      });
    }
  }

  function requestInitialResponse() {
    if (initialResponseStarted) return;
    sendProviderEvent(buildResponseCreateEvent({ id: eventId("greeting") }));
    initialResponseFallbackTimer = window.setTimeout(() => {
      initialResponseFallbackTimer = null;
      inputReady = true;
      setTrackEnabled();
      emit({ type: "local.greeting_timeout" });
    }, 5_000);
  }

  function emitScenarioCompleted() {
    if (!scenarioCompletionPending || scenarioCompletionEmitted) return;
    if (scenarioCompletionTimer) {
      window.clearTimeout(scenarioCompletionTimer);
      scenarioCompletionTimer = null;
    }
    if (scenarioAudioDrainTimer) {
      window.clearTimeout(scenarioAudioDrainTimer);
      scenarioAudioDrainTimer = null;
    }
    scenarioCompletionEmitted = true;
    if (interviewSceneId) {
      emit({ type: "local.interview_end_requested", reportStatus: pendingInterviewReportStatus });
    } else {
      emit({ type: "local.scenario_completed" });
    }
    void stop({ reason: "state_machine" }).catch((error) => {
      emit({
        type: interviewSceneId ? "local.interview_end_error" : "local.scenario_completion_error",
        message: error instanceof Error ? error.message
          : interviewSceneId ? "面试自动结束失败" : "场景自动结束失败",
      });
    });
  }

  function armScenarioCompletionTimeout() {
    if (!scenarioCompletionPending || scenarioCompletionTimer) return;
    scenarioCompletionTimer = window.setTimeout(() => {
      scenarioCompletionTimer = null;
      emitScenarioCompleted();
    }, SCENARIO_CLOSING_TIMEOUT_MS);
  }

  function scheduleScenarioCompletionAfterAudioDrain() {
    if (!scenarioCompletionPending || scenarioAudioDrainTimer) return;
    scenarioAudioDrainTimer = window.setTimeout(() => {
      scenarioAudioDrainTimer = null;
      emitScenarioCompleted();
    }, SCENARIO_AUDIO_DRAIN_MS);
  }

  function requestTurnResponse({ closing = false, instructions = "" } = {}) {
    if (responsePending) return false;
    responsePending = true;
    if (closing) {
      closingResponseRequested = true;
      armScenarioCompletionTimeout();
    }
    try {
      sendProviderEvent(buildResponseCreateEvent({
        id: eventId(closing ? "closing_response" : "turn_response"),
        instructions,
      }));
    } catch (error) {
      responsePending = false;
      if (closing) closingResponseRequested = false;
      throw error;
    }
    return true;
  }

  async function transitionIeltsPart2(eventName) {
    if (!ieltsSceneId || ieltsActivePart !== "PART_2" || !sessionId) {
      throw new Error("当前会话不是 IELTS Part 2");
    }
    const completing = eventName === "ANSWER_COMPLETE"
      || eventName === "LONG_TURN_TIME_LIMIT";
    if (completing) {
      inputReady = false;
      muted = true;
      setTrackEnabled();
      turnAudioCapture?.stop();
    }
    const state = await withTimeout(
      advanceIeltsPart2State(ieltsSceneId, sessionId, eventName),
      IELTS_STATE_TIMEOUT_MS,
      "IELTS Part 2 状态推进超时",
    );
    ieltsDialogueCompleted = Boolean(state?.completed);
    if (eventName === "PREPARATION_COMPLETE") {
      muted = false;
      inputReady = false;
      setTrackEnabled();
    }
    emit({ type: "local.ielts_part2_state", state, event: eventName });
    requestTurnResponse({ instructions: state?.controlInstruction || "" });
    return state;
  }

  async function forceIeltsPart3TurnTimeout() {
    if (!ieltsSceneId || ieltsActivePart !== "PART_3" || !sessionId) {
      throw new Error("当前会话不是 IELTS Part 3");
    }
    if (ieltsDialogueCompleted || ieltsTimedOutTurn) return null;
    const turnNo = learnerTurnNo + 1;
    inputReady = false;
    muted = true;
    setTrackEnabled();
    turnAudioCapture?.stop();
    const state = await withTimeout(
      advanceIeltsDialogueState(ieltsSceneId, sessionId, turnNo, true),
      IELTS_STATE_TIMEOUT_MS,
      "IELTS Part 3 单题超时推进失败",
    );
    learnerTurnNo = turnNo;
    ieltsTimedOutTurn = { turnNo, itemId: activeInputItemId };
    ieltsDialogueCompleted = Boolean(state?.completed);
    emit({ type: "local.ielts_state", state, timedOut: true });
    requestTurnResponse({ instructions: state?.controlInstruction || "" });
    muted = false;
    setTrackEnabled();
    return state;
  }

  function applyScenarioState(state) {
    if (!state) return;
    emit({ type: "local.scenario_state", state });
    const instruction = String(state.controlInstruction || "").trim();
    if (instruction && sessionConfig && channel?.readyState === "open") {
      sessionConfig = {
        ...sessionConfig,
        instructions: [baseSessionInstructions, instruction]
          .filter(Boolean)
          .join("\n\n"),
      };
      sendSessionUpdate();
    }
    if (!state.completed) return;
    scenarioCompletionPending = true;
    inputReady = false;
    setTrackEnabled();
    turnAudioCapture?.stop();
    if (!responsePending) {
      requestTurnResponse({ closing: true });
    }
  }

  async function postStart({ offerSdp, voice, model }) {
    const accessToken = getAccessToken();
    const path = customSceneId
      ? `/api/custom-scenes/${encodeURIComponent(customSceneId)}/sessions`
      : ieltsSceneId
        ? `/api/ielts/${encodeURIComponent(ieltsSceneId)}/sessions`
        : interviewSceneId
          ? `/api/interview-scenes/${encodeURIComponent(interviewSceneId)}/sessions`
      : "/api/scene-sessions";
    const response = await fetch(`${base}${path}`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
      },
      body: JSON.stringify({
        offerSdp,
        provider: "QWEN",
        model: model || DEFAULT_MODEL,
        ...(ieltsSceneId
          ? { voiceId: voice || DEFAULT_VOICE }
          : { voice: voice || DEFAULT_VOICE }),
        translationEnabled: true,
      }),
    });
    return unwrapResponse(response);
  }

  async function handleProviderEvent(raw) {
    let event;
    try {
      event = typeof raw === "string" ? JSON.parse(raw) : raw;
    } catch {
      emit({ type: "local.error", message: "收到无法解析的模型事件" });
      return;
    }

    if (isActiveResponseConflict(event)) {
      initialResponseStarted = true;
      if (scenarioCompletionPending && closingResponseRequested) {
        closingResponseRequested = false;
      }
      responsePending = true;
      inputReady = !manualTurnResponses && !scenarioCompletionPending;
      if (initialResponseFallbackTimer) {
        window.clearTimeout(initialResponseFallbackTimer);
        initialResponseFallbackTimer = null;
      }
      setTrackEnabled();
      emit({ type: "local.provider_warning", message: event.error?.message || event.message });
      return;
    }

    emit(event);

    if (event.type === "error"
      && pendingInstructionUpdate
      && /session(?:\s|\.)*update|instructions?/i.test(event.error?.message || event.message || "")) {
      settleInstructionUpdate(new Error(event.error?.message || event.message));
      return;
    }

    if (event.type === "session.created") {
      started = true;
      const binding = buildProviderSessionBindingFrame(sessionId, event);
      if (binding) {
        const operation = ensureProviderSessionBinding(binding.providerSessionId);
        pendingOperations.add(operation);
        try {
          await operation;
        } finally {
          pendingOperations.delete(operation);
        }
      } else {
        emit({ type: "local.backend_warning", message: "服务商未返回 session.created 会话标识，用量无法归属" });
      }
      const audioTrack = localStream?.getAudioTracks?.()[0];
      if (audioTrack && audioSender?.track !== audioTrack) {
        await audioSender?.replaceTrack(audioTrack);
      }
      setTrackEnabled();
      sendSessionUpdate();
      sessionUpdateRetryTimer = window.setTimeout(() => {
        sessionUpdateRetryTimer = null;
        if (!sessionUpdateAcknowledged && channel?.readyState === "open") {
          sendSessionUpdate();
        }
      }, 2_000);
      return;
    }

    if (event.type === "session.updated") {
      settleInstructionUpdate(null, event.session?.instructions);
      if (sessionUpdateAcknowledged) return;
      sessionUpdateAcknowledged = true;
      if (sessionUpdateRetryTimer) {
        window.clearTimeout(sessionUpdateRetryTimer);
        sessionUpdateRetryTimer = null;
      }
      inputReady = !manualTurnResponses;
      setTrackEnabled();
      requestInitialResponse();
      return;
    }

    if (event.type === "input_audio_buffer.speech_started") {
      if (scenarioCompletionPending
        || (ieltsActivePart === "PART_2" && ieltsDialogueCompleted)) return;
      clearIeltsInputRecovery();
      const startedItemId = event.item_id || event.item?.id || null;
      if (ieltsTimedOutTurn
        && !responsePending
        && (!ieltsTimedOutTurn.itemId || ieltsTimedOutTurn.itemId !== startedItemId)) {
        ieltsTimedOutTurn = null;
      }
      activeInputItemId = startedItemId;
      turnAudioCapture?.start();
      return;
    }

    if (event.type === "input_audio_buffer.speech_stopped") {
      turnAudioCapture?.stop();
      return;
    }

    if (event.type === "response.created") {
      clearIeltsInputRecovery();
      initialResponseStarted = true;
      responsePending = true;
      inputReady = !manualTurnResponses;
      if (initialResponseFallbackTimer) {
        window.clearTimeout(initialResponseFallbackTimer);
        initialResponseFallbackTimer = null;
      }
      setTrackEnabled();
      return;
    }

    if (event.type === "conversation.item.input_audio_transcription.completed") {
      if (scenarioCompletionPending
        || (ieltsActivePart === "PART_2" && ieltsDialogueCompleted)) {
        turnAudioCapture?.stop();
        return;
      }
      const transcript = String(event.transcript || event.text || "").trim();
      if (!transcript) return;
      const completedInputItemId = event.item_id || event.item?.id || null;
      const timedOutTurn = ieltsActivePart === "PART_3"
        && ieltsTimedOutTurn
        && (!ieltsTimedOutTurn.itemId
          || !completedInputItemId
          || ieltsTimedOutTurn.itemId === completedInputItemId)
          ? ieltsTimedOutTurn
          : null;
      if (manualTurnResponses) {
        inputReady = false;
        setTrackEnabled();
      }
      turnAudioCapture?.stop();
      const persistenceOperation = addSessionMessage(
        1,
        transcript,
        event.item_id || event.item?.id || event.event_id,
      );
      if (customSceneId) {
        requestTurnResponse();
      }
      const ieltsTurnNo = ieltsSceneId
        ? timedOutTurn?.turnNo || learnerTurnNo + 1
        : null;
      const deterministicIeltsPart = ieltsActivePart === "PART_1"
        || ieltsActivePart === "PART_3";
      const ieltsStateOperation = deterministicIeltsPart && !timedOutTurn
        ? withTimeout(
          advanceIeltsDialogueState(
            ieltsSceneId,
            sessionId,
            ieltsTurnNo,
          ),
          IELTS_STATE_TIMEOUT_MS,
          "IELTS 题目状态推进超时",
        )
        : null;
      let persisted = false;
      try {
        persisted = await persistenceOperation;
      } catch (persistenceError) {
        if (!ieltsSceneId) throw persistenceError;
      }
      if (ieltsSceneId) {
        let turnInstructions = "";
        if (ieltsStateOperation) {
          let state = null;
          try {
            state = await ieltsStateOperation;
          } catch (stateError) {
            state = fallbackIeltsState(ieltsTurnNo);
            emit({
              type: state ? "local.ielts_state_recovered" : "local.ielts_state_error",
              message: stateError instanceof Error
                ? stateError.message
                : "IELTS 题目状态推进失败",
            });
          }
          if (state) {
            ieltsDialogueCompleted = Boolean(state.completed);
            if (ieltsDialogueCompleted) clearIeltsInputRecovery();
            emit({ type: "local.ielts_state", state });
            turnInstructions = String(state.controlInstruction || "").trim();
          }
        } else if (ieltsActivePart === "PART_2") {
          if (!ieltsDialogueCompleted) {
            inputReady = true;
            setTrackEnabled();
            emit({ type: "local.ielts_input_ready", source: "part2_continue" });
          }
        } else if (timedOutTurn) {
          ieltsTimedOutTurn = null;
        } else {
          await waitForInstructionUpdate();
        }
        if (ieltsActivePart !== "PART_2" && !timedOutTurn) {
          requestTurnResponse({ instructions: turnInstructions });
        }
      }
      let ieltsTurnAudio = null;
      if (ieltsSceneId) {
        learnerTurnNo = Math.max(learnerTurnNo, ieltsTurnNo);
        ieltsTurnAudio = await turnAudioCapture?.take();
      }
      if (ieltsSceneId && persisted) {
        const turnNo = ieltsTurnNo;
        const evaluationOperation = evaluateIeltsDialogueTurn(
          ieltsSceneId,
          sessionId,
          turnNo,
          transcript,
          ieltsTurnAudio,
        );
        pendingEvaluationOperations.add(evaluationOperation);
        void evaluationOperation.then((turnResult) => {
          emit({
            type: "local.turn_evaluation",
            evaluation: turnResult?.evaluation || turnResult,
            scenarioState: null,
          });
        }).catch((error) => {
          emit({
            type: "local.turn_evaluation_error",
            turnNo,
            message: error instanceof Error ? error.message : "IELTS 本轮评分失败",
          });
        }).finally(() => {
          pendingEvaluationOperations.delete(evaluationOperation);
        });
      }
      if (customSceneId && persisted) {
        const turnNo = ++learnerTurnNo;
        const wavAudio = await turnAudioCapture?.take();
        const stateOperation = statePipeline.then(() => advanceCustomDialogueState(
          customSceneId,
          sessionId,
          turnNo,
          transcript,
        ));
        statePipeline = stateOperation.catch(() => null);
        const evaluationOperation = evaluateCustomDialogueTurn(
          customSceneId,
          sessionId,
          turnNo,
          transcript,
          wavAudio,
        );
        pendingOperations.add(stateOperation);
        pendingOperations.add(evaluationOperation);
        void evaluationOperation.then((turnResult) => {
          const evaluation = turnResult?.evaluation || turnResult;
          emit({
            type: "local.turn_evaluation",
            evaluation,
            scenarioState: null,
          });
        }).catch((error) => {
          emit({
            type: "local.turn_evaluation_error",
            turnNo,
            message: error instanceof Error ? error.message : "本轮评分失败",
          });
        }).finally(() => {
          pendingOperations.delete(evaluationOperation);
        });
        try {
          const scenarioState = await stateOperation;
          applyScenarioState(scenarioState);
        } catch (error) {
          emit({
            type: "local.scenario_state_error",
            turnNo,
            message: error instanceof Error ? error.message : "场景状态推进失败",
          });
        } finally {
          pendingOperations.delete(stateOperation);
        }
      }
      if (interviewSceneId && persisted) {
        const turnNo = ++learnerTurnNo;
        const wavAudio = await turnAudioCapture?.take();
        const turnFormData = new FormData();
        turnFormData.append("transcript", transcript);
        if (wavAudio) {
          turnFormData.append("audio", wavAudio, `interview-turn-${turnNo}.wav`);
        }
        const turnOperation = submitInterviewTurn(
          interviewSceneId,
          sessionId,
          turnNo,
          turnFormData,
        );
        pendingOperations.add(turnOperation);
        try {
          const turnResult = await turnOperation;
          const state = turnResult?.state || null;
          const reportStatus = turnResult?.reportStatus || null;
          emit({
            type: "local.interview_state",
            turnNo,
            state,
            reportStatus,
          });
          if (state?.shouldEnd) {
            pendingInterviewReportStatus = reportStatus ?? null;
            closingInstructions = String(state.controlInstruction || "").trim()
              || DEFAULT_INTERVIEW_CLOSING;
            scenarioCompletionPending = true;
            inputReady = false;
            setTrackEnabled();
            turnAudioCapture?.stop();
            armScenarioCompletionTimeout();
            emit({ type: "local.interview_closing" });
            if (!responsePending) {
              requestTurnResponse({ closing: true, instructions: closingInstructions });
            }
          } else {
            requestTurnResponse({
              instructions:
                state?.controlInstruction ||
                (state?.currentTopic
                  ? `Current interview topic: ${state.currentTopic}. Continue the interview naturally — ask a focused follow-up within this topic, or transition to the NEXT topic in the interview flow when this one is covered. Do not skip topics.`
                  : ""),
            });
          }
        } catch (error) {
          emit({
            type: "local.turn_evaluation_error",
            turnNo,
            message: error instanceof Error ? error.message : "本轮面试提交失败",
          });
        } finally {
          pendingOperations.delete(turnOperation);
        }
      }
      return;
    }

    const completedAssistantMessage = extractCompletedAssistantMessage(event);
    if (completedAssistantMessage) {
      scheduleIeltsInputRecovery();
      if (scenarioCompletionPending && interviewSceneId) {
        // 收尾期间会话已被后端终态化：跳过 WS 落库（否则 Session not found），仅展示收尾语
        const closingText = String(completedAssistantMessage.text || "").trim();
        if (closingText) {
          emit({
            type: "local.transcript.final",
            owner: 0,
            itemId: completedAssistantMessage.id || eventId("transcript"),
            text: closingText,
          });
        }
      } else {
        await addSessionMessage(
          0,
          completedAssistantMessage.text,
          completedAssistantMessage.id,
        );
      }
    }
    if (event.type === "response.done") {
      responsePending = false;
      if (ieltsActivePart === "PART_2" && ieltsDialogueCompleted) {
        inputReady = false;
        setTrackEnabled();
        emit({ type: "local.ielts_part2_completion_ready" });
      }
      if (scenarioCompletionPending) {
        if (closingResponseRequested) {
          if (scenarioCompletionTimer) {
            window.clearTimeout(scenarioCompletionTimer);
            scenarioCompletionTimer = null;
          }
          scheduleScenarioCompletionAfterAudioDrain();
        } else {
          requestTurnResponse({ closing: true, instructions: closingInstructions });
        }
      } else if (manualTurnResponses) {
        if (isDeterministicIeltsPart()) {
          releaseIeltsInput("response_done");
        } else {
          inputReady = true;
          setTrackEnabled();
          emit({ type: "local.ielts_input_ready", source: "response_done" });
        }
      }
    }
  }

  async function start({
    voice = DEFAULT_VOICE,
    model = DEFAULT_MODEL,
    speechSpeed = DEFAULT_SPEECH_SPEED,
    silenceDurationMs = null,
    turnDetectionType = null,
    interruptResponse = null,
  } = {}) {
    if (peer) return { sessionId };
    emit({ type: "local.connecting" });

    try {
      peer = new RTCPeerConnection({ iceServers: defaultIceServers() });
      peer.ontrack = (event) => {
        const stream = event.streams?.[0];
        if (stream) onRemoteStream(stream);
      };
      peer.onconnectionstatechange = () => {
        emit({ type: "local.connection_state", state: peer?.connectionState });
      };

      localStream = await navigator.mediaDevices.getUserMedia({
        audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true },
      });
      if (customSceneId || ieltsSceneId || interviewSceneId) {
        try {
          segmentRecorder = await withTimeout(
            createPcmWavSegmentRecorder(localStream), 3_000, "录音器初始化超时");
          turnAudioCapture = createTurnAudioCaptureController(segmentRecorder);
        } catch (recorderError) {
          segmentRecorder = null;
          turnAudioCapture = null;
          emit({ type: "local.provider_warning", message: "逐轮录音不可用，本轮评分将仅基于文本" });
        }
      }
      const audioTrack = localStream.getAudioTracks()[0];
      audioTrack.enabled = false;
      audioSender = peer.addTrack(audioTrack, localStream);
      await audioSender.replaceTrack(null);

      channel = peer.createDataChannel(DATA_CHANNEL_LABEL);
      channel.onmessage = (message) => { void handleProviderEvent(message.data); };
      peer.ondatachannel = (event) => {
        const incoming = event.channel;
        incoming.onmessage = (message) => { void handleProviderEvent(message.data); };
      };

      const offer = await peer.createOffer();
      await peer.setLocalDescription(offer);
      await waitForIceGathering(peer);

      const backend = await postStart({
        offerSdp: peer.localDescription?.sdp || offer.sdp || "",
        voice,
        model,
      });
      sessionId = backend.sessionId;
      ieltsActivePart = backend.currentStage || null;
      ieltsPreparedQuestions = Array.isArray(
        backend.content?.[ieltsActivePart === "PART_1" ? "part1" : "part3"],
      )
        ? backend.content[ieltsActivePart === "PART_1" ? "part1" : "part3"]
          .map((item) => String(item?.question || "").trim())
          .filter(Boolean)
        : [];
      ieltsDialogueCompleted = false;
      const finalSystemPrompt = String(backend.systemPrompt || "").trim();
      if (!finalSystemPrompt) {
        throw new Error("后端没有返回由 SceneService 生成的五层提示词");
      }
      sessionConfig = buildRealtimeSessionConfig({
        systemPrompt: finalSystemPrompt,
        voice: backend.voiceId || DEFAULT_VOICE,
        model,
        speechSpeed,
        automaticTurnResponses: !manualTurnResponses,
        // 场景覆盖：Interview 由调用方显式传 1500；IELTS 保持确定性 3000；其余回落 600
        silenceDurationMs: silenceDurationMs ?? (ieltsSceneId ? 3_000 : 600),
        turnDetectionType: turnDetectionType ?? (isDeterministicIeltsPart() ? "server_vad" : null),
        interruptResponse: interruptResponse ?? (ieltsActivePart !== "PART_2"),
      });
      baseSessionInstructions = sessionConfig.instructions;

      await connectSessionSocket();
      await peer.setRemoteDescription({ type: "answer", sdp: normalizeSdp(backend.answerSdp) });
      await waitForChannel(channel, peer);
      emit({ type: "local.connected", sessionId, backend });
      return { sessionId, backend };
    } catch (error) {
      await stop({ notifyBackend: false, reason: "start_failed", emitEnded: false });
      const mic = isMicFailure(error);
		const message = mic
		  ? micFailureMessage(error) || "无法访问麦克风"
		  : realtimeFailureMessage(error);
      emit({
        type: mic ? "local.mic_error" : "local.error",
        message,
      });
      throw mic ? error : new Error(message, { cause: error });
    }
  }

  function setMuted(value) {
    muted = Boolean(value);
    setTrackEnabled();
    emit({ type: "local.muted", muted });
    return muted;
  }

  async function pause() {
    paused = true;
    setTrackEnabled();
    emit({ type: "local.paused" });
  }

  async function resume() {
    paused = false;
    setTrackEnabled();
    emit({ type: "local.resumed" });
  }

  async function interrupt() {
    if (channel?.readyState === "open") {
      sendProviderEvent({ event_id: eventId("cancel"), type: "response.cancel" });
    }
    emit({ type: "local.interrupted" });
  }

  function requestResponse() {
    if (!channel || channel.readyState !== "open") {
      throw new Error("实时数据通道尚未连接");
    }
    return requestTurnResponse();
  }

  function updateInstructions(additionalInstructions = "", { replaceBase = false } = {}) {
    if (!sessionConfig || !channel || channel.readyState !== "open") {
      return Promise.reject(new Error("实时数据通道尚未连接"));
    }
    const instruction = String(additionalInstructions || "").trim();
    instructionUpdateQueue = instructionUpdateQueue
      .catch(() => false)
      .then(() => {
        sessionConfig = {
          ...sessionConfig,
          instructions: replaceBase
            ? instruction
            : [baseSessionInstructions, instruction]
              .filter(Boolean)
              .join("\n\n"),
        };
        return sendInstructionUpdateAndWait();
      });
    return instructionUpdateQueue;
  }

  async function waitForPendingOperations(maxWaitMs) {
    const deadline = Date.now() + maxWaitMs;
    while (Date.now() < deadline) {
      const operations = [...pendingOperations];
      if (!operations.length) {
        // A message persistence promise can settle immediately before its
        // continuation enqueues the corresponding turn evaluation.
        await new Promise((resolve) => window.setTimeout(resolve, 50));
        if (!pendingOperations.size) return;
        continue;
      }
      let timeout = null;
      const remaining = Math.max(0, deadline - Date.now());
      try {
        await Promise.race([
          Promise.allSettled(operations),
          new Promise((resolve) => {
            timeout = window.setTimeout(resolve, remaining);
          }),
        ]);
      } finally {
        if (timeout) window.clearTimeout(timeout);
      }
    }
  }

  async function waitForEvaluations(maxWaitMs = 30_000) {
    const operations = [...pendingEvaluationOperations];
    if (!operations.length) return;
    let timeout = null;
    try {
      await Promise.race([
        Promise.allSettled(operations),
        new Promise((resolve) => {
          timeout = window.setTimeout(resolve, maxWaitMs);
        }),
      ]);
    } finally {
      if (timeout) window.clearTimeout(timeout);
    }
  }

  async function performStop({
    notifyBackend = true,
    reason = "user_stop",
    emitEnded = true,
    awaitEvaluations = true,
  } = {}) {
    clearInitializationTimers();
    clearIeltsInputRecovery();
    settleInstructionUpdate(new Error("实时会话已结束"));
    started = false;
    inputReady = false;
    setTrackEnabled();
    localStream?.getTracks?.().forEach((track) => track.stop());

    // IELTS 的整场评分依赖逐轮发音评分已落库；结束会话前给评分请求
    // 足够时间完成，避免总评先于最后一轮 turn_evaluation 查询。
    await waitForPendingOperations(reason === "user_stop" ? 15_000 : 8_000);
    if (awaitEvaluations) {
      await waitForEvaluations(reason === "user_stop" ? 30_000 : 15_000);
    }
    const stopTime = new Date().toISOString();
    const endingSessionId = sessionId;
    if (providerSessionId && !providerSessionBound) {
      await ensureProviderSessionBinding(providerSessionId);
    }
    const endRequest = notifyBackend && sessionId
      ? customSceneId
        ? completeCustomDialogue(customSceneId, sessionId, stopTime)
        : interviewSceneId
          ? endInterview(interviewSceneId, sessionId)
          : sendSessionFrame("end", null, stopTime)
      : Promise.resolve(null);

    try { channel?.close?.(); } catch { /* already closed */ }
    try { peer?.close?.(); } catch { /* already closed */ }
    let completion = null;
    let completionError = null;
    try {
      completion = await endRequest;
      if (completion?.evaluation) {
        emit({ type: "local.session_evaluation", evaluation: completion.evaluation });
      }
    } catch (error) {
      emit({
        type: "local.backend_warning",
        message: error instanceof Error ? error.message : "会话结束失败",
      });
      if (customSceneId && endingSessionId) {
        let recoveredEvaluation = null;
        for (const delay of [0, 400, 1_200]) {
          if (delay) {
            await new Promise((resolve) => window.setTimeout(resolve, delay));
          }
          try {
            recoveredEvaluation = await getCustomDialogueEvaluation(
              customSceneId,
              endingSessionId,
            );
            break;
          } catch {
            // The completion request may have persisted the report just after
            // its response was interrupted. Retry the idempotent query briefly.
          }
        }
        if (recoveredEvaluation) {
          completion = {
            sceneId: customSceneId,
            sessionId: endingSessionId,
            stopTime,
            evaluation: recoveredEvaluation,
            state: null,
          };
          emit({
            type: "local.session_evaluation",
            evaluation: recoveredEvaluation,
          });
        } else {
          completionError = error;
        }
      } else if (interviewSceneId && endingSessionId) {
        let recoveredReport = null;
        for (const delay of [0, 400, 1_200]) {
          if (delay) {
            await new Promise((resolve) => window.setTimeout(resolve, delay));
          }
          try {
            recoveredReport = await getInterviewReport(
              interviewSceneId,
              endingSessionId,
            );
            break;
          } catch {
            // The end request may have persisted the report just after its
            // response was interrupted. Retry the idempotent query briefly.
          }
        }
        if (recoveredReport) {
          completion = {
            sceneId: interviewSceneId,
            sessionId: endingSessionId,
            stopTime,
            reportStatus: recoveredReport.status || "PROCESSING",
            report: recoveredReport.report || null,
          };
          emit({
            type: "local.interview_state",
            state: null,
            reportStatus: completion.reportStatus,
          });
        } else {
          completionError = error;
        }
      } else {
        completionError = error;
      }
    }
    try {
      await segmentRecorder?.close?.();
    } finally {
      try { sessionSocket?.close?.(); } catch { /* already closed */ }
      if (scenarioCompletionTimer) window.clearTimeout(scenarioCompletionTimer);
      if (scenarioAudioDrainTimer) window.clearTimeout(scenarioAudioDrainTimer);
      peer = null;
      channel = null;
      sessionSocket = null;
      localStream = null;
      audioSender = null;
      sessionId = null;
      providerSessionId = null;
      providerSessionBound = false;
      sessionConfig = null;
      segmentRecorder = null;
      turnAudioCapture = null;
      learnerTurnNo = 0;
      pendingOperations = new Set();
      pendingEvaluationOperations = new Set();
      baseSessionInstructions = "";
      scenarioCompletionPending = false;
      scenarioCompletionEmitted = false;
      scenarioCompletionTimer = null;
      scenarioAudioDrainTimer = null;
      responsePending = false;
      closingResponseRequested = false;
      closingInstructions = "";
      pendingInterviewReportStatus = null;
      statePipeline = Promise.resolve();
      ieltsActivePart = null;
      ieltsPreparedQuestions = [];
      ieltsDialogueCompleted = false;
      ieltsInputRecoveryTimer = null;
      activeInputItemId = null;
      ieltsTimedOutTurn = null;
      pendingInstructionUpdate = null;
      instructionUpdateQueue = Promise.resolve(true);
      pendingAcks = [];
      persistedMessageIds = new Set();
      sessionUpdateAcknowledged = false;
      initialResponseStarted = false;
      paused = false;
      muted = false;
    }
    if (completionError && (customSceneId || ieltsSceneId || interviewSceneId)) throw completionError;
    if (emitEnded) emit({ type: "local.ended", reason, completion });
    return completion;
  }

  function stop(options = {}) {
    if (!stopPromise) {
      stopPromise = performStop(options).finally(() => {
        stopPromise = null;
      });
    }
    return stopPromise;
  }

  return {
    start,
    handleEvent: handleProviderEvent,
    pause,
    resume,
    interrupt,
    requestResponse,
    transitionIeltsPart2,
    forceIeltsPart3TurnTimeout,
    updateInstructions,
    waitForEvaluations,
    stop,
    setMuted,
    isActive: () => Boolean(peer || sessionId),
  };
}
