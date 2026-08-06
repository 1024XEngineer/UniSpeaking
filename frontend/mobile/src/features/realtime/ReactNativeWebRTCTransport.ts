import type {
  RealtimeTransport,
  RealtimeTransportEvent,
} from './RealtimeSessionController';

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
  private readonly listeners = new Set<Listener>();
  private readonly channels = new Set<RTCDataChannelLike>();
  private peer: PeerConnectionLike | null = null;
  private stream: MediaStreamLike | null = null;
  private channel: RTCDataChannelLike | null = null;

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

    for (const track of stream.getAudioTracks()) {
      track.enabled = false;
      peer.addTrack(track, stream);
    }

    peer.onconnectionstatechange = () => {
      if (peer.connectionState === 'failed') {
        this.emit({
          type: 'connection.failed',
          message: 'WebRTC 连接失败',
        });
      }
    };
    peer.oniceconnectionstatechange = () => {
      if (peer.iceConnectionState === 'failed') {
        this.emit({ type: 'ice.failed', message: 'ICE 连接失败' });
      }
    };
    peer.ondatachannel = ({ channel }) => this.bindChannel(channel);
    this.bindChannel(peer.createDataChannel('oai-events'));
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
