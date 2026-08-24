const mockGetToken = jest.fn();
const mockAppStateListener = jest.fn();
const mockAddEventListener = jest.fn((_event: string, listener: (state: string) => void) => {
  mockAppStateListener.mockImplementation(listener);
  return { remove: jest.fn() };
});
const mockGetNetworkState = jest.fn();
const mockSentry = { init: jest.fn(), setUser: jest.fn(), captureException: jest.fn(), wrap: jest.fn((component) => component) };
const mockCrashlytics = { marker: 'crashlytics' };
const mockCrashlyticsSdk = {
  getCrashlytics: jest.fn(() => mockCrashlytics),
  setCrashlyticsCollectionEnabled: jest.fn(async () => undefined),
  setUserId: jest.fn(async () => undefined),
  recordError: jest.fn(),
};

jest.mock('@/infrastructure/auth/SecureTokenStore', () => ({
  SecureTokenStore: jest.fn(() => ({ get: mockGetToken })),
}));
jest.mock('@/infrastructure/config/runtimeConfig', () => ({
  getRuntimeConfig: () => ({ backendUrl: 'https://api.example.test' }),
}));
jest.mock('expo-constants', () => ({
  expoConfig: { version: '9.9.9', extra: { firebaseCrashlyticsIosConfigured: true } },
  nativeAppVersion: null,
}));
jest.mock('expo-device', () => ({
  brand: 'Apple', modelName: 'Simulator', osName: 'iOS', osVersion: '18', isDevice: false,
}));
jest.mock('expo-network', () => ({ getNetworkStateAsync: mockGetNetworkState }));
jest.mock('react-native', () => ({
  Platform: { OS: 'ios', Version: '18' },
  AppState: { addEventListener: mockAddEventListener },
}));
jest.mock('@sentry/react-native', () => mockSentry, { virtual: true });
jest.mock('@react-native-firebase/crashlytics', () => mockCrashlyticsSdk, { virtual: true });

describe('mobile telemetry native integrations', () => {
  const originalNodeEnv = process.env.NODE_ENV;
  const originalDsn = process.env.EXPO_PUBLIC_SENTRY_DSN;
  const originalErrorUtils = (globalThis as typeof globalThis & { ErrorUtils?: unknown }).ErrorUtils;
  const originalUnhandledRejection = globalThis.onunhandledrejection;

  beforeEach(() => {
    jest.clearAllMocks();
    jest.useFakeTimers();
    process.env.NODE_ENV = 'production';
    process.env.EXPO_PUBLIC_SENTRY_DSN = 'https://sentry.example/1';
    mockGetToken.mockResolvedValue('access-token');
    mockGetNetworkState.mockResolvedValue({ type: 'wifi', isConnected: true, isInternetReachable: true });
    global.fetch = jest.fn().mockResolvedValue({ ok: true, status: 202 }) as typeof fetch;
  });

  afterEach(() => {
    jest.clearAllTimers();
    jest.useRealTimers();
    process.env.NODE_ENV = originalNodeEnv;
    if (originalDsn === undefined) delete process.env.EXPO_PUBLIC_SENTRY_DSN;
    else process.env.EXPO_PUBLIC_SENTRY_DSN = originalDsn;
    (globalThis as typeof globalThis & { ErrorUtils?: unknown }).ErrorUtils = originalErrorUtils;
    globalThis.onunhandledrejection = originalUnhandledRejection;
  });

  it('initializes crash reporting, captures native failures, and records network context', async () => {
    const previousHandler = jest.fn();
    let installedHandler: ((error: Error, isFatal?: boolean) => void) | undefined;
    (globalThis as typeof globalThis & { ErrorUtils?: unknown }).ErrorUtils = {
      getGlobalHandler: () => previousHandler,
      setGlobalHandler: (handler: (error: Error, isFatal?: boolean) => void) => { installedHandler = handler; },
    };
    const previousRejection = jest.fn();
    globalThis.onunhandledrejection = previousRejection;

    const { mobileTelemetry } = require('../MobileTelemetry') as typeof import('../MobileTelemetry');
    mobileTelemetry.initialize();
    await Promise.resolve();
    await Promise.resolve();

    expect(mockSentry.init).toHaveBeenCalledWith(expect.objectContaining({ enabled: true, release: 'mobile@9.9.9' }));
    expect(mockCrashlyticsSdk.setCrashlyticsCollectionEnabled).toHaveBeenCalledWith(mockCrashlytics, true);
    expect(mockCrashlyticsSdk.setUserId).toHaveBeenCalledWith(mockCrashlytics, 'anonymous');
    expect(mockAddEventListener).toHaveBeenCalledWith('change', expect.any(Function));

    mobileTelemetry.setUser('user-42');
    installedHandler!(new Error('native crash'), true);
    globalThis.onunhandledrejection?.call(globalThis as unknown as Window, { reason: 'lost connection' } as PromiseRejectionEvent);
    mockAppStateListener('background');
    mobileTelemetry.captureException('plain failure', { eventType: 'mobile.manual_failure' });
    await mobileTelemetry.recordApiRequest({ path: '/api/me?token=secret', method: 'GET', durationMs: 12, outcome: 'success', status: 200 });
    mockGetNetworkState.mockRejectedValueOnce(new Error('network details unavailable'));
    await mobileTelemetry.recordApiRequest({ path: '/api/score', method: 'POST', durationMs: 42, outcome: 'error', message: 'bad gateway' });
    mobileTelemetry.setUser(null);
    await mobileTelemetry.flush();

    expect(previousHandler).toHaveBeenCalledWith(expect.any(Error), true);
    expect(previousRejection).toHaveBeenCalledWith(expect.objectContaining({ reason: 'lost connection' }));
    expect(mockCrashlyticsSdk.recordError).toHaveBeenCalledWith(mockCrashlytics, expect.any(Error), expect.any(String));
    expect(mockSentry.captureException).toHaveBeenCalledWith(expect.objectContaining({ message: 'plain failure' }));
    expect(mockSentry.setUser).toHaveBeenLastCalledWith(null);
    const events = (global.fetch as jest.Mock).mock.calls.flatMap(([, request]) =>
      JSON.parse(String((request as RequestInit).body)).events,
    );
    expect(events).toEqual(expect.arrayContaining([
      expect.objectContaining({ eventType: 'api.request', route: '/api/me', attributes: expect.objectContaining({ network_type: 'wifi', network_connected: true }) }),
      expect.objectContaining({ eventType: 'api.request', route: '/api/score', severity: 'ERROR', attributes: expect.objectContaining({ network_type: 'unknown', network_connected: false }) }),
      expect.objectContaining({ eventType: 'mobile.js_crash', severity: 'FATAL' }),
      expect.objectContaining({ eventType: 'mobile.unhandled_rejection', severity: 'ERROR' }),
    ]));
  });

  it('wraps roots through Sentry outside the test runtime', () => {
    const { wrapTelemetryRoot } = require('../MobileTelemetry') as typeof import('../MobileTelemetry');
    const Root = () => null;
    expect(wrapTelemetryRoot(Root)).toBe(Root);
    expect(mockSentry.wrap).toHaveBeenCalledWith(Root);
  });
});
