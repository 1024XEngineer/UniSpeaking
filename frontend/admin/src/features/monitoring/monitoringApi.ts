import { buildAdminApiUrl } from '../auth/authApi'

export interface MonitoringSummary { backendStatus: string; clientErrorRate: number; api5xxRate: number; apiP95Seconds: number; activeAlerts: number; affectedUsers: number; completedOptimizations: number; resolvedBugs7d: number; generatedAt: string }
export interface MonitoringProblem { problem: string; platform: string; path: string; count: number; affectedUsers: number; lastSeen: string; status: string }
export interface SlowEndpoint { method: string; path: string; calls: number; averageSeconds: number; p95Seconds: number; maxSeconds: number; slowCount: number }
export interface MonitoringEvent { timestamp: string; userId: string; platform: string; page: string; errorType: string; errorMessage: string; apiPath: string; httpStatus: number | null; requestId: string }
export interface PlatformSummary { platform: 'web' | 'mobile' | 'backend'; p95DurationMs: number; requestFailureRate: number; affectedUsers: number; errorCount: number }
export interface MonitoringTrendPoint { timestamp: number; clientErrors: number; slowRequests: number; backendErrors: number }
export interface MonitoringOverview { summary: MonitoringSummary; problems: MonitoringProblem[]; slowEndpoints: SlowEndpoint[]; recentEvents: MonitoringEvent[]; platformSummaries: PlatformSummary[]; trend: MonitoringTrendPoint[] }

export async function getMonitoringOverview(): Promise<MonitoringOverview> {
  const response = await fetch(buildAdminApiUrl('/api/admin/monitoring/overview'), {
    credentials: 'include',
    cache: 'no-store',
  })
  if (!response.ok) throw new Error('运行监控数据暂时无法读取')
  return response.json() as Promise<MonitoringOverview>
}

export function grafanaUrl(path: string, params: Record<string, string> = {}) {
  const base = (import.meta.env.VITE_GRAFANA_URL || 'http://218.11.5.202:3001').replace(/\/$/, '')
  const query = new URLSearchParams({ orgId: '1', from: 'now-24h', to: 'now', timezone: 'browser', refresh: '15s', ...params })
  return `${base}${path}${query.size ? `?${query.toString()}` : ''}`
}

export const grafanaDashboards = {
  overview: '/d/unispeaking-overview/01-e7b3bb-e7bb9f-e680a788',
  client: '/d/unispeaking-clients/02-web-mobile',
  backend: '/d/unispeaking-backend-logs/03-e5908e-e7abaf-e4b88e-e697a5-e5bf97',
  logs: '/d/unispeaking-backend-logs/03-e5908e-e7abaf-e4b88e-e697a5-e5bf97',
  performance: '/d/unispeaking-performance/04-e680a7-e883bd-e4bc98-e58c96',
}
