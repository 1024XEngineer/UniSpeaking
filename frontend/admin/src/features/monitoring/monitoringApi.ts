import { buildAdminApiUrl } from '../auth/authApi'

export type MonitoringRange = '1h' | '6h' | '24h' | '7d'
export interface MonitoringSummary { backendStatus: string; apiErrorRate5m: number; api5xxCount24h: number; apiP95Milliseconds24h: number; activeAlerts: number; affectedUsers24h: number; generatedAt: string }
export interface MonitoringComparison { previous: number; current: number }
export interface MonitoringGovernance { pendingIssues: number; newBugs7d: number; resolvedBugs7d: number; bugFixRate: number; errorEvents: MonitoringComparison; apiP95Milliseconds: MonitoringComparison; affectedUsers: MonitoringComparison }
export interface MonitoringProblem { problem: string; platform: string; path: string; count: number; errorRate: number | null; affectedUsers: number; lastSeen: string; status: string }
export type PerformanceStatus = 'OPTIMIZED' | 'OBSERVING' | 'PENDING' | 'REGRESSED'
export interface PerformanceEndpoint { method: string; path: string; previousPeriodP95Milliseconds: number | null; currentPeriodP95Milliseconds: number | null; improvementRate: number | null; status: PerformanceStatus }
export interface MonitoringEvent { timestamp: string; userId: string; platform: string; page: string; errorType: string; errorMessage: string; apiPath: string; httpStatus: number | null; requestId: string }
export interface PlatformSummary { platform: 'web' | 'mobile' | 'backend'; p95DurationMs: number; requestFailureRate: number; affectedUsers: number; errorCount: number }
export interface MonitoringTrendPoint { timestamp: number; clientErrors: number | null; backendErrors: number | null; slowRequests: number | null }
export interface MonitoringOverview { summary: MonitoringSummary; governance: MonitoringGovernance; problems: MonitoringProblem[]; performanceEndpoints: PerformanceEndpoint[]; recentEvents: MonitoringEvent[]; platformSummaries: PlatformSummary[]; trend: MonitoringTrendPoint[] }

export async function getMonitoringOverview(range: MonitoringRange = '24h'): Promise<MonitoringOverview> {
  const response = await fetch(buildAdminApiUrl(`/api/admin/monitoring/overview?range=${range}`), {
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
