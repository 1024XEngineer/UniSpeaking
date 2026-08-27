import { afterEach, describe, expect, it, vi } from 'vitest'
import { getMonitoringOverview, grafanaDashboards, grafanaUrl } from './monitoringApi'
import { formatDuration, formatMilliseconds, improvementRate } from './monitoringFormat'

describe('monitoringApi', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('always reloads the production monitoring overview', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ summary: {} }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    }))
    vi.stubGlobal('fetch', fetchMock)

    await getMonitoringOverview()

    expect(fetchMock).toHaveBeenCalledWith('/api/admin/monitoring/overview?range=24h', {
      credentials: 'include',
      cache: 'no-store',
    })
  })

  it('passes the selected trend range to the backend', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ summary: {} }), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    await getMonitoringOverview('7d')

    expect(fetchMock).toHaveBeenCalledWith('/api/admin/monitoring/overview?range=7d', expect.any(Object))
  })

  it('uses the dashboard UIDs provisioned by production Grafana', () => {
    expect(grafanaDashboards).toEqual({
      overview: '/d/unispeaking-overview/01-e7b3bb-e7bb9f-e680a788',
      client: '/d/unispeaking-clients/02-web-mobile',
      backend: '/d/unispeaking-backend-logs/03-e5908e-e7abaf-e4b88e-e697a5-e5bf97',
      logs: '/d/unispeaking-backend-logs/03-e5908e-e7abaf-e4b88e-e697a5-e5bf97',
      performance: '/d/unispeaking-performance/04-e680a7-e883bd-e4bc98-e58c96',
    })
    expect(grafanaUrl(grafanaDashboards.logs, { 'var-platform': 'mobile' }))
      .toContain('/d/unispeaking-backend-logs/03-e5908e-e7abaf-e4b88e-e697a5-e5bf97?')
    expect(grafanaUrl(grafanaDashboards.logs, { 'var-platform': 'mobile' }))
      .toContain('var-platform=mobile')
  })

  it('keeps sub-second endpoint timings visible in milliseconds', () => {
    expect(formatDuration(0)).toBe('0 ms')
    expect(formatDuration(0.0035)).toBe('3.5 ms')
    expect(formatDuration(0.7241)).toBe('724.1 ms')
    expect(formatDuration(15.032)).toBe('15.03 s')
    expect(formatDuration(60.2058)).toBe('60.21 s')
    expect(formatMilliseconds(724.1)).toBe('724.1 ms')
    expect(formatMilliseconds(null)).toBe('—')
    expect(improvementRate(1000, 750)).toBe(25)
    expect(improvementRate(0, 750)).toBeNull()
  })
})
