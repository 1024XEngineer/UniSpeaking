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

  raw(data: string) {
    this.onmessage?.({ data });
  }

  fail() {
    this.onerror?.();
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

  it('maps plain HTTP and rejects non-HTTP backend schemes', () => {
    expect(sessionWebSocketUrl(' http://localhost:8080/root?old=1#hash ', 'token')).toBe(
      'ws://localhost:8080/root/ws/session-messages?access_token=token',
    );
    expect(() => sessionWebSocketUrl('ftp://example.com', 'token')).toThrow(
      '后端地址必须使用 HTTP 或 HTTPS',
    );
  });
});

describe('SessionMessageSocket', () => {
  afterEach(() => {
    jest.useRealTimers();
  });

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

  it('validates session/provider ids and ignores blank transcript content', async () => {
    const nativeSocket = new FakeWebSocket();
    const socket = new SessionMessageSocket({
      baseUrl: 'http://localhost:8080',
      tokenStore: { get: async () => 'token' },
      webSocketFactory: () => nativeSocket,
    });
    await expect(socket.connect('   ')).rejects.toThrow('会话 ID 尚未建立');
    expect(() => socket.bindProviderSession('  ')).toThrow('服务商会话 ID 不能为空');
    await expect(socket.persistMessage({ owner: 0, content: '   ' })).resolves.toBeUndefined();
    await expect(socket.end('now')).rejects.toThrow('会话 ID 尚未建立');
  });

  it('reuses active and in-flight connections for the same session', async () => {
    const nativeSocket = new FakeWebSocket();
    const factory = jest.fn(() => nativeSocket);
    const socket = new SessionMessageSocket({
      baseUrl: 'http://localhost:8080', tokenStore: { get: async () => 'token' }, webSocketFactory: factory,
    });
    const first = socket.connect(' session-1 ');
    await Promise.resolve();
    const second = socket.connect('session-1');
    nativeSocket.open();
    await Promise.all([first, second]);
    await socket.connect('session-1');
    expect(factory).toHaveBeenCalledTimes(1);
  });

  it('uses an injected token coordinator and reports connect errors', async () => {
    const nativeSocket = new FakeWebSocket();
    const tokenCoordinator = { getAccessToken: jest.fn(async () => 'coordinated-token') };
    const factory = jest.fn(() => nativeSocket);
    const socket = new SessionMessageSocket({
      baseUrl: 'http://localhost:8080',
      tokenStore: { get: jest.fn(async () => 'fallback') },
      tokenCoordinator: tokenCoordinator as any,
      webSocketFactory: factory,
    });
    const connecting = socket.connect('session-1');
    await Promise.resolve();
    nativeSocket.fail();
    await expect(connecting).rejects.toThrow('会话 WebSocket 连接失败');
    expect(factory).toHaveBeenCalledWith(expect.stringContaining('coordinated-token'));
  });

  it('times out connection and ACK waits', async () => {
    jest.useFakeTimers();
    const nativeSocket = new FakeWebSocket();
    const socket = new SessionMessageSocket({
      baseUrl: 'http://localhost:8080',
      tokenStore: { get: async () => 'token' },
      webSocketFactory: () => nativeSocket,
      connectTimeoutMs: 5,
      ackTimeoutMs: 5,
    });
    const connecting = socket.connect('session-1');
    await Promise.resolve();
    jest.advanceTimersByTime(5);
    await expect(connecting).rejects.toThrow('会话 WebSocket 连接超时');

    const reconnecting = socket.connect('session-1');
    await Promise.resolve();
    nativeSocket.open();
    await reconnecting;
    const pending = socket.persistMessage({ owner: 1, content: 'hello' });
    jest.advanceTimersByTime(5);
    await expect(pending).rejects.toThrow('等待 session.message.accepted 超时');
  });

  it('ignores malformed and unrelated ACKs and uses code/default failure messages', async () => {
    const nativeSocket = new FakeWebSocket();
    const socket = new SessionMessageSocket({
      baseUrl: 'http://localhost:8080', tokenStore: { get: async () => 'token' }, webSocketFactory: () => nativeSocket,
    });
    const connecting = socket.connect('session-1');
    await Promise.resolve();
    nativeSocket.open();
    await connecting;

    const coded = socket.end('now');
    nativeSocket.raw('{invalid');
    nativeSocket.message({ type: 'session.message.accepted', success: true });
    nativeSocket.message({ type: 'session.end.failed', success: false, code: 'END_FAILED' });
    await expect(coded).rejects.toThrow('END_FAILED');

    const generic = socket.end('later');
    nativeSocket.message({ type: 'session.end.failed', success: false });
    await expect(generic).rejects.toThrow('会话消息处理失败');
  });

  it('rejects sends before open and does not close an already-closing native socket', async () => {
    const nativeSocket = new FakeWebSocket();
    const socket = new SessionMessageSocket({
      baseUrl: 'http://localhost:8080', tokenStore: { get: async () => 'token' }, webSocketFactory: () => nativeSocket,
    });
    const connecting = socket.connect('session-1');
    await Promise.resolve();
    await expect(socket.end('now')).rejects.toThrow('会话 WebSocket 尚未连接');
    nativeSocket.open();
    await connecting;
    nativeSocket.readyState = 2;
    socket.close();
    expect(nativeSocket.close).not.toHaveBeenCalled();
  });
});
