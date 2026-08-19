import type {
  RealtimeTransport,
  RealtimeTransportEvent,
} from './RealtimeSessionController';
import { summarizeRtcStats } from './RtcStatsTelemetry';
import { mobileTelemetry, type MobileTelemetryEvent } from '@/infrastructure/telemetry/MobileTelemetry';

export type MediaStreamTrackLike = {
  enabled: boolean;
  stop(): void;
};

export type MediaStreamLike = {
  getTracks(): MediaStreamTrackLike[];
  getAudioTracks(): MediaStreamTrackLike[];
};

export type RTCDataChannelLike = {
  readyState: 'connecting' | 'open' | 'closing' | 'closed';
  onopen: (() => void) | null;
  onerror: (() => void) | null;
  onmessage: ((event: { data: string }) => void) | null;
  send(data: string): void;
  close(): void;
};

export type SdpDescription = {
  type: 'offer' | 'answer';
  sdp: string;
};

export type PeerConnectionLike = {
  iceGatheringState: string;
  connectionState: string;
  iceConnectionState: string;
  localDescription: SdpDescription | null;
  onicegatheringstatechange: (() => void) | null;
  onconnectionstatechange: (() => void) | null;
  oniceconnectionstatechange: (() => void) | null;
  ondatachannel: ((event: { channel: RTCDataChannelLike }) => void) | null;
  addTrack(track: MediaStreamTrackLike, stream: MediaStreamLike): unknown;
  createDataChannel(label: string): RTCDataChannelLike;
  createOffer(): Promise<SdpDescription>;
  setLocalDescription(description: SdpDescription): Promise<void>;
  setRemoteDescription(description: SdpDescription): Promise<void>;
  getStats?(): Promise<unknown>;
  close(): void;
};

export type ReactNativeWebRTCAdapter = {
  getUserMedia(constraints: {
    audio: {
      echoCancellation: boolean;
      noiseSuppression: boolean;
      autoGainControl: boolean;
    };
    video: false;
  }): Promise<MediaStreamLike>;
  createPeerConnection(): PeerConnectionLike;
  createSessionDescription(description: SdpDescription): SdpDescription;
};

type TransportOptions = ReactNativeWebRTCAdapter & {
  iceGatheringTimeoutMs?: number;
  dataChannelTimeoutMs?: number;
  rtcStatsIntervalMs?: number;
  telemetry?: { record(event: MobileTelemetryEvent): void };
};

function createDefaultAdapter(): ReactNativeWebRTCAdapter {
  // A literal require keeps Jest unit tests independent from the native module while
  // Metro can still statically include it in the Android/iOS development build.
  // eslint-disable-next-line @typescript-eslint/no-require-imports
  const native = require('react-native-webrtc') as typeof import('react-native-webrtc');
  return {
    getUserMedia: (constraints) =>
      native.mediaDevices.getUserMedia(
        constraints as unknown as Parameters<typeof native.mediaDevices.getUserMedia>[0],
      ) as unknown as Promise<MediaStreamLike>,
    createPeerConnection: () =>
      new native.RTCPeerConnection() as unknown as PeerConnectionLike,
    createSessionDescription: (description) =>
      new native.RTCSessionDescription(
        description as ConstructorParameters<typeof native.RTCSessionDescription>[0],
      ) as unknown as SdpDescription,
  };
}

function normalizeSdp(sdp: string) {
  const normalized = String(sdp || '').trim().replace(/\r?\n/g, '\r\n');
  return normalized.endsWith('\r\n') ? normalized : `${normalized}\r\n`;
}

type Listener = (event: RealtimeTransportEvent) => void;

export class ReactNativeWebRTCTransport implements RealtimeTransport {
  private readonly adapter: ReactNativeWebRTCAdapter;
  private readonly iceGatheringTimeoutMs: number;
  private readonly dataChannelTimeoutMs: number;
  private readonly rtcStatsIntervalMs: number;
  private readonly telemetry: { record(event: MobileTelemetryEvent): void };
  private readonly listeners = new Set<Listener>();
  private readonly channels = new Set<RTCDataChannelLike>();
  private peer: PeerConnectionLike | null = null;
  private stream: MediaStreamLike | null = null;
  private channel: RTCDataChannelLike | null = null;
  private telemetrySessionId: string | null = null;
  private rtcStatsTimer: ReturnType<typeof setInterval> | null = null;
  private rtcStartedAt = 0;
  private rtcConnected = false;
  private rtcIceConnected = false;
  private previousRtcTotals: { packetsReceived?: number; packetsLost?: number } = {};

  constructor(options?: Partial<TransportOptions>) {
    const defaults = options ? null : createDefaultAdapter();
    if (
      !options?.getUserMedia ||
      !options.createPeerConnection ||
      !options.createSessionDescription
    ) {
      if (!defaults) {
        throw new Error('React Native WebRTC adapter 配置不完整');
      }
    }
    this.adapter = (options ?? defaults) as ReactNativeWebRTCAdapter;
    this.iceGatheringTimeoutMs = options?.iceGatheringTimeoutMs ?? 10_000;
    this.dataChannelTimeoutMs = options?.dataChannelTimeoutMs ?? 10_000;
    this.rtcStatsIntervalMs = options?.rtcStatsIntervalMs ?? 10_000;
    this.telemetry = options?.telemetry ?? mobileTelemetry;
  }

  subscribe(listener: Listener) {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  async prepare() {
    if (this.peer || this.stream) this.close();
    const stream = await this.adapter.getUserMedia({
      audio: {
        echoCancellation: true,
        noiseSuppression: true,
        autoGainControl: true,
      },
      video: false,
    });
    const peer = this.adapter.createPeerConnection();
    this.stream = stream;
    this.peer = peer;
    this.rtcStartedAt = Date.now();
    this.rtcConnected = false;
    this.rtcIceConnected = false;
    this.previousRtcTotals = {};
	this.telemetry.record({ eventType: 'rtc.started' });

    for (const track of stream.getAudioTracks()) {
      track.enabled = false;
      peer.addTrack(track, stream);
    }

    peer.onconnectionstatechange = () => {
	  this.recordRtcState('connection', peer.connectionState);
      if (peer.connectionState === 'failed') {
        this.emit({
          type: 'connection.failed',
          message: 'WebRTC 连接失败',
        });
      }
    };
    peer.oniceconnectionstatechange = () => {
	  this.recordRtcState('ice', peer.iceConnectionState);
      if (peer.iceConnectionState === 'failed') {
        this.emit({ type: 'ice.failed', message: 'ICE 连接失败' });
      }
    };
    peer.ondatachannel = ({ channel }) => this.bindChannel(channel);
    this.bindChannel(peer.createDataChannel('oai-events'));
	if (peer.getStats) {
	  this.rtcStatsTimer = setInterval(() => {
		void this.collectRtcStats(peer);
	  }, this.rtcStatsIntervalMs);
	}
  }

  async createOffer() {
    const peer = this.requirePeer();
    const offer = await peer.createOffer();
    await peer.setLocalDescription(offer);
    await this.waitForIceGathering(peer);
    const sdp = peer.localDescription?.sdp;
    if (!sdp?.trim()) throw new Error('本地 Offer SDP 为空');
    return sdp;
  }

  async applyAnswer(answerSdp: string) {
    const peer = this.requirePeer();
    const answer = this.adapter.createSessionDescription({
      type: 'answer',
      sdp: normalizeSdp(answerSdp),
    });
    await peer.setRemoteDescription(answer);
  }

  bindSession(sessionId: string) {
	this.telemetrySessionId = sessionId;
  }

  waitForDataChannel() {
    const channel = this.channel;
    if (!channel) return Promise.reject(new Error('实时数据通道尚未创建'));
    if (channel.readyState === 'open') return Promise.resolve();
    return new Promise<void>((resolve, reject) => {
      const previousOpen = channel.onopen;
      const previousError = channel.onerror;
      const timer = setTimeout(() => {
        reject(new Error('实时数据通道连接超时'));
      }, this.dataChannelTimeoutMs);
      channel.onopen = () => {
        previousOpen?.();
        clearTimeout(timer);
		this.markRtcConnected();
        resolve();
      };
      channel.onerror = () => {
        previousError?.();
        clearTimeout(timer);
        reject(new Error('实时数据通道连接失败'));
      };
    });
  }

  sendProviderEvent(event: Record<string, unknown>) {
    const channel = this.channel;
    if (!channel || channel.readyState !== 'open') {
      throw new Error('实时数据通道尚未连接');
    }
    channel.send(JSON.stringify(event));
  }

  setAudioEnabled(enabled: boolean) {
    for (const track of this.stream?.getAudioTracks() ?? []) {
      track.enabled = enabled;
    }
  }

  close() {
    const stream = this.stream;
    const peer = this.peer;
    this.stream = null;
    this.peer = null;
    this.channel = null;
	if (this.rtcStatsTimer) clearInterval(this.rtcStatsTimer);
	this.rtcStatsTimer = null;
	if (peer) void this.collectRtcStats(peer, 'rtc.final_quality');
	if (this.rtcStartedAt) {
	  this.telemetry.record({
		eventType: 'rtc.ended',
		sessionId: this.telemetrySessionId,
		attributes: { duration_ms: Date.now() - this.rtcStartedAt },
	  });
	}
	this.telemetrySessionId = null;
	this.rtcStartedAt = 0;

    for (const track of stream?.getTracks() ?? []) track.stop();
    for (const channel of this.channels) channel.close();
    this.channels.clear();
    peer?.close();
  }

  private bindChannel(channel: RTCDataChannelLike) {
    this.channel = channel;
    this.channels.add(channel);
    channel.onmessage = ({ data }) => {
      this.emit({ type: 'provider.message', data });
    };
    channel.onerror = () => {
      this.emit({
        type: 'datachannel.failed',
        message: '实时数据通道连接失败',
      });
    };
  }

  private markRtcConnected() {
	if (this.rtcConnected) return;
	this.rtcConnected = true;
	this.telemetry.record({
	  eventType: 'rtc.connected',
	  sessionId: this.telemetrySessionId,
	  attributes: { connect_duration_ms: Math.max(0, Date.now() - this.rtcStartedAt) },
	});
	if (this.peer) void this.collectRtcStats(this.peer);
  }

  private recordRtcState(kind: 'connection' | 'ice', state: string) {
	const failed = state === 'failed';
	this.telemetry.record({
	  eventType: `rtc.${kind}_state`,
	  severity: failed ? 'ERROR' : state === 'disconnected' ? 'WARN' : 'INFO',
	  sessionId: this.telemetrySessionId,
	  message: failed ? `${kind} failed` : null,
	  attributes: { state },
	});
	if (kind === 'ice' && !this.rtcIceConnected && ['connected', 'completed'].includes(state)) {
	  this.rtcIceConnected = true;
	  this.telemetry.record({
		eventType: 'rtc.ice_connected',
		sessionId: this.telemetrySessionId,
		attributes: { connect_duration_ms: Math.max(0, Date.now() - this.rtcStartedAt) },
	  });
	}
	if (failed && this.peer) void this.collectRtcStats(this.peer, 'rtc.failure', 'ERROR');
  }

  private async collectRtcStats(
	peer: PeerConnectionLike,
	eventType = 'rtc.quality',
	severity: MobileTelemetryEvent['severity'] = 'INFO',
  ) {
	if (!peer.getStats) return;
	try {
	  const summary = summarizeRtcStats(await peer.getStats(), this.previousRtcTotals);
	  this.previousRtcTotals = summary.totals;
	  this.telemetry.record({
		eventType,
		severity,
		sessionId: this.telemetrySessionId,
		attributes: {
		  ...summary.attributes,
		  ice_state: peer.iceConnectionState,
		  connection_state: peer.connectionState,
		},
	  });
	} catch (error) {
	  this.telemetry.record({
		eventType: 'rtc.stats_failed',
		severity: 'WARN',
		sessionId: this.telemetrySessionId,
		message: error instanceof Error ? error.message : 'RTC getStats failed',
	  });
	}
  }

  private waitForIceGathering(peer: PeerConnectionLike) {
    if (peer.iceGatheringState === 'complete') return Promise.resolve();
    return new Promise<void>((resolve, reject) => {
      const previous = peer.onicegatheringstatechange;
      const timer = setTimeout(() => {
        reject(new Error('ICE 候选收集超时'));
      }, this.iceGatheringTimeoutMs);
      peer.onicegatheringstatechange = () => {
        previous?.();
        if (peer.iceGatheringState === 'complete') {
          clearTimeout(timer);
          resolve();
        }
      };
    });
  }

  private requirePeer() {
    if (!this.peer) throw new Error('WebRTC 尚未准备');
    return this.peer;
  }

  private emit(event: RealtimeTransportEvent) {
    this.listeners.forEach((listener) => listener(event));
  }
}
