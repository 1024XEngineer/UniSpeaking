const mockGetToken = jest.fn();

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

jest.mock('expo-network', () => ({}));
jest.mock('@sentry/react-native', () => ({ init: jest.fn(), setUser: jest.fn() }), { virtual: true });

describe('mobileTelemetry event delivery', () => {
  const originalNodeEnv = process.env.NODE_ENV;
  let fetchMock: jest.Mock;
  let mobileTelemetry: typeof import('../MobileTelemetry').mobileTelemetry;

  beforeEach(() => {
    jest.resetModules();
    jest.useFakeTimers();
    process.env.NODE_ENV = 'production';
    mockGetToken.mockResolvedValue('access-token');
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
});
