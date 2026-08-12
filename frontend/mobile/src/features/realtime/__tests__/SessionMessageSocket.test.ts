import {
  SessionMessageSocket,
  sessionWebSocketUrl,
  type SessionWebSocketLike,
} from '../SessionMessageSocket';

class FakeWebSocket implements SessionWebSocketLike {
  static readonly CONNECTING = 0;
  static readonly OPEN = 1;

  readyState = FakeWebSocket.CONNECTING;
  onopen: (() => void) | null = null;
  onmessage: ((event: { data: string }) => void) | null = null;
  onerror: (() => void) | null = null;
  onclose: (() => void) | null = null;
  readonly sent: string[] = [];
  close = jest.fn(() => {
    this.readyState = 3;
    this.onclose?.();
  });

  send(data: string) {
    this.sent.push(data);
  }

  open() {
    this.readyState = FakeWebSocket.OPEN;
    this.onopen?.();
  }

  message(data: unknown) {
    this.onmessage?.({ data: JSON.stringify(data) });
  }
}

describe('sessionWebSocketUrl', () => {
  it('maps HTTP to an authenticated WebSocket URL and preserves a base path', () => {
    expect(
      sessionWebSocketUrl('https://api.example.com/backend/', 'signed token/+'),
    ).toBe(
      'wss://api.example.com/backend/ws/session-messages?access_token=signed+token%2F%2B',
    );
  });
});

describe('SessionMessageSocket', () => {
  it('requires a saved access token before connecting', async () => {
    const socket = new SessionMessageSocket({
      baseUrl: 'http://127.0.0.1:8080',
      tokenStore: { get: async () => null },
      webSocketFactory: jest.fn(),
    });

    await expect(socket.connect('session-1')).rejects.toThrow(
      '请先登录后再建立会话 WebSocket',
    );
  });

  it('persists a transcript and resolves only after the matching accepted ACK', async () => {
    const nativeSocket = new FakeWebSocket();
    const socket = new SessionMessageSocket({
      baseUrl: 'http://127.0.0.1:8080',
      tokenStore: { get: async () => 'jwt-token' },
      webSocketFactory: jest.fn(() => nativeSocket),
    });

    const connecting = socket.connect('session-1');
    await Promise.resolve();
    nativeSocket.open();
    await connecting;
    const pending = socket.persistMessage({
      owner: 1,
      content: '  Hello there.  ',
      providerMessageId: 'provider-1',
    });

    expect(JSON.parse(nativeSocket.sent[0])).toEqual({
      type: 'message',
      sessionId: 'session-1',
      message: { owner: 1, content: 'Hello there.', audio: null },
      stopTime: null,
      providerSessionId: null,
    });
    nativeSocket.message({
      type: 'session.message.accepted',
      success: true,
      sessionId: 'session-1',
    });
    await expect(pending).resolves.toBeUndefined();
  });

  it('sends the stop timestamp and rejects a backend failure ACK', async () => {
    const nativeSocket = new FakeWebSocket();
    const socket = new SessionMessageSocket({
      baseUrl: 'http://127.0.0.1:8080',
      tokenStore: { get: async () => 'jwt-token' },
      webSocketFactory: () => nativeSocket,
    });
    const connecting = socket.connect('session-1');
    await Promise.resolve();
    nativeSocket.open();
    await connecting;

    const pending = socket.end('2026-08-05T08:00:00.000Z');
    expect(JSON.parse(nativeSocket.sent[0])).toEqual({
      type: 'end',
      sessionId: 'session-1',
      message: null,
      stopTime: '2026-08-05T08:00:00.000Z',
      providerSessionId: null,
    });
    nativeSocket.message({
      type: 'session.end.failed',
      success: false,
      code: 'SESSION_SOCKET_ERROR',
      message: 'session already ended',
    });

    await expect(pending).rejects.toThrow('session already ended');
  });

  it('binds the provider session and waits for the accepted ACK', async () => {
    const nativeSocket = new FakeWebSocket();
    const socket = new SessionMessageSocket({
      baseUrl: 'http://127.0.0.1:8080',
      tokenStore: { get: async () => 'jwt-token' },
      webSocketFactory: () => nativeSocket,
    });
    const connecting = socket.connect('session-1');
    await Promise.resolve();
    nativeSocket.open();
    await connecting;

    const pending = socket.bindProviderSession(' provider-1 ');
    expect(JSON.parse(nativeSocket.sent[0])).toEqual({
      type: 'bind',
      sessionId: 'session-1',
      message: null,
      stopTime: null,
      providerSessionId: 'provider-1',
    });
    nativeSocket.message({
      type: 'session.bind.accepted',
      success: true,
      sessionId: 'session-1',
    });
    await expect(pending).resolves.toEqual(expect.objectContaining({
      type: 'session.bind.accepted',
      success: true,
    }));
  });

  it('rejects pending acknowledgements when the socket closes', async () => {
    const nativeSocket = new FakeWebSocket();
    const socket = new SessionMessageSocket({
      baseUrl: 'http://127.0.0.1:8080',
      tokenStore: { get: async () => 'jwt-token' },
      webSocketFactory: () => nativeSocket,
    });
    const connecting = socket.connect('session-1');
    await Promise.resolve();
    nativeSocket.open();
    await connecting;

    const pending = socket.persistMessage({ owner: 0, content: 'Welcome.' });
    socket.close();

    await expect(pending).rejects.toThrow('会话 WebSocket 已关闭');
    expect(nativeSocket.close).toHaveBeenCalledTimes(1);
    socket.close();
    expect(nativeSocket.close).toHaveBeenCalledTimes(1);
  });
});
