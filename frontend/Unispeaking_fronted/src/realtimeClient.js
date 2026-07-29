import { getAccessToken } from "./apiClient.js";

const DEFAULT_API_BASE = "";
const DEFAULT_VOICE = "Katerina";
const DEFAULT_MODEL = "qwen3.5-omni-flash-realtime";
const DATA_CHANNEL_LABEL = "oai-events";
const DEFAULT_SPEECH_SPEED = "NATURAL";
const SPEECH_SPEED_INSTRUCTIONS = {
  SLOWER: "Voice delivery rule: speak distinctly and very slowly, around 70 English words per minute, with clear pauses between short phrases.",
  MODERATE: "Voice delivery rule: speak at a calm moderate pace, around 120 English words per minute, with clear pauses between ideas.",
  NATURAL: "Voice delivery rule: speak at a natural conversational pace, around 165 English words per minute.",
  FASTER: "Voice delivery rule: speak quickly but clearly, around 210 English words per minute, without dropping or slurring words.",
};

const eventId = (prefix) => `${prefix}_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;

function normalizeBaseUrl(baseUrl) {
  if (!baseUrl) return "";
  const url = new URL(baseUrl);
  if (url.protocol !== "http:" && url.protocol !== "https:") {
    throw new Error("后端地址必须使用 HTTP 或 HTTPS");
  }
  return url.toString().replace(/\/$/, "");
}

function websocketUrl(baseUrl) {
  const origin = baseUrl || window.location.origin;
  const url = new URL(origin);
  url.protocol = url.protocol === "https:" ? "wss:" : "ws:";
  url.pathname = "/ws/session-messages";
  url.search = "";
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
      silence_duration_ms: 800,
      create_response: true,
      interrupt_response: true,
    },
  };
}

export function createRealtimeClient({
  apiBase = import.meta.env?.VITE_UNISPEAKING_API_BASE || DEFAULT_API_BASE,
  onEvent = () => {},
  onRemoteStream = () => {},
} = {}) {
  const base = normalizeBaseUrl(apiBase);
  const sessionMessagesUrl = websocketUrl(base);
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
    sessionSocket = new WebSocket(sessionMessagesUrl);
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

  function addSessionMessage(owner, content, providerMessageId) {
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
    void sendSessionFrame("message", {
      owner,
      content: text,
      audio: null,
    }).catch((error) => {
      if (messageKey) persistedMessageIds.delete(messageKey);
      emit({ type: "local.backend_warning", message: error.message });
    });
    return true;
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

  async function postStart({ offerSdp, voice, model }) {
    const accessToken = getAccessToken();
    const response = await fetch(`${base}/api/scene-sessions`, {
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
      inputReady = true;
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
      inputReady = true;
      setTrackEnabled();
      requestInitialResponse();
      return;
    }

    if (event.type === "input_audio_buffer.speech_started") {
      return;
    }

    if (event.type === "response.created") {
      initialResponseStarted = true;
      inputReady = true;
      if (initialResponseFallbackTimer) {
        window.clearTimeout(initialResponseFallbackTimer);
        initialResponseFallbackTimer = null;
      }
      setTrackEnabled();
      return;
    }

    if (event.type === "conversation.item.input_audio_transcription.completed") {
      addSessionMessage(
        1,
        event.transcript || event.text,
        event.item_id || event.item?.id || event.event_id,
      );
      return;
    }

    const completedAssistantMessage = extractCompletedAssistantMessage(event);
    if (completedAssistantMessage) {
      addSessionMessage(
        0,
        completedAssistantMessage.text,
        completedAssistantMessage.id,
      );
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
      });

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

    const endRequest = notifyBackend && sessionId
      ? sendSessionFrame("end", null, new Date().toISOString()).catch((error) => {
        emit({ type: "local.backend_warning", message: error.message });
      })
      : Promise.resolve();

    try { channel?.close?.(); } catch { /* already closed */ }
    try { peer?.close?.(); } catch { /* already closed */ }
    await endRequest;
    try { sessionSocket?.close?.(); } catch { /* already closed */ }
    peer = null;
    channel = null;
    sessionSocket = null;
    localStream = null;
    audioSender = null;
    sessionId = null;
    sessionConfig = null;
    pendingAcks = [];
    persistedMessageIds = new Set();
    sessionUpdateAcknowledged = false;
    initialResponseStarted = false;
    paused = false;
    muted = false;
    if (emitEnded) emit({ type: "local.ended", reason });
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
