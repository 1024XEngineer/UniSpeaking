import { buildAdminApiUrl } from '../auth/authApi'

export type IssueType = 'BUG' | 'OPTIMIZATION'
export type IssuePlatform = 'WEB' | 'MOBILE' | 'BACKEND' | 'CROSS_PLATFORM'
export type IssueSeverity = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW'
export type IssueStatus = 'OPEN' | 'INVESTIGATING' | 'IN_PROGRESS' | 'RESOLVED' | 'VERIFIED' | 'IGNORED'

export interface QualitySummary {
  activeIssues: number
  criticalIssues: number
  optimizations: number
  events7d: number
  affectedUsers7d: number
  resolved7d: number
  generatedAt: string
}

export interface QualityIssue {
  issueId: string
  fingerprint: string | null
  issueType: IssueType
  source: 'TELEMETRY' | 'MANUAL'
  platform: IssuePlatform
  severity: IssueSeverity
  status: IssueStatus
  title: string
  description: string | null
  errorCode: string | null
  apiPath: string | null
  httpStatus: number | null
  release: string | null
  assignee: string | null
  resolution: string | null
  occurrenceCount: number
  affectedUsers: number
  firstSeenAt: string | null
  lastSeenAt: string | null
  resolvedAt: string | null
  createdBy: string | null
  updatedBy: string | null
  createdAt: string
  updatedAt: string
}

export interface QualityEvent {
  eventId: string
  issueId: string
  userId: string | null
  anonymousId: string | null
  sessionId: string | null
  platform: Exclude<IssuePlatform, 'CROSS_PLATFORM'>
  eventType: string
  severity: string
  release: string | null
  route: string | null
  message: string | null
  apiPath: string | null
  apiMethod: string | null
  httpStatus: number | null
  outcome: string | null
  errorCode: string | null
  errorName: string | null
  deviceModel: string | null
  osName: string | null
  osVersion: string | null
  networkType: string | null
  durationMs: number | null
  occurredAt: string
}

export interface CreateQualityIssue {
  issueType: IssueType
  platform: IssuePlatform
  severity: IssueSeverity
  status: IssueStatus
  title: string
  description?: string
  assignee?: string
}

export interface UpdateQualityIssue {
  issueType?: IssueType
  platform?: IssuePlatform
  severity?: IssueSeverity
  status?: IssueStatus
  title?: string
  description?: string
  assignee?: string
  resolution?: string
  note?: string
}

interface ApiErrorBody { error?: { message?: string } }

async function requestJson<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(buildAdminApiUrl(path), {
    credentials: 'include',
    ...init,
    headers: { 'Content-Type': 'application/json', ...(init.headers || {}) },
  })
  if (!response.ok) {
    let body: ApiErrorBody = {}
    try { body = await response.json() as ApiErrorBody } catch { body = {} }
    throw new Error(body.error?.message || '质量数据服务暂时不可用')
  }
  return response.json() as Promise<T>
}

export const getQualitySummary = () =>
  requestJson<QualitySummary>('/api/admin/quality/summary')

export async function listQualityIssues(filters: {
  status?: IssueStatus
  platform?: IssuePlatform
  issueType?: IssueType
}): Promise<QualityIssue[]> {
  const params = new URLSearchParams({ limit: '200' })
  Object.entries(filters).forEach(([key, value]) => { if (value) params.set(key, value) })
  return (await requestJson<{ issues: QualityIssue[] }>(`/api/admin/quality/issues?${params}`)).issues
}

export async function listQualityEvents(issueId: string): Promise<QualityEvent[]> {
  return (await requestJson<{ events: QualityEvent[] }>(
    `/api/admin/quality/issues/${encodeURIComponent(issueId)}/events?limit=100`,
  )).events
}

export const createQualityIssue = (body: CreateQualityIssue) =>
  requestJson<QualityIssue>('/api/admin/quality/issues', { method: 'POST', body: JSON.stringify(body) })

export const updateQualityIssue = (issueId: string, body: UpdateQualityIssue) =>
  requestJson<QualityIssue>(`/api/admin/quality/issues/${encodeURIComponent(issueId)}`, {
    method: 'PATCH', body: JSON.stringify(body),
  })
