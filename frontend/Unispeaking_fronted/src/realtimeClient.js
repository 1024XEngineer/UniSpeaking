import { getAccessToken } from "./apiClient.js";
import {
  advanceCustomDialogueState,
  completeCustomDialogue,
  evaluateCustomDialogueTurn,
  getCustomDialogueEvaluation,
} from "./apiClient.js";
import { createPcmWavSegmentRecorder } from "./audioRecorder.js";

const DEFAULT_API_BASE = "";
const DEFAULT_VOICE = "Katerina";
const DEFAULT_MODEL = "qwen3.5-omni-flash-realtime";
const DATA_CHANNEL_LABEL = "oai-events";
const DEFAULT_SPEECH_SPEED = "NATURAL";
const SCENARIO_CLOSING_TIMEOUT_MS = 20_000;
const SCENARIO_AUDIO_DRAIN_MS = 1_200;
const SPEECH_SPEED_INSTRUCTIONS = {
  SLOWER: "Voice delivery rule: speak distinctly and very slowly, around 70 English words per minute, with clear pauses between short phrases.",
  MODERATE: "Voice delivery rule: speak at a calm moderate pace, around 120 English words per minute, with clear pauses between ideas.",
  NATURAL: "Voice delivery rule: speak at a natural conversational pace, around 165 English words per minute.",
  FASTER: "Voice delivery rule: speak quickly but clearly, around 210 English words per minute, without dropping or slurring words.",
};

const eventId = (prefix) => `${prefix}_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;

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

function waitForChannel(channel) {
  if (channel.readyState === "open") return Promise.resolve();
  return new Promise((resolve, reject) => {
    const timer = window.setTimeout(() => reject(new Error("实时数据通道连接超时")), 10_000);
    channel.onopen = () => {
      window.clearTimeout(timer);
      resolve();
    };
    channel.onerror = () => {
      window.clearTimeout(timer);
      reject(new Error("实时数据通道连接失败"));
    };
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
      type: String(model || DEFAULT_MODEL).startsWith("qwen3.5-omni-") ? "semantic_vad" : "server_vad",
      threshold: 0.5,
      prefix_padding_ms: 500,
      silence_duration_ms: 600,
      create_response: Boolean(automaticTurnResponses),
      interrupt_response: true,
    },
  };
}

export function createRealtimeClient({
  apiBase = import.meta.env?.VITE_BACKEND_URL || DEFAULT_API_BASE,
  sceneId: customSceneId = null,
  onEvent = () => {},
  onRemoteStream = () => {},
} = {}) {
  const base = normalizeBaseUrl(apiBase);
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
  let segmentActive = false;
  let currentTurnAudio = Promise.resolve(null);
  let learnerTurnNo = 0;
  let pendingOperations = new Set();
  let baseSessionInstructions = "";
  let scenarioCompletionPending = false;
  let scenarioCompletionEmitted = false;
  let scenarioCompletionTimer = null;
  let scenarioAudioDrainTimer = null;
  let responsePending = false;
  let closingResponseRequested = false;
  let statePipeline = Promise.resolve();

  const emit = (event) => onEvent(event);

  function setTrackEnabled() {
    const track = localStream?.getAudioTracks?.()[0];
    if (track) track.enabled = started && inputReady && !muted && !paused;
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

  async function sendSessionFrame(type, message = null, stopTime = null) {
    if (!sessionId) throw new Error("会话 ID 尚未建立");
    const socket = await connectSessionSocket();
    const operation = type === "end" ? "end" : "message";
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
    }));
    return ack;
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

  function requestInitialResponse() {
    if (initialResponseStarted) return;
    sendProviderEvent({
      event_id: eventId("greeting"),
      type: "response.create",
    });
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
    emit({ type: "local.scenario_completed" });
    void stop({ reason: "state_machine" }).catch((error) => {
      emit({
        type: "local.scenario_completion_error",
        message: error instanceof Error ? error.message : "场景自动结束失败",
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

  function requestTurnResponse({ closing = false } = {}) {
    if (responsePending) return false;
    responsePending = true;
    if (closing) {
      closingResponseRequested = true;
      armScenarioCompletionTimeout();
    }
    try {
      sendProviderEvent({
        event_id: eventId(closing ? "closing_response" : "turn_response"),
        type: "response.create",
      });
    } catch (error) {
      responsePending = false;
      if (closing) closingResponseRequested = false;
      throw error;
    }
    return true;
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
    if (segmentActive) {
      currentTurnAudio = segmentRecorder?.stopSegment() || Promise.resolve(null);
      segmentActive = false;
    }
    if (!responsePending) {
      requestTurnResponse({ closing: true });
    }
  }

  async function postStart({ offerSdp, voice, model }) {
    const accessToken = getAccessToken();
    const path = customSceneId
      ? `/api/custom-scenes/${encodeURIComponent(customSceneId)}/sessions`
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
        voice: voice || DEFAULT_VOICE,
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
      inputReady = !customSceneId && !scenarioCompletionPending;
      if (initialResponseFallbackTimer) {
        window.clearTimeout(initialResponseFallbackTimer);
        initialResponseFallbackTimer = null;
      }
      setTrackEnabled();
      emit({ type: "local.provider_warning", message: event.error?.message || event.message });
      return;
    }

    emit(event);

    if (event.type === "session.created") {
      started = true;
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
      if (sessionUpdateAcknowledged) return;
      sessionUpdateAcknowledged = true;
      if (sessionUpdateRetryTimer) {
        window.clearTimeout(sessionUpdateRetryTimer);
        sessionUpdateRetryTimer = null;
      }
      inputReady = !customSceneId;
      setTrackEnabled();
      requestInitialResponse();
      return;
    }

    if (event.type === "input_audio_buffer.speech_started") {
      if (scenarioCompletionPending) return;
      segmentRecorder?.startSegment();
      segmentActive = Boolean(segmentRecorder);
      return;
    }

    if (event.type === "input_audio_buffer.speech_stopped") {
      if (segmentActive) {
        currentTurnAudio = segmentRecorder?.stopSegment() || Promise.resolve(null);
        segmentActive = false;
      }
      return;
    }

    if (event.type === "response.created") {
      initialResponseStarted = true;
      responsePending = true;
      inputReady = !customSceneId;
      if (initialResponseFallbackTimer) {
        window.clearTimeout(initialResponseFallbackTimer);
        initialResponseFallbackTimer = null;
      }
      setTrackEnabled();
      return;
    }

    if (event.type === "conversation.item.input_audio_transcription.completed") {
      if (scenarioCompletionPending) return;
      const transcript = String(event.transcript || event.text || "").trim();
      if (!transcript) return;
      if (customSceneId) {
        inputReady = false;
        setTrackEnabled();
      }
      if (segmentActive) {
        currentTurnAudio = segmentRecorder?.stopSegment() || Promise.resolve(null);
        segmentActive = false;
      }
      const persistenceOperation = addSessionMessage(
        1,
        transcript,
        event.item_id || event.item?.id || event.event_id,
      );
      if (customSceneId) {
        requestTurnResponse();
      }
      const persisted = await persistenceOperation;
      if (customSceneId && persisted) {
        const turnNo = ++learnerTurnNo;
        const wavAudio = await currentTurnAudio;
        currentTurnAudio = Promise.resolve(null);
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
      return;
    }

    const completedAssistantMessage = extractCompletedAssistantMessage(event);
    if (completedAssistantMessage) {
      await addSessionMessage(
        0,
        completedAssistantMessage.text,
        completedAssistantMessage.id,
      );
    }
    if (event.type === "response.done") {
      responsePending = false;
      if (scenarioCompletionPending) {
        if (closingResponseRequested) {
          if (scenarioCompletionTimer) {
            window.clearTimeout(scenarioCompletionTimer);
            scenarioCompletionTimer = null;
          }
          scheduleScenarioCompletionAfterAudioDrain();
        } else {
          requestTurnResponse({ closing: true });
        }
      } else if (customSceneId) {
        inputReady = true;
        setTrackEnabled();
      }
    }
  }

  async function start({
    voice = DEFAULT_VOICE,
    model = DEFAULT_MODEL,
    speechSpeed = DEFAULT_SPEECH_SPEED,
  } = {}) {
    if (peer) return { sessionId };
    emit({ type: "local.connecting" });

    try {
      peer = new RTCPeerConnection();
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
      if (customSceneId) {
        segmentRecorder = await createPcmWavSegmentRecorder(localStream);
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
      const finalSystemPrompt = String(backend.systemPrompt || "").trim();
      if (!finalSystemPrompt) {
        throw new Error("后端没有返回由 SceneService 生成的五层提示词");
      }
      sessionConfig = buildRealtimeSessionConfig({
        systemPrompt: finalSystemPrompt,
        voice: backend.voiceId || DEFAULT_VOICE,
        model,
        speechSpeed,
        automaticTurnResponses: !customSceneId,
      });
      baseSessionInstructions = sessionConfig.instructions;

      await connectSessionSocket();
      await peer.setRemoteDescription({ type: "answer", sdp: normalizeSdp(backend.answerSdp) });
      await waitForChannel(channel);
      emit({ type: "local.connected", sessionId, backend });
      return { sessionId, backend };
    } catch (error) {
      await stop({ notifyBackend: false, reason: "start_failed", emitEnded: false });
      emit({ type: "local.error", message: error instanceof Error ? error.message : "无法开始实时对话" });
      throw error;
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

  async function waitForPendingOperations(maxWaitMs) {
    const operations = [...pendingOperations];
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
  } = {}) {
    clearInitializationTimers();
    started = false;
    inputReady = false;
    setTrackEnabled();
    localStream?.getTracks?.().forEach((track) => track.stop());

    await waitForPendingOperations(reason === "user_stop" ? 3_000 : 15_000);
    const stopTime = new Date().toISOString();
    const endingSessionId = sessionId;
    const endRequest = notifyBackend && sessionId
      ? customSceneId
        ? completeCustomDialogue(customSceneId, sessionId, stopTime)
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
      sessionConfig = null;
      segmentRecorder = null;
      segmentActive = false;
      currentTurnAudio = Promise.resolve(null);
      learnerTurnNo = 0;
      pendingOperations = new Set();
      baseSessionInstructions = "";
      scenarioCompletionPending = false;
      scenarioCompletionEmitted = false;
      scenarioCompletionTimer = null;
      scenarioAudioDrainTimer = null;
      responsePending = false;
      closingResponseRequested = false;
      statePipeline = Promise.resolve();
      pendingAcks = [];
      persistedMessageIds = new Set();
      sessionUpdateAcknowledged = false;
      initialResponseStarted = false;
      paused = false;
      muted = false;
    }
    if (completionError && customSceneId) throw completionError;
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
    stop,
    setMuted,
    isActive: () => Boolean(peer || sessionId),
  };
}
