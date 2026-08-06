import {
  ReactNativeWebRTCTransport,
  type MediaStreamLike,
  type MediaStreamTrackLike,
  type PeerConnectionLike,
  type RTCDataChannelLike,
} from '../ReactNativeWebRTCTransport';

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
});
