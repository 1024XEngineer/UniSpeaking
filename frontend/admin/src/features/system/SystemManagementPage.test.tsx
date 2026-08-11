import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { ReactNode } from 'react'
import { SystemManagementPage } from './SystemManagementPage'
import { listDataSources, syncAliyunOfficialUsage } from '../governance/governanceApi'

vi.mock('../governance/governanceApi', () => ({
  listDataSources: vi.fn(),
  syncAliyunOfficialUsage: vi.fn(),
}))

function renderPage(ui: ReactNode) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>)
}

describe('SystemManagementPage', () => {
  it('shows data sources and refreshes them after an SLS sync', async () => {
    vi.mocked(listDataSources).mockResolvedValue([
      { code: 'UNIFIED_LEDGER', name: '统一练习账本', state: 'READY', detail: 'PostgreSQL 已连接' },
      { code: 'ALIYUN_SLS', name: '阿里云 SLS', state: 'READY', detail: '推理日志可查询' },
      { code: 'PROMETHEUS', name: 'Prometheus', state: 'READY', detail: '指标读取正常' },
    ])
    vi.mocked(syncAliyunOfficialUsage).mockResolvedValue({
      scanned: 18,
      accepted: 15,
      duplicate: 1,
      unbound: 0,
      rejected_context: 1,
      rejected_schema: 1,
      imported: 14,
      provider_duplicates: 1,
      matched: 12,
      unmatched: 2,
      synced_at: '2026-08-10T09:00:00Z',
    })
    const user = userEvent.setup()

    renderPage(<SystemManagementPage />)

    expect(await screen.findByText('阿里云 SLS')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '立即同步 SLS' }))

    expect(syncAliyunOfficialUsage).toHaveBeenCalledOnce()
    expect(await screen.findByText('已导入 14 条，匹配 12 条')).toBeInTheDocument()
  })

  it('simulates key dispatch and model failover without real credentials', async () => {
    vi.mocked(listDataSources).mockResolvedValue([])
    const user = userEvent.setup()

    renderPage(<SystemManagementPage />)

    expect(await screen.findAllByText('模拟策略')).toHaveLength(2)
    await user.click(screen.getByRole('button', { name: '模拟分发 Key' }))
    expect(screen.getByText(/本次分发/)).toHaveTextContent('key_mock_cn_01')

    await user.click(screen.getByRole('button', { name: '模拟故障切换' }))
    expect(screen.getByText(/已切换至备用模型/)).toHaveTextContent('qwen3-omni-flash-realtime')
  })
})
