import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { OverviewPage } from '../overview/OverviewPage'
import { MonitoringPage } from './MonitoringPage'
import { ReconciliationPage } from './ReconciliationPage'
import { UsersPage } from './UsersPage'
import {
  getDashboardSummary,
  listRealtimeSessions,
  listReconciliationRecords,
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

  it('shows session identity chain and official reconciliation', async () => {
    vi.mocked(listRealtimeSessions).mockResolvedValue([session])
    vi.mocked(listReconciliationRecords).mockResolvedValue([{
      user_id: 'user-01', session_id: 'local-session-01', temporary_key_id: 'key-6124876',
      task_uuid: 'sess_41tAWSq7xIR1b2EDelEgS', request_id: '3131bedf-7956-91c3-90fe-27cbcb3dfbcf',
      client_tokens: 13200, official_tokens: 13289,
      client_usage: { response_count: 1, total_tokens: 13200, input_tokens: 12850, output_tokens: 350, input_text_tokens: 12760, input_audio_tokens: 90, output_text_tokens: 84, output_audio_tokens: 266 },
      official_usage: { response_count: 1, total_tokens: 13289, input_tokens: 12938, output_tokens: 351, input_text_tokens: 12840, input_audio_tokens: 98, output_text_tokens: 85, output_audio_tokens: 266 },
      official_duration_ms: 50962, estimated_cost_cny: '0.026500',
      status: 'MISMATCH', reasons: ['client_official_tokens_differ'],
    }])

    renderPage(<MonitoringPage />)
    expect(await screen.findByText('sess_provider_01')).toBeInTheDocument()
    expect(screen.getByText('key-6124876')).toBeInTheDocument()

    cleanup()
    renderPage(<ReconciliationPage />)
    expect(await screen.findByText('sess_41tA…EgS')).toBeInTheDocument()
    expect(screen.getByText('3131bedf…fbcf')).toBeInTheDocument()
    expect(screen.queryByText('3131bedf-7956-91c3-90fe-27cbcb3dfbcf')).not.toBeInTheDocument()
    expect(screen.getByText('输入 12,938')).toBeInTheDocument()
    expect(screen.getByText('输出 351')).toBeInTheDocument()
    expect(screen.getByText('存在差异')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: '查看详情' }))
    expect(screen.getByText('3131bedf-7956-91c3-90fe-27cbcb3dfbcf')).toBeInTheDocument()
    expect(screen.getByText(/输入：文本 12,840 · 音频 98/)).toBeInTheDocument()
  })

  it('counts backend realtime statuses as active connections', async () => {
    vi.mocked(listRealtimeSessions).mockResolvedValue([
      { ...session, session_id: 'waiting-session', status: 'waiting_client' },
      { ...session, session_id: 'active-session', status: 'active' },
    ])

    renderPage(<MonitoringPage />)

    expect(await screen.findByText('2 个活跃连接')).toBeInTheDocument()
    expect(screen.getByText(/等待客户端/)).toBeInTheDocument()
    expect(screen.getByText(/进行中/)).toBeInTheDocument()
  })
})
