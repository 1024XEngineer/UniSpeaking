import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { OverviewPage } from '../overview/OverviewPage'
import { UsersPage } from './UsersPage'
import {
  getDashboardSummary,
  listUsageUsers,
  updateUserEntitlement,
} from './governanceApi'

vi.mock('./governanceApi', () => ({
  getDashboardSummary: vi.fn(),
  listDataSources: vi.fn(),
  listRealtimeSessions: vi.fn(),
  listReconciliationRecords: vi.fn(),
  listUsageUsers: vi.fn(),
  updateUserEntitlement: vi.fn(),
  syncAliyunOfficialUsage: vi.fn(),
}))

const session = {
  session_id: 'local-session-01', user_id: 'user-01', plan_code: 'free', status: 'ended',
  measured_seconds: 81.215, remaining_seconds: 98.785, temporary_key_id: 'key-6124876',
  temporary_key_fingerprint: 'abc123', temporary_key_expires_at: 1784092500,
  task_uuid: 'sess_provider_01', provider_request_id: 'request-official-01',
  model_usage: { response_count: 1, total_tokens: 20780, input_tokens: 20040, output_tokens: 740 },
  official_usage: { response_count: 1, total_tokens: 20786, input_tokens: 20043, output_tokens: 743 },
  official_duration_ms: 81215, estimated_cost_cny: '0.026500', pricing_status: 'priced',
  reconciliation_status: 'MISMATCH', reconciliation_reasons: ['client_official_tokens_differ'], end_reason: 'user_end',
}

const user = {
  user_id: 'user-01', display_name: 'User 01', plan_code: 'free', plan_name: 'Free', quota_date: '2026-07-20',
  status: 'active' as const, quota_seconds: 180, settled_seconds: 81.215, active_elapsed_seconds: 0, used_seconds: 81.215,
  remaining_seconds: 98.785, reset_at: 1784563200, active_session_id: null, session_count: 1,
  sessions: [session], model_usage: session.model_usage, official_usage: session.official_usage,
  estimated_cost_cny: '0.026500', reconciliation_counts: { PENDING: 0, MATCHED: 0, MISMATCH: 1 },
}

function renderPage(ui: React.ReactNode) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>)
}

describe('governance pages', () => {
  it('shows live summary and independent user quota', async () => {
    vi.mocked(getDashboardSummary).mockResolvedValue({
      total_users: 1, active_sessions: 0, quota_seconds: 180, used_seconds: 81.215,
      remaining_seconds: 98.785, client_tokens: 20780, official_tokens: 20786,
      estimated_cost_cny: '0.026500', reconciliation_pending: 0, reconciliation_matched: 0,
      reconciliation_mismatch: 1, generated_at: '2026-07-20T07:30:00Z',
    })
    vi.mocked(listUsageUsers).mockResolvedValue([user])

    renderPage(<OverviewPage />)
    expect(await screen.findByText('81.2 秒')).toBeInTheDocument()
    expect(screen.getByText('¥0.03')).toBeInTheDocument()

    cleanup()
    renderPage(<UsersPage />)
    expect(await screen.findByText('User 01')).toBeInTheDocument()
    expect(screen.getByText('98.8 秒')).toBeInTheDocument()
    expect(screen.getByText('¥0.03')).toBeInTheDocument()

    vi.mocked(updateUserEntitlement).mockResolvedValue({
      user_id: 'user-01', plan_code: 'pro', plan_name: 'Pro', quota_date: '2026-07-20',
      quota_seconds: 3600, used_seconds: 81.215, status: 'active',
    })
    await userEvent.click(screen.getByRole('button', { name: '编辑权限' }))
    const dialog = screen.getByRole('dialog', { name: '编辑用户额度' })
    expect(dialog).toBeInTheDocument()
    expect(within(dialog).getByText('User 01')).toBeInTheDocument()
    expect(screen.getByLabelText('套餐编码')).toHaveValue('free')
    expect(screen.queryByText('保存权限', { selector: 'td *' })).not.toBeInTheDocument()
    await userEvent.clear(screen.getByLabelText('套餐编码'))
    await userEvent.type(screen.getByLabelText('套餐编码'), 'pro')
    await userEvent.click(screen.getByRole('button', { name: '保存权限' }))
    expect(updateUserEntitlement).toHaveBeenCalledWith('user-01', {
      planCode: 'pro', planName: 'Free', quotaSeconds: 180, status: 'active',
    })
    expect(await screen.findByText('权限已更新')).toBeInTheDocument()
  })

  it('closes entitlement editor without changing data when cancelled', async () => {
    vi.mocked(listUsageUsers).mockResolvedValue([user])
    vi.mocked(updateUserEntitlement).mockClear()

    renderPage(<UsersPage />)
    await userEvent.click(await screen.findByRole('button', { name: '编辑权限' }))
    expect(screen.getByRole('dialog', { name: '编辑用户额度' })).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: '取消' }))

    expect(screen.queryByRole('dialog', { name: '编辑用户额度' })).not.toBeInTheDocument()
    expect(updateUserEntitlement).not.toHaveBeenCalled()
  })

})
