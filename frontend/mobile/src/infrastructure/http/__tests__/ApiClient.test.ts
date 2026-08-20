import { ApiClient, ApiError, type ApiRequestOptions } from '../ApiClient';
import type { TokenStore } from '../../auth/SecureTokenStore';
import { mobileTelemetry } from '../../telemetry/MobileTelemetry';

jest.mock('../../telemetry/MobileTelemetry', () => ({
  mobileTelemetry: { recordApiRequest: jest.fn(async () => undefined) },
}));

function createTokenStore(token: string | null): TokenStore & {
  clear: jest.Mock<Promise<void>, []>;
} {
  return {
    get: jest.fn(async () => token),
    set: jest.fn(async () => undefined),
    clear: jest.fn(async () => undefined),
  };
}

function jsonResponse(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: {
      get: (name: string) =>
        name.toLowerCase() === 'content-type' ? 'application/json' : null,
    },
    json: jest.fn(async () => body),
    text: jest.fn(async () => JSON.stringify(body)),
  } as unknown as Response;
}

describe('ApiClient', () => {
  it('adds the bearer token and unwraps a successful API envelope', async () => {
    const tokenStore = createTokenStore('jwt-token');
    const fetchImpl = jest.fn<Promise<Response>, [string, RequestInit?]>(
      async () => jsonResponse({ success: true, data: { id: 'user-1' } }),
    );
    const client = new ApiClient({
      baseUrl: 'http://127.0.0.1:8080/',
      tokenStore,
      fetchImpl,
    });

    await expect(client.request<{ id: string }>('/api/auth/me')).resolves.toEqual({
      id: 'user-1',
    });

    expect(fetchImpl).toHaveBeenCalledWith(
      'http://127.0.0.1:8080/api/auth/me',
      expect.objectContaining({
        headers: expect.objectContaining({ Authorization: 'Bearer jwt-token' }),
      }),
    );
  });

  it('does not set JSON content type for FormData uploads', async () => {
    const fetchImpl = jest.fn<Promise<Response>, [string, RequestInit?]>(
      async () => jsonResponse({ success: true, data: { overallScore: 86 } }),
    );
    const client = new ApiClient({
      baseUrl: 'http://127.0.0.1:8080',
      tokenStore: createTokenStore('jwt-token'),
      fetchImpl,
    });
    const body = new FormData();
    body.append('transcript', 'Hello');

    await client.request('/api/custom-scenes/scene-1/evaluation', {
      method: 'POST',
      body,
    });

    const request = fetchImpl.mock.calls[0][1] as RequestInit;
    expect(request.headers).toEqual({ Authorization: 'Bearer jwt-token' });
  });

  it('throws the backend error message and status', async () => {
    const client = new ApiClient({
      baseUrl: 'http://127.0.0.1:8080',
      tokenStore: createTokenStore(null),
      fetchImpl: jest.fn(async () =>
        jsonResponse({ success: false, code: 'SCENE_INVALID', message: '场景内容不完整' }, 400),
      ),
    });

    await expect(client.request('/api/custom-scenes/generate')).rejects.toEqual(
      expect.objectContaining({
        message: '场景内容不完整',
        status: 400,
        code: 'SCENE_INVALID',
      } satisfies Partial<ApiError>),
    );
  });

  it('clears the token and notifies auth state after a protected 401', async () => {
    const tokenStore = createTokenStore('expired-token');
    const onUnauthorized = jest.fn(async () => undefined);
    const client = new ApiClient({
      baseUrl: 'http://127.0.0.1:8080',
      tokenStore,
      onUnauthorized,
      fetchImpl: jest.fn(async () =>
        jsonResponse({ success: false, message: '登录已过期' }, 401),
      ),
    });

    await expect(client.request('/api/user-preferences')).rejects.toThrow('登录已过期');
    expect(tokenStore.clear).toHaveBeenCalledTimes(1);
    expect(onUnauthorized).toHaveBeenCalledTimes(1);
    expect(mobileTelemetry.recordApiRequest).toHaveBeenCalledWith(
      expect.objectContaining({
        path: '/api/user-preferences',
        outcome: 'unauthenticated',
        status: 401,
      }),
    );
  });

  it('aborts a request after its configured timeout', async () => {
    jest.useFakeTimers();
    const fetchImpl = jest.fn<Promise<Response>, [string, RequestInit?]>(
      async (_url, init) =>
        new Promise<Response>((resolve, reject) => {
          init?.signal?.addEventListener('abort', () => {
            const error = new Error('aborted');
            error.name = 'AbortError';
            reject(error);
          });
          setTimeout(
            () => resolve(jsonResponse({ success: true, data: { late: true } })),
            1_000,
          );
        }),
    );
    const client = new ApiClient({
      baseUrl: 'http://127.0.0.1:8080',
      tokenStore: createTokenStore('jwt-token'),
      fetchImpl,
    });

    const request = client.request('/api/slow', {
      timeoutMs: 10,
    } satisfies ApiRequestOptions);
    const expectation = expect(request).rejects.toEqual(
      expect.objectContaining({
        message: '请求超时，请检查网络后重试',
        status: 408,
        code: 'REQUEST_TIMEOUT',
      } satisfies Partial<ApiError>),
    );
    await jest.runAllTimersAsync();
    await expectation;
    jest.useRealTimers();
  });
});
