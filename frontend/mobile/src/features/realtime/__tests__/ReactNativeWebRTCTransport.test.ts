import {
  ReactNativeWebRTCTransport,
  type MediaStreamLike,
  type MediaStreamTrackLike,
  type PeerConnectionLike,
  type RTCDataChannelLike,
} from '../ReactNativeWebRTCTransport';

let mockNativeFixture: ReturnType<typeof createFixture>;
jest.mock('react-native-webrtc', () => ({
  mediaDevices: { getUserMedia: jest.fn(() => Promise.resolve(mockNativeFixture.stream)) },
  RTCPeerConnection: function MockPeerConnection() { return mockNativeFixture.peer; },
  RTCSessionDescription: function MockSessionDescription(description: unknown) { return description; },
}), { virtual: true });

class FakeDataChannel implements RTCDataChannelLike {
  readyState: 'connecting' | 'open' | 'closing' | 'closed' = 'connecting';
  onopen: (() => void) | null = null;
  onerror: (() => void) | null = null;
  onmessage: ((event: { data: string }) => void) | null = null;
  send = jest.fn();
  close = jest.fn(() => {
    this.readyState = 'closed';
  });

  open() {
    this.readyState = 'open';
    this.onopen?.();
  }
}

function createFixture() {
  const track: MediaStreamTrackLike = {
    enabled: true,
    stop: jest.fn(),
  };
  const stream: MediaStreamLike = {
    getTracks: () => [track],
    getAudioTracks: () => [track],
  };
  const channel = new FakeDataChannel();
  const peer: PeerConnectionLike = {
    iceGatheringState: 'complete',
    connectionState: 'new',
    iceConnectionState: 'new',
    localDescription: null,
    onicegatheringstatechange: null,
    onconnectionstatechange: null,
    oniceconnectionstatechange: null,
    ondatachannel: null,
    addTrack: jest.fn(),
    createDataChannel: jest.fn(() => channel),
    createOffer: jest.fn(async () => ({ type: 'offer' as const, sdp: 'offer\nsdp' })),
    setLocalDescription: jest.fn(async (description) => {
      peer.localDescription = description;
    }),
    setRemoteDescription: jest.fn(async () => undefined),
    close: jest.fn(),
  };
  const adapter = {
    getUserMedia: jest.fn(async () => stream),
    createPeerConnection: jest.fn(() => peer),
    createSessionDescription: jest.fn((description) => description),
  };
  return { track, stream, channel, peer, adapter };
}

describe('ReactNativeWebRTCTransport', () => {
  afterEach(() => {
    jest.useRealTimers();
  });

  it('prepares a disabled microphone track with mobile speech constraints', async () => {
    const fixture = createFixture();
    const transport = new ReactNativeWebRTCTransport(fixture.adapter);

    await transport.prepare();

    expect(fixture.adapter.getUserMedia).toHaveBeenCalledWith({
      audio: {
        echoCancellation: true,
        noiseSuppression: true,
        autoGainControl: true,
      },
      video: false,
    });
    expect(fixture.peer.addTrack).toHaveBeenCalledWith(
      fixture.track,
      fixture.stream,
    );
    expect(fixture.track.enabled).toBe(false);
    expect(fixture.peer.createDataChannel).toHaveBeenCalledWith('oai-events');
  });

  it('returns the gathered local SDP and applies a normalized answer', async () => {
    const fixture = createFixture();
    const transport = new ReactNativeWebRTCTransport(fixture.adapter);
    await transport.prepare();

    await expect(transport.createOffer()).resolves.toBe('offer\nsdp');
    await transport.applyAnswer('answer\nsdp');

    expect(fixture.peer.setRemoteDescription).toHaveBeenCalledWith({
      type: 'answer',
      sdp: 'answer\r\nsdp\r\n',
    });
  });

  it('waits for the data channel, sends provider JSON and emits provider messages', async () => {
    const fixture = createFixture();
    const transport = new ReactNativeWebRTCTransport(fixture.adapter);
    const events: unknown[] = [];
    transport.subscribe((event) => events.push(event));
    await transport.prepare();

    const waiting = transport.waitForDataChannel();
    fixture.channel.open();
    await waiting;
    transport.sendProviderEvent({ type: 'session.update' });
    fixture.channel.onmessage?.({ data: '{"type":"session.updated"}' });

    expect(fixture.channel.send).toHaveBeenCalledWith(
      '{"type":"session.update"}',
    );
    expect(events).toContainEqual({
      type: 'provider.message',
      data: '{"type":"session.updated"}',
    });
  });

  it('accepts a provider-created channel and reports connection failures', async () => {
    const fixture = createFixture();
    const incomingChannel = new FakeDataChannel();
    const transport = new ReactNativeWebRTCTransport(fixture.adapter);
    const events: unknown[] = [];
    transport.subscribe((event) => events.push(event));
    await transport.prepare();

    fixture.peer.ondatachannel?.({ channel: incomingChannel });
    incomingChannel.open();
    fixture.peer.connectionState = 'failed';
    fixture.peer.onconnectionstatechange?.();

    await expect(transport.waitForDataChannel()).resolves.toBeUndefined();
    expect(events).toContainEqual({
      type: 'connection.failed',
      message: 'WebRTC 连接失败',
    });
  });

  it('toggles and then releases every native resource exactly once', async () => {
    const fixture = createFixture();
    const transport = new ReactNativeWebRTCTransport(fixture.adapter);
    await transport.prepare();

    transport.setAudioEnabled(true);
    expect(fixture.track.enabled).toBe(true);
    transport.close();
    transport.close();

    expect(fixture.track.stop).toHaveBeenCalledTimes(1);
    expect(fixture.channel.close).toHaveBeenCalledTimes(1);
    expect(fixture.peer.close).toHaveBeenCalledTimes(1);
  });

  it('rejects incomplete adapters and operations attempted before prepare', async () => {
    expect(
      () =>
        new ReactNativeWebRTCTransport({
          getUserMedia: jest.fn(),
        }),
    ).toThrow('React Native WebRTC adapter 配置不完整');

    const fixture = createFixture();
    const transport = new ReactNativeWebRTCTransport(fixture.adapter);
    await expect(transport.createOffer()).rejects.toThrow('WebRTC 尚未准备');
    await expect(transport.applyAnswer('answer')).rejects.toThrow('WebRTC 尚未准备');
    await expect(transport.waitForDataChannel()).rejects.toThrow('实时数据通道尚未创建');
    expect(() => transport.sendProviderEvent({ type: 'test' })).toThrow(
      '实时数据通道尚未连接',
    );
  });

  it('waits for ICE gathering and preserves an existing state listener', async () => {
    const fixture = createFixture();
    fixture.peer.iceGatheringState = 'gathering';
    const previous = jest.fn();
    fixture.peer.onicegatheringstatechange = previous;
    const transport = new ReactNativeWebRTCTransport(fixture.adapter);
    await transport.prepare();

    const pending = transport.createOffer();
    await Promise.resolve();
    fixture.peer.iceGatheringState = 'complete';
    fixture.peer.onicegatheringstatechange?.();

    await expect(pending).resolves.toBe('offer\nsdp');
    expect(previous).toHaveBeenCalled();
  });

  it('reports ICE and data-channel timeouts and an empty local SDP', async () => {
    const fixture = createFixture();
    fixture.peer.iceGatheringState = 'gathering';
    const transport = new ReactNativeWebRTCTransport({
      ...fixture.adapter,
      iceGatheringTimeoutMs: 1,
      dataChannelTimeoutMs: 1,
    });
    await transport.prepare();

    const offer = transport.createOffer();
    await expect(offer).rejects.toThrow('ICE 候选收集超时');

    const channel = transport.waitForDataChannel();
    await expect(channel).rejects.toThrow('实时数据通道连接超时');

    fixture.peer.iceGatheringState = 'complete';
    fixture.peer.localDescription = { type: 'offer', sdp: '   ' };
    fixture.peer.setLocalDescription = jest.fn(async () => undefined);
    await expect(transport.createOffer()).rejects.toThrow('本地 Offer SDP 为空');
  });

  it('chains channel handlers and rejects when opening the channel fails', async () => {
    const fixture = createFixture();
    const transport = new ReactNativeWebRTCTransport(fixture.adapter);
    const previousError = jest.fn();
    await transport.prepare();
    fixture.channel.onerror = previousError;

    const pending = transport.waitForDataChannel();
    fixture.channel.onerror?.();

    await expect(pending).rejects.toThrow('实时数据通道连接失败');
    expect(previousError).toHaveBeenCalled();
  });

  it('records connection, ICE and stats telemetry including failures', async () => {
    const fixture = createFixture();
    const telemetry = { record: jest.fn() };
    fixture.peer.getStats = jest
      .fn()
      .mockResolvedValueOnce(new Map())
      .mockRejectedValueOnce(new Error('stats unavailable'))
      .mockResolvedValue(new Map());
    const transport = new ReactNativeWebRTCTransport({
      ...fixture.adapter,
      telemetry,
      rtcStatsIntervalMs: 60_000,
    });
    transport.bindSession('session-1');
    await transport.prepare();

    const ready = transport.waitForDataChannel();
    fixture.channel.open();
    await ready;
    fixture.peer.connectionState = 'disconnected';
    fixture.peer.onconnectionstatechange?.();
    fixture.peer.iceConnectionState = 'connected';
    fixture.peer.oniceconnectionstatechange?.();
    fixture.peer.iceConnectionState = 'failed';
    fixture.peer.oniceconnectionstatechange?.();
    await new Promise((resolve) => setTimeout(resolve, 0));
    transport.close();
    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(telemetry.record).toHaveBeenCalledWith(
      expect.objectContaining({ eventType: 'rtc.connected', sessionId: 'session-1' }),
    );
    expect(telemetry.record).toHaveBeenCalledWith(
      expect.objectContaining({ eventType: 'rtc.ice_connected' }),
    );
    expect(telemetry.record).toHaveBeenCalledWith(
      expect.objectContaining({ eventType: 'rtc.stats_failed', message: 'stats unavailable' }),
    );
    expect(telemetry.record).toHaveBeenCalledWith(
      expect.objectContaining({ eventType: 'rtc.ended', sessionId: 'session-1' }),
    );
  });

  it('closes an existing session before preparing again and supports unsubscribe', async () => {
    const fixture = createFixture();
    const transport = new ReactNativeWebRTCTransport(fixture.adapter);
    const listener = jest.fn();
    const unsubscribe = transport.subscribe(listener);
    unsubscribe();
    await transport.prepare();
    await transport.prepare();
    fixture.peer.connectionState = 'failed';
    fixture.peer.onconnectionstatechange?.();

    expect(fixture.track.stop).toHaveBeenCalledTimes(1);
    expect(fixture.peer.close).toHaveBeenCalledTimes(1);
    expect(listener).not.toHaveBeenCalled();
  });

  it('uses the bundled native adapter and accepts already normalized SDP', async () => {
    mockNativeFixture = createFixture();
    const transport = new ReactNativeWebRTCTransport();
    await transport.prepare();
    await expect(transport.createOffer()).resolves.toBe('offer\nsdp');
    await transport.applyAnswer('answer\r\nsdp\r\n');
    expect(mockNativeFixture.peer.setRemoteDescription).toHaveBeenCalledWith({ type: 'answer', sdp: 'answer\r\nsdp\r\n' });
    transport.close();
  });

  it('covers duplicate connection signals, channel errors, and empty cleanup paths', async () => {
    const empty = createFixture();
    const emptyTransport = new ReactNativeWebRTCTransport(empty.adapter);
    emptyTransport.setAudioEnabled(false);
    emptyTransport.close();

    const fixture = createFixture();
    const telemetry = { record: jest.fn() };
    fixture.peer.getStats = jest.fn(async () => new Map());
    const transport = new ReactNativeWebRTCTransport({ ...fixture.adapter, telemetry, rtcStatsIntervalMs: 1 });
    const listener = jest.fn();
    transport.subscribe(listener);
    await transport.prepare();
    fixture.channel.onerror?.();
    fixture.channel.open();
    fixture.channel.onopen?.();
    await new Promise((resolve) => setTimeout(resolve, 5));
    expect(listener).toHaveBeenCalledWith(expect.objectContaining({ type: 'datachannel.failed' }));
    transport.close();
  });
});
