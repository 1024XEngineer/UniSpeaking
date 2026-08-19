import { buildAdminApiUrl } from '../auth/authApi'

export type AiCapability = 'REALTIME' | 'LLM' | 'SCORING' | 'TTS' | 'TRANSCRIPTION'

export interface ProviderView {
  providerId: string
  displayName: string
  adapterType: string
  baseUrl: string | null
  enabled: boolean
  connectTimeoutMs: number
  readTimeoutMs: number
  configVersion: number
}

export interface ModelView {
  modelId: string
  providerId: string
  displayName: string
  capability: AiCapability
  enabled: boolean
  billingUnit: 'TOKENS' | 'AUDIO_MINUTES' | 'CHARACTERS' | 'REQUESTS' | 'MIXED'
  inputPricePerMillion: number
  outputPricePerMillion: number
  characterPricePerMillion: number
  audioInputPricePerMinute: number
  audioOutputPricePerMinute: number
  requestPricePerCall: number
  currency: string
}

export interface RouteView {
  routeKey: string
  capability: AiCapability
  modelIds: string[]
}

export interface AiConfiguration {
  providers: ProviderView[]
  models: ModelView[]
  routes: RouteView[]
  databaseBacked: boolean
}

export interface CredentialStatus {
  configured: boolean
  fingerprint: string | null
  writable: boolean
}

export interface InvocationUsage {
  query: { from: string; to: string; userId: string | null; providerId: string | null; modelId: string | null; limit: number }
  recordPage: { page: number; pageSize: number; totalRecords: number; totalPages: number }
  requestIdCoverage: { recordsWithRequestId: number; eligibleRecords: number }
  summary: {
    requests: number; attempts: number; succeededAttempts: number; fallbackAttempts: number
    inputTokens: number; outputTokens: number; totalTokens: number
    audioInputSeconds: number; audioOutputSeconds: number; averageDurationMs: number
    estimatedCost: number; currency: string
  }
  byModel: Array<{
    providerId: string; modelId: string; capability: string; attempts: number; successes: number
    totalTokens: number; averageDurationMs: number; estimatedCost: number
  }>
  byUser: Array<{
    userId: string | null; email: string | null; requests: number; sessions: number; attempts: number
    successes: number; failures: number; fallbackAttempts: number; inputTokens: number; outputTokens: number
    totalTokens: number; inputCharacters: number; outputCharacters: number; audioInputSeconds: number
    audioOutputSeconds: number; totalDurationMs: number; averageDurationMs: number; estimatedCost: number
    lastInvokedAt: string
    models: Array<{
      providerId: string; modelId: string; capability: string; requests: number; attempts: number
      successes: number; inputTokens: number; outputTokens: number; totalTokens: number
      audioInputSeconds: number; audioOutputSeconds: number; totalDurationMs: number; estimatedCost: number
    }>
  }>
  records: Array<{
    invocationId: string; logicalRequestId: string; attemptNo: number; userId: string | null; userEmail: string | null
    sessionId: string | null
    businessScene: string; routeKey: string; capability: string; providerId: string; modelId: string
    providerRequestId: string | null; startedAt: string; completedAt: string; durationMs: number
    firstTokenLatencyMs: number | null; inputTokens: number; outputTokens: number; totalTokens: number
    inputCharacters: number; outputCharacters: number; audioInputSeconds: number; audioOutputSeconds: number
    usageSource: string; status: string; errorCode: string | null; retryable: boolean
    fallbackFromModelId: string | null; estimatedCost: number; priceCurrency: string
  }>
}

interface ApiErrorBody { error?: { code?: string; message?: string } }

async function requestJson<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(buildAdminApiUrl(path), {
    credentials: 'include',
    ...init,
    headers: { 'Content-Type': 'application/json', ...(init.headers || {}) },
  })
  if (!response.ok) {
    let body: ApiErrorBody = {}
    try { body = await response.json() as ApiErrorBody } catch { body = {} }
    throw new Error(body.error?.message || 'AI 配置服务暂时不可用')
  }
  return response.json() as Promise<T>
}

export const getAiConfiguration = () => requestJson<AiConfiguration>('/api/admin/ai/configuration')

export const updateProvider = (providerId: string, body: Partial<ProviderView>) =>
  requestJson<ProviderView>(`/api/admin/ai/providers/${encodeURIComponent(providerId)}`, {
    method: 'PATCH', body: JSON.stringify(body),
  })

export const updateModel = (modelId: string, body: Partial<ModelView>) =>
  requestJson<ModelView>(`/api/admin/ai/models/${encodeURIComponent(modelId)}`, {
    method: 'PATCH', body: JSON.stringify(body),
  })

export const replaceRoute = (capability: AiCapability, modelIds: string[]) =>
  requestJson<RouteView>(`/api/admin/ai/routes/${capability}`, {
    method: 'PUT', body: JSON.stringify({ routeKey: 'default', modelIds }),
  })

export const getCredentialStatus = (providerId: string) =>
  requestJson<CredentialStatus>(`/api/admin/ai/providers/${encodeURIComponent(providerId)}/credential`)

export const replaceCredential = (providerId: string, secret: string) =>
  requestJson<CredentialStatus>(`/api/admin/ai/providers/${encodeURIComponent(providerId)}/credential`, {
    method: 'PUT', body: JSON.stringify({ secret }),
  })

export function getInvocationUsage(filters: { from?: string; to?: string; userId?: string; providerId?: string; modelId?: string; page?: number; limit?: number }) {
  const params = new URLSearchParams()
  Object.entries(filters).forEach(([key, value]) => { if (value) params.set(key, String(value)) })
  return requestJson<InvocationUsage>(`/api/admin/ai/usage?${params.toString()}`)
}
