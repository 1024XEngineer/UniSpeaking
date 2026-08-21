import { AuthTokenCoordinator, authTokenCoordinator } from '../auth/AuthTokenCoordinator';
import { SecureTokenStore, type TokenStore } from '../auth/SecureTokenStore';
import { mobileTelemetry } from '../telemetry/MobileTelemetry';

type FetchLike = (input: string, init?: RequestInit) => Promise<Response>;

type ApiEnvelope<T> = {
  success: boolean;
  data?: T;
  code?: string;
  message?: string;
};

export type ApiClientOptions = {
  baseUrl: string;
  tokenStore: TokenStore;
  fetchImpl?: FetchLike;
  onUnauthorized?: () => void | Promise<void>;
  tokenCoordinator?: AuthTokenCoordinator;
};

export type ApiRequestOptions = RequestInit & {
  timeoutMs?: number;
};

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
    public readonly code?: string,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

function toHeaderRecord(headers: HeadersInit | undefined): Record<string, string> {
  if (!headers) return {};
  if (Array.isArray(headers)) return Object.fromEntries(headers);
  if (typeof Headers !== 'undefined' && headers instanceof Headers) {
    return Object.fromEntries(headers.entries());
  }
  return { ...headers } as Record<string, string>;
}

function isApiEnvelope<T>(body: unknown): body is ApiEnvelope<T> {
  return Boolean(
    body &&
      typeof body === 'object' &&
      'success' in body &&
      typeof (body as { success?: unknown }).success === 'boolean',
  );
}

export class ApiClient {
  private readonly baseUrl: string;
  private readonly tokenStore: TokenStore;
  private readonly fetchImpl: FetchLike;
  private readonly onUnauthorized?: () => void | Promise<void>;
  private readonly tokenCoordinator: AuthTokenCoordinator;

  constructor(options: ApiClientOptions) {
    this.baseUrl = options.baseUrl.replace(/\/+$/, '');
    this.tokenStore = options.tokenStore;
    this.tokenCoordinator = options.tokenCoordinator ?? (
      options.tokenStore instanceof SecureTokenStore
        ? authTokenCoordinator
        : new AuthTokenCoordinator(options.tokenStore)
    );
    this.fetchImpl = options.fetchImpl ?? fetch;
    this.onUnauthorized = options.onUnauthorized;
  }

  async request<T>(path: string, options: ApiRequestOptions = {}): Promise<T> {
    const { timeoutMs = 15_000, ...requestOptions } = options;
    const skipAuthorization = toHeaderRecord(requestOptions.headers)['X-Skip-Authorization'] === 'true';
    const requestOnce = async (token: string | null) => {
      const isFormData = typeof FormData !== 'undefined' && requestOptions.body instanceof FormData;
      const suppliedHeaders = toHeaderRecord(requestOptions.headers);
      delete suppliedHeaders['X-Skip-Authorization'];
      const headers = {
        ...(requestOptions.body && !isFormData ? { 'Content-Type': 'application/json' } : {}),
        ...(!skipAuthorization && token ? { Authorization: `Bearer ${token}` } : {}),
        ...suppliedHeaders,
      };
      return this.fetchWithTimeout(path, requestOptions, headers, timeoutMs);
    };

    let token = skipAuthorization ? null : await this.tokenCoordinator.getAccessToken();
    let response = await requestOnce(token);

    if (response.status === 401 && !skipAuthorization && !path.startsWith('/api/auth/')) {
      const refreshedToken = await this.tokenCoordinator.refreshAccessToken();
      if (refreshedToken) {
        token = refreshedToken;
        response = await requestOnce(token);
      }
    }

    return this.parseResponse<T>(path, requestOptions, response);
  }

  private async fetchWithTimeout(path: string, requestOptions: RequestInit, headers: Record<string, string>, timeoutMs: number) {

    const controller = new AbortController();
    const externalSignal = requestOptions.signal;
    const abortFromExternalSignal = () => controller.abort();
    if (externalSignal?.aborted) controller.abort();
    else externalSignal?.addEventListener('abort', abortFromExternalSignal, { once: true });
    const timeout = setTimeout(() => controller.abort(), timeoutMs);
	const startedAt = Date.now();
	const method = requestOptions.method || 'GET';

    let response: Response;
    try {
      response = await this.fetchImpl(
        `${this.baseUrl}${path.startsWith('/') ? path : `/${path}`}`,
        {
          ...requestOptions,
          headers,
          signal: controller.signal,
        },
      );
    } catch (error) {
      if (controller.signal.aborted && !externalSignal?.aborted) {
		void mobileTelemetry.recordApiRequest({
		  path,
		  method,
		  durationMs: Date.now() - startedAt,
		  outcome: 'timeout',
		  status: 408,
		  message: '请求超时',
		});
        throw new ApiError(
          '请求超时，请检查网络后重试',
          408,
          'REQUEST_TIMEOUT',
        );
      }
	  void mobileTelemetry.recordApiRequest({
		path,
		method,
		durationMs: Date.now() - startedAt,
		outcome: 'network_error',
		message: error instanceof Error ? error.message : 'Network request failed',
	  });
      throw error;
    } finally {
      clearTimeout(timeout);
      externalSignal?.removeEventListener('abort', abortFromExternalSignal);
    }

    return response;
  }

  private async parseResponse<T>(path: string, requestOptions: RequestInit, response: Response): Promise<T> {
    const startedAt = Date.now();
    const method = requestOptions.method || 'GET';
    const contentType = response.headers.get('content-type') ?? '';
    const body: unknown = contentType.includes('application/json')
      ? await response.json()
      : await response.text();

    if (response.status === 401 && !path.startsWith('/api/auth/')) {
      await this.tokenCoordinator.clear();
      await this.onUnauthorized?.();
    }

    if (!response.ok || (isApiEnvelope(body) && !body.success)) {
      const envelope = isApiEnvelope(body) ? body : undefined;
	  void mobileTelemetry.recordApiRequest({
		path,
		method,
		durationMs: Date.now() - startedAt,
		outcome: response.status === 401 ? 'unauthenticated' : 'error',
		status: response.status,
		message: envelope?.message ?? envelope?.code ?? `请求失败（${response.status}）`,
	  });
      throw new ApiError(
        envelope?.message ?? envelope?.code ?? `请求失败（${response.status}）`,
        response.status,
        envelope?.code,
      );
    }

	void mobileTelemetry.recordApiRequest({
	  path,
	  method,
	  durationMs: Date.now() - startedAt,
	  outcome: 'success',
	  status: response.status,
	});

    return (isApiEnvelope<T>(body) ? body.data : body) as T;
  }
}
