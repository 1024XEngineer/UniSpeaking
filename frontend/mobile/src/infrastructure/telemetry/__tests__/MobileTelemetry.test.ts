const mockGetToken = jest.fn();
const mockGetNetworkState = jest.fn();
const mockSentryInit = jest.fn();
const mockSentrySetUser = jest.fn();
const mockSentryCaptureException = jest.fn();
const mockSentryWrap = jest.fn((component) => component);

jest.mock('@/infrastructure/auth/SecureTokenStore', () => ({
  SecureTokenStore: jest.fn(() => ({ get: mockGetToken })),
}));

jest.mock('@/infrastructure/config/runtimeConfig', () => ({
  getRuntimeConfig: () => ({ backendUrl: 'https://api.example.test' }),
}));

jest.mock('expo-constants', () => ({
  expoConfig: { version: '1.2.3' },
  nativeAppVersion: null,
}));

jest.mock('expo-device', () => ({
  brand: 'Apple', modelName: 'Simulator', osName: 'iOS', osVersion: '18', isDevice: false,
}));

jest.mock('expo-network', () => ({ getNetworkStateAsync: mockGetNetworkState }));
jest.mock('@sentry/react-native', () => ({
  init: mockSentryInit,
  setUser: mockSentrySetUser,
  captureException: mockSentryCaptureException,
  wrap: mockSentryWrap,
}), { virtual: true });

describe('mobileTelemetry event delivery', () => {
  const originalNodeEnv = process.env.NODE_ENV;
  let fetchMock: jest.Mock;
  let mobileTelemetry: typeof import('../MobileTelemetry').mobileTelemetry;

  beforeEach(() => {
    jest.resetModules();
    jest.useFakeTimers();
    process.env.NODE_ENV = 'production';
    mockGetToken.mockResolvedValue('access-token');
    mockGetNetworkState.mockResolvedValue({ type: 'WIFI', isConnected: true, isInternetReachable: true });
    fetchMock = jest.fn().mockResolvedValue({ ok: true, status: 202 });
    global.fetch = fetchMock as typeof fetch;
    // Load after NODE_ENV is set: telemetry deliberately disables delivery in test mode.
    ({ mobileTelemetry } = require('../MobileTelemetry') as typeof import('../MobileTelemetry'));
  });

  afterEach(() => {
    jest.useRealTimers();
    process.env.NODE_ENV = originalNodeEnv;
    jest.clearAllMocks();
  });

  it('drops invalid event names and sends a scrubbed, authenticated event', async () => {
    mobileTelemetry.record({ eventType: 'Not valid' });
    mobileTelemetry.record({
      eventType: 'mobile.session_finished',
      route: 'https://app.example.test/conversation?token=secret',
      message: 'a'.repeat(700),
      stack: 'Error: bad?access_token=secret',
      attributes: {
        valid_key: 'x'.repeat(600),
        invalidKey: 'discard',
        nullable: null,
        non_finite: Number.NaN,
        retries: 2,
      },
    });

    await mobileTelemetry.flush();

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, request] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('https://api.example.test/api/telemetry/events');
    expect(request.headers).toEqual({ 'Content-Type': 'application/json', Authorization: 'Bearer access-token' });
    const event = JSON.parse(String(request.body)).events[0];
    expect(event.route).toBe('/conversation');
    expect(event.message).toHaveLength(500);
    expect(event.stack).toContain('access_token=[redacted]');
    expect(event.attributes).toMatchObject({ valid_key: 'x'.repeat(500), retries: 2, app_version: '1.2.3' });
    expect(event.attributes).not.toHaveProperty('invalidKey');
    expect(event.attributes).not.toHaveProperty('nullable');
    expect(event.attributes).not.toHaveProperty('non_finite');
  });

  it('retains a failed batch and successfully retries it', async () => {
    fetchMock.mockResolvedValueOnce({ ok: false, status: 500 }).mockResolvedValueOnce({ ok: true, status: 202 });
    mobileTelemetry.record({ eventType: 'mobile.network_failed' });

    await mobileTelemetry.flush();
    await mobileTelemetry.flush();

    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(JSON.parse(String(fetchMock.mock.calls[1][1].body)).events).toHaveLength(1);
  });

  it('updates the anonymous/user identity and ignores empty flushes', async () => {
    mobileTelemetry.setUser('user-42');
    await mobileTelemetry.flush();
    mobileTelemetry.setUser(null);
    await mobileTelemetry.flush();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('installs production telemetry handlers and queues startup events', async () => {
    process.env.EXPO_PUBLIC_SENTRY_DSN = 'https://sentry.example/1';
    mobileTelemetry.initialize();
    mobileTelemetry.record({ eventType: 'mobile.manual_check', severity: 'WARN', attributes: { enabled: true } });
    await mobileTelemetry.flush();
    expect(fetchMock).toHaveBeenCalled();
    delete process.env.EXPO_PUBLIC_SENTRY_DSN;
  });

  it('flushes a scheduled event and sends without authorization when no token exists', async () => {
    mockGetToken.mockResolvedValueOnce(null);
    mobileTelemetry.record({ eventType: 'mobile.scheduled' });
    await jest.advanceTimersByTimeAsync(5_000);

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0][1].headers).toEqual({ 'Content-Type': 'application/json' });
  });

  it('scrubs Sentry requests and invokes global exception and rejection handlers', async () => {
    const previousErrorHandler = jest.fn();
    const setGlobalHandler = jest.fn();
    const previousRejection = jest.fn();
    (globalThis as any).ErrorUtils = {
      getGlobalHandler: () => previousErrorHandler,
      setGlobalHandler,
    };
    (globalThis as any).onunhandledrejection = previousRejection;
    process.env.EXPO_PUBLIC_SENTRY_DSN = 'https://sentry.example/1';
    mobileTelemetry.initialize();
    await Promise.resolve();

    const options = mockSentryInit.mock.calls[0][0];
    const event = options.beforeSend({
      request: {
        url: 'https://example.test/private?token=secret',
        data: { secret: true },
        cookies: 'secret',
        headers: { Authorization: 'secret' },
      },
    });
    expect(event.request).toEqual({ url: '/private' });
    expect(options.beforeSend({ message: 'safe' })).toEqual({ message: 'safe' });

    const installed = setGlobalHandler.mock.calls[0][0];
    installed(new TypeError('fatal failure'), true);
    installed(new Error('recoverable'), false);
    expect(previousErrorHandler).toHaveBeenCalledTimes(2);
    (globalThis as any).onunhandledrejection({ reason: 'rejected value' });
    (globalThis as any).onunhandledrejection({ reason: new Error('rejected error') });
    expect(previousRejection).toHaveBeenCalledTimes(2);

    mobileTelemetry.setUser('user-1');
    mobileTelemetry.setUser(null);
    mobileTelemetry.captureException('plain failure');
    mobileTelemetry.captureException(new Error('typed failure'), { eventType: 'mobile.custom_exception' });
    expect(mockSentrySetUser).toHaveBeenCalledWith({ id: 'user-1' });
    expect(mockSentrySetUser).toHaveBeenCalledWith(null);
    expect(mockSentryCaptureException).toHaveBeenCalledTimes(2);
    delete (globalThis as any).ErrorUtils;
    delete (globalThis as any).onunhandledrejection;
    delete process.env.EXPO_PUBLIC_SENTRY_DSN;
  });

  it('records API network details, severity variants and network lookup failures', async () => {
    const record = jest.spyOn(mobileTelemetry, 'record');
    await mobileTelemetry.recordApiRequest({
      path: 'https://api.example.test/items?token=secret',
      method: 'GET',
      durationMs: 12,
      outcome: 'success',
      status: 200,
    });
    expect(record).toHaveBeenLastCalledWith(expect.objectContaining({
      severity: 'INFO',
      attributes: expect.objectContaining({ network_type: 'WIFI', network_connected: true }),
    }));

    mockGetNetworkState.mockResolvedValueOnce({ type: null, isConnected: true, isInternetReachable: false });
    await mobileTelemetry.recordApiRequest({ path: '/auth', method: 'POST', durationMs: 20, outcome: 'unauthenticated' });
    mockGetNetworkState.mockRejectedValueOnce(new Error('network unavailable'));
    await mobileTelemetry.recordApiRequest({ path: '/items', method: 'GET', durationMs: 30, outcome: 'timeout' });
    expect(record).toHaveBeenLastCalledWith(expect.objectContaining({
      severity: 'ERROR',
      attributes: expect.objectContaining({ network_type: 'unknown', network_connected: false }),
    }));
  });

  it('accepts rate limiting and caps oversized queues while retaining failed batches', async () => {
    fetchMock.mockResolvedValue({ ok: false, status: 429 });
    for (let index = 0; index < 105; index += 1) {
      mobileTelemetry.record({ eventType: 'mobile.queue', attributes: { index } });
    }
    await Promise.resolve();
    await mobileTelemetry.flush();
    expect(fetchMock).toHaveBeenCalled();

    fetchMock.mockRejectedValueOnce(new Error('offline'));
    mobileTelemetry.record({ eventType: 'mobile.retry' });
    await mobileTelemetry.flush();
    fetchMock.mockResolvedValue({ ok: true, status: 202 });
    await mobileTelemetry.flush();
    expect(fetchMock.mock.calls.length).toBeGreaterThan(2);
  });

  it('delegates public initialization and root wrapping outside test mode', () => {
    const module = require('../MobileTelemetry') as typeof import('../MobileTelemetry');
    module.initializeMobileTelemetry();
    const Component = (() => null) as any;
    expect(module.wrapTelemetryRoot(Component)).toBe(Component);
    expect(mockSentryWrap).toHaveBeenCalledWith(Component);
  });
});
