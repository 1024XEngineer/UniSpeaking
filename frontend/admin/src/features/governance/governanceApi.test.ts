import { afterEach, describe, expect, it, vi } from 'vitest'
import { getDashboardSummary, listUsageUsers, updateUserEntitlement } from './governanceApi'
import { buildAdminApiUrl } from '../auth/authApi'

describe('governanceApi', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('loads governance data only through the authenticated Java API', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({
        total_users: 5,
        active_sessions: 1,
        quota_seconds: 900,
        used_seconds: 81.2,
        remaining_seconds: 818.8,
        client_tokens: 20780,
        official_tokens: 20786,
        estimated_cost_cny: '0.0265',
        reconciliation_pending: 0,
        reconciliation_matched: 0,
        reconciliation_mismatch: 1,
        generated_at: '2026-07-20T07:30:00Z',
      }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ users: [] }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }))
    vi.stubGlobal('fetch', fetchMock)

    const summary = await getDashboardSummary()
    const users = await listUsageUsers()

    expect(summary.official_tokens).toBe(20786)
    expect(users).toEqual([])
    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/admin/dashboard/summary', { credentials: 'include' })
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/admin/users', { credentials: 'include' })
  })

  it('updates a user entitlement through the authenticated Java API', async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify({
      user_id: 'user-01', plan_code: 'pro', plan_name: 'Pro', quota_date: '2026-08-10',
      quota_seconds: 3600, used_seconds: 81.2, status: 'active',
    }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)

    const entitlement = await updateUserEntitlement('user-01', {
      planCode: 'pro', planName: 'Pro', quotaSeconds: 3600, status: 'active',
    })

    expect(entitlement.plan_code).toBe('pro')
    expect(fetchMock).toHaveBeenCalledWith('/api/admin/users/user-01/entitlement', {
      method: 'PATCH',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ planCode: 'pro', planName: 'Pro', quotaSeconds: 3600, status: 'active' }),
    })
  })

  it('builds production requests with the backend prefix', () => {
    expect(buildAdminApiUrl('/api/admin/users', '/backend')).toBe('/backend/api/admin/users')
  })
})
