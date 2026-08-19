import type { TokenStore } from '../auth/SecureTokenStore';
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

  constructor(options: ApiClientOptions) {
    this.baseUrl = options.baseUrl.replace(/\/+$/, '');
    this.tokenStore = options.tokenStore;
    this.fetchImpl = options.fetchImpl ?? fetch;
    this.onUnauthorized = options.onUnauthorized;
  }

  async request<T>(path: string, options: ApiRequestOptions = {}): Promise<T> {
    const { timeoutMs = 15_000, ...requestOptions } = options;
    const token = await this.tokenStore.get();
    const isFormData =
      typeof FormData !== 'undefined' && requestOptions.body instanceof FormData;
    const headers = {
      ...(requestOptions.body && !isFormData ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...toHeaderRecord(requestOptions.headers),
    };

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

    const contentType = response.headers.get('content-type') ?? '';
    const body: unknown = contentType.includes('application/json')
      ? await response.json()
      : await response.text();

    if (response.status === 401 && !path.startsWith('/api/auth/')) {
      await this.tokenStore.clear();
      await this.onUnauthorized?.();
    }

    if (!response.ok || (isApiEnvelope(body) && !body.success)) {
      const envelope = isApiEnvelope(body) ? body : undefined;
	  void mobileTelemetry.recordApiRequest({
		path,
		method,
		durationMs: Date.now() - startedAt,
		outcome: 'error',
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
