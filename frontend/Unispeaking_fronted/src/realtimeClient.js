const DEFAULT_API_BASE = "";
const DEFAULT_USER_ID = "local-demo-user";
const DEFAULT_VOICE = "Katerina";
const DEFAULT_MODEL = "qwen3.5-omni-flash-realtime";
const DATA_CHANNEL_LABEL = "oai-events";

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

export function createRealtimeClient({
  apiBase = import.meta.env.VITE_UNISPEAKING_API_BASE || DEFAULT_API_BASE,
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
  let localSessionId = null;
  let sessionConfig = null;
  let pendingAcks = [];
  let persistedMessageIds = new Set();
  let muted = false;
  let paused = false;
  let started = false;

  const emit = (event) => onEvent(event);

  function setTrackEnabled() {
    const track = localStream?.getAudioTracks?.()[0];
    if (track) track.enabled = started && !muted && !paused;
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

  async function sendSessionFrame(type, message = null) {
    if (!localSessionId) throw new Error("本地会话 ID 尚未建立");
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
      sessionId: localSessionId,
      message,
    }));
    return ack;
  }

  function addSessionMessage(owner, content, providerMessageId) {
    const text = String(content || "").trim();
    if (!text) return;
    const messageKey = providerMessageId ? `${owner}:${providerMessageId}` : null;
    if (messageKey && persistedMessageIds.has(messageKey)) return;
    if (messageKey) persistedMessageIds.add(messageKey);
    void sendSessionFrame("message", {
      owner,
      content: text,
      audio: null,
    }).catch((error) => {
      if (messageKey) persistedMessageIds.delete(messageKey);
      emit({ type: "local.backend_warning", message: error.message });
    });
  }

  function sendProviderEvent(event) {
    if (!channel || channel.readyState !== "open") {
      throw new Error("实时数据通道尚未连接");
    }
    channel.send(JSON.stringify(event));
  }

  async function postStart({ offerSdp, topic, userId }) {
    const response = await fetch(`${base}/api/scene-sessions`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        userId: userId || DEFAULT_USER_ID,
        sceneType: "FREE_CHAT",
        prompt: topic || "",
        userPreference: "",
        offerSdp,
        topic: topic || undefined,
        provider: "QWEN",
        model: DEFAULT_MODEL,
        voice: DEFAULT_VOICE,
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

    emit(event);

    if (event.type === "session.created") {
      started = true;
      const audioTrack = localStream?.getAudioTracks?.()[0];
      if (audioTrack && audioSender?.track !== audioTrack) {
        await audioSender?.replaceTrack(audioTrack);
      }
      setTrackEnabled();
      sendProviderEvent({ event_id: eventId("config"), type: "session.update", session: sessionConfig });
      return;
    }

    if (event.type === "session.updated") {
      sendProviderEvent({ event_id: eventId("response"), type: "response.create" });
      return;
    }

    if (event.type === "input_audio_buffer.speech_started") {
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

    if (event.type === "response.audio_transcript.done") {
      addSessionMessage(
        0,
        event.transcript || event.text,
        event.item_id || event.response_id || event.response?.id || event.event_id,
      );
    }
  }

  async function start({ topic = "", userId = DEFAULT_USER_ID } = {}) {
    if (peer) return { localSessionId };
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

      const backend = await postStart({ offerSdp: peer.localDescription?.sdp || offer.sdp || "", topic, userId });
      localSessionId = backend.localSessionId;
      sessionConfig = {
        modalities: ["text", "audio"],
        voice: backend.voiceId || DEFAULT_VOICE,
        instructions: backend.systemPrompt || topic || "",
        input_audio_transcription: { model: "gummy-realtime-v1" },
        turn_detection: {
          type: "server_vad",
          interrupt_response: true,
        },
      };

      await connectSessionSocket();
      await peer.setRemoteDescription({ type: "answer", sdp: normalizeSdp(backend.answerSdp) });
      await waitForChannel(channel);
      emit({ type: "local.connected", localSessionId, backend });
      return { localSessionId, backend };
    } catch (error) {
      await stop({ notifyBackend: false, reason: "start_failed" });
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

  async function stop({ notifyBackend = true, reason = "user_stop" } = {}) {
    if (notifyBackend && localSessionId) {
      await sendSessionFrame("end").catch((error) => {
        emit({ type: "local.backend_warning", message: error.message });
      });
    }
    try { channel?.close?.(); } catch { /* already closed */ }
    try { sessionSocket?.close?.(); } catch { /* already closed */ }
    try { peer?.close?.(); } catch { /* already closed */ }
    localStream?.getTracks?.().forEach((track) => track.stop());
    peer = null;
    channel = null;
    sessionSocket = null;
    localStream = null;
    audioSender = null;
    localSessionId = null;
    sessionConfig = null;
    pendingAcks = [];
    persistedMessageIds = new Set();
    started = false;
    paused = false;
    muted = false;
    emit({ type: "local.ended", reason });
  }

  return {
    start,
    handleEvent: handleProviderEvent,
    pause,
    resume,
    interrupt,
    stop,
    setMuted,
    isActive: () => Boolean(peer || localSessionId),
  };
}
