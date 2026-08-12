import type { components } from '../../api/generated'
import { buildAdminApiUrl } from '../auth/authApi'

export type DashboardSummary = components['schemas']['DashboardSummary']
export type UsageUser = components['schemas']['UsageUser']
export type UsageSession = components['schemas']['UsageSession']
export type ReconciliationRecord = components['schemas']['ReconciliationRecord']
export type DataSourceState = components['schemas']['DataSourceState']
export type OfficialUsageSyncResult = components['schemas']['OfficialUsageSyncResult']
export type UserEntitlement = components['schemas']['UserEntitlement']
export type UpdateUserEntitlementRequest = components['schemas']['UpdateUserEntitlementRequest']

interface ApiErrorEnvelope {
  error?: { code?: string; message?: string }
  request_id?: string
}

export class GovernanceApiError extends Error {
  readonly code: string
  readonly requestId?: string

  constructor(
    code: string,
    message: string,
    requestId?: string,
  ) {
    super(message)
    this.name = 'GovernanceApiError'
    this.code = code
    this.requestId = requestId
  }
}

async function getJson<T>(path: string): Promise<T> {
  const response = await fetch(buildAdminApiUrl(path), { credentials: 'include' })
  if (!response.ok) {
    let body: ApiErrorEnvelope | undefined
    try {
      body = await response.json() as ApiErrorEnvelope
    } catch {
      body = undefined
    }
    throw new GovernanceApiError(
      body?.error?.code ?? 'NETWORK_RESPONSE_INVALID',
      body?.error?.message ?? '数据服务暂时不可用',
      body?.request_id,
    )
  }
  return response.json() as Promise<T>
}

async function postJson<T>(path: string, headers: Record<string, string> = {}): Promise<T> {
  const response = await fetch(buildAdminApiUrl(path), { method: 'POST', credentials: 'include', headers })
  if (!response.ok) {
    let body: ApiErrorEnvelope | undefined
    try {
      body = await response.json() as ApiErrorEnvelope
    } catch {
      body = undefined
    }
    throw new GovernanceApiError(
      body?.error?.code ?? 'NETWORK_RESPONSE_INVALID',
      body?.error?.message ?? '同步服务暂时不可用',
      body?.request_id,
    )
  }
  return response.json() as Promise<T>
}

async function patchJson<T>(path: string, body: unknown): Promise<T> {
  const response = await fetch(buildAdminApiUrl(path), {
    method: 'PATCH',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!response.ok) {
    let errorBody: ApiErrorEnvelope | undefined
    try {
      errorBody = await response.json() as ApiErrorEnvelope
    } catch {
      errorBody = undefined
    }
    throw new GovernanceApiError(
      errorBody?.error?.code ?? 'NETWORK_RESPONSE_INVALID',
      errorBody?.error?.message ?? '权限更新暂时不可用',
      errorBody?.request_id,
    )
  }
  return response.json() as Promise<T>
}

export function getDashboardSummary(): Promise<DashboardSummary> {
  return getJson('/api/admin/dashboard/summary')
}

export async function listUsageUsers(): Promise<UsageUser[]> {
  return (await getJson<{ users: UsageUser[] }>('/api/admin/users')).users
}

export function updateUserEntitlement(
  userId: string,
  request: UpdateUserEntitlementRequest,
): Promise<UserEntitlement> {
  return patchJson(`/api/admin/users/${encodeURIComponent(userId)}/entitlement`, request)
}

export async function listRealtimeSessions(): Promise<UsageSession[]> {
  return (await getJson<{ sessions: UsageSession[] }>('/api/admin/realtime/sessions')).sessions
}

export async function listReconciliationRecords(): Promise<ReconciliationRecord[]> {
  return (await getJson<{ records: ReconciliationRecord[] }>('/api/admin/reconciliation/records')).records
}

export async function listDataSources(): Promise<DataSourceState[]> {
  return (await getJson<{ sources: DataSourceState[] }>('/api/admin/data-sources')).sources
}

export function syncAliyunOfficialUsage(): Promise<OfficialUsageSyncResult> {
  return postJson('/api/admin/data-sources/aliyun-sls/sync', { 'X-Admin-Action': 'sync-official-usage' })
}
