import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { ReactNode } from 'react'
import { SystemManagementPage } from './SystemManagementPage'
import { listDataSources, syncAliyunOfficialUsage } from '../governance/governanceApi'
import { getAiConfiguration, getCredentialStatus, getInvocationUsage, replaceCredential, updateProvider, replaceRoute } from './systemApi'

vi.mock('../governance/governanceApi', () => ({ listDataSources: vi.fn(), syncAliyunOfficialUsage: vi.fn() }))
vi.mock('./systemApi', () => ({
  getAiConfiguration: vi.fn(), getInvocationUsage: vi.fn(), updateProvider: vi.fn(), updateModel: vi.fn(),
  replaceRoute: vi.fn(), getCredentialStatus: vi.fn(), replaceCredential: vi.fn(),
}))

const configuration = {
  databaseBacked: true,
  providers: [{ providerId: 'qwen', displayName: '通义千问', adapterType: 'qwen', baseUrl: null, enabled: true, connectTimeoutMs: 10000, readTimeoutMs: 60000, configVersion: 1 }],
  models: [
    { modelId: 'qwen3.5-plus', providerId: 'qwen', displayName: 'Qwen Plus', capability: 'LLM' as const, enabled: true, billingUnit: 'TOKENS' as const, inputPricePerMillion: 1, outputPricePerMillion: 3, characterPricePerMillion: 0, audioInputPricePerMinute: 0, audioOutputPricePerMinute: 0, requestPricePerCall: 0, currency: 'CNY' },
    { modelId: 'deepseek-v4-flash', providerId: 'deepseek', displayName: 'DeepSeek', capability: 'LLM' as const, enabled: true, billingUnit: 'TOKENS' as const, inputPricePerMillion: 0.5, outputPricePerMillion: 1.5, characterPricePerMillion: 0, audioInputPricePerMinute: 0, audioOutputPricePerMinute: 0, requestPricePerCall: 0, currency: 'CNY' },
  ],
  routes: [{ routeKey: 'default', capability: 'LLM' as const, modelIds: ['qwen3.5-plus', 'deepseek-v4-flash'] }],
}

const usage = {
  query: { from: '2026-07-01T00:00:00Z', to: '2026-08-01T00:00:00Z', userId: null, providerId: null, modelId: null, limit: 200 },
  recordPage: { page: 1, pageSize: 10, totalRecords: 1, totalPages: 1 },
  summary: { requests: 2, attempts: 3, succeededAttempts: 2, fallbackAttempts: 1, inputTokens: 1000, outputTokens: 200, totalTokens: 1200, audioInputSeconds: 0, audioOutputSeconds: 0, averageDurationMs: 540, estimatedCost: 0.0016, currency: 'CNY' },
  byModel: [],
  byUser: [{
    userId: 'user-1', email: 'learner@example.com', requests: 1, sessions: 1, attempts: 1,
    successes: 1, failures: 0, fallbackAttempts: 1, inputTokens: 1000, outputTokens: 200,
    totalTokens: 1200, inputCharacters: 0, outputCharacters: 0, audioInputSeconds: 0,
    audioOutputSeconds: 0, totalDurationMs: 540, averageDurationMs: 540, estimatedCost: 0.0016,
    lastInvokedAt: '2026-07-20T09:00:00Z',
    models: [{ providerId: 'deepseek', modelId: 'deepseek-v4-flash', capability: 'LLM', requests: 1, attempts: 1, successes: 1, inputTokens: 1000, outputTokens: 200, totalTokens: 1200, audioInputSeconds: 0, audioOutputSeconds: 0, totalDurationMs: 540, estimatedCost: 0.0016 }],
  }],
  records: [{ invocationId: 'inv-1', logicalRequestId: 'req-1', attemptNo: 2, userId: 'user-1', userEmail: 'learner@example.com', sessionId: 'session-1', businessScene: 'dialogue_report', routeKey: 'default', capability: 'LLM', providerId: 'deepseek', modelId: 'deepseek-v4-flash', providerRequestId: 'vendor-1', startedAt: '2026-07-20T09:00:00Z', completedAt: '2026-07-20T09:00:00.540Z', durationMs: 540, firstTokenLatencyMs: 120, inputTokens: 1000, outputTokens: 200, totalTokens: 1200, inputCharacters: 0, outputCharacters: 0, audioInputSeconds: 0, audioOutputSeconds: 0, usageSource: 'PROVIDER', status: 'SUCCEEDED', errorCode: null, retryable: false, fallbackFromModelId: 'qwen3.5-plus', estimatedCost: 0.0016, priceCurrency: 'CNY' }],
}

function renderPage(ui: ReactNode) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>)
}

describe('SystemManagementPage', () => {
  it('includes the complete selected end date in usage requests', async () => {
    vi.mocked(listDataSources).mockResolvedValue([])
    vi.mocked(getAiConfiguration).mockResolvedValue(configuration)
    vi.mocked(getInvocationUsage).mockResolvedValue(usage)

    renderPage(<SystemManagementPage />)

    const selectedEndDate = (screen.getByLabelText('结束日期') as HTMLInputElement).value
    const expectedExclusiveEnd = new Date(`${selectedEndDate}T00:00:00`)
    expectedExclusiveEnd.setDate(expectedExclusiveEnd.getDate() + 1)
    await waitFor(() => expect(getInvocationUsage).toHaveBeenCalledWith(
      expect.objectContaining({ to: expectedExclusiveEnd.toISOString() }),
    ))
  })

  it('shows real provider configuration and usage ledger', async () => {
    vi.mocked(listDataSources).mockResolvedValue([])
    vi.mocked(getAiConfiguration).mockResolvedValue(configuration)
    vi.mocked(getInvocationUsage).mockResolvedValue(usage)
    vi.mocked(updateProvider).mockResolvedValue({ ...configuration.providers[0], enabled: false })
    const user = userEvent.setup()

    renderPage(<SystemManagementPage />)

    expect((await screen.findAllByText('通义千问')).length).toBeGreaterThan(0)
    expect(screen.getByText('数据库配置已生效')).toBeInTheDocument()
    expect(screen.getByText('deepseek-v4-flash', { selector: 'td small' })).toBeInTheDocument()
    expect(screen.getAllByText('¥0.0016')).toHaveLength(2)

    await user.click(screen.getByRole('tab', { name: '用户消耗' }))
    expect(screen.getByText('learner@example.com')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /learner@example.com/ }))
    expect(screen.getByText('1,200 Token', { selector: '.user-model-detail td strong' })).toBeInTheDocument()

    await user.click(screen.getByRole('checkbox', { name: '通义千问供应商状态' }))
    expect(updateProvider).toHaveBeenCalledWith('qwen', { enabled: false })
  })

  it('persists reordered failover routes', async () => {
    vi.mocked(listDataSources).mockResolvedValue([])
    vi.mocked(getAiConfiguration).mockResolvedValue(configuration)
    vi.mocked(getInvocationUsage).mockResolvedValue(usage)
    vi.mocked(replaceRoute).mockResolvedValue({ routeKey: 'default', capability: 'LLM', modelIds: ['deepseek-v4-flash', 'qwen3.5-plus'] })
    const user = userEvent.setup()

    renderPage(<SystemManagementPage />)
    await screen.findByText('主备路由')
    await user.click(screen.getByRole('button', { name: '上移 deepseek-v4-flash' }))
    await user.click(screen.getByRole('button', { name: '保存路由' }))

    expect(replaceRoute).toHaveBeenCalledWith('LLM', ['deepseek-v4-flash', 'qwen3.5-plus'])
  })

  it('loads invocation details ten records at a time', async () => {
    vi.mocked(listDataSources).mockResolvedValue([])
    vi.mocked(getAiConfiguration).mockResolvedValue(configuration)
    vi.mocked(getInvocationUsage).mockImplementation(async (filters) => filters.page === 2
      ? {
          ...usage,
          recordPage: { page: 2, pageSize: 10, totalRecords: 11, totalPages: 2 },
          records: [{
            ...usage.records[0], invocationId: 'inv-11', businessScene: 'second_page_scene',
            inputTokens: 0, outputTokens: 0, totalTokens: 0, inputCharacters: 0, outputCharacters: 0,
          }],
        }
      : { ...usage, recordPage: { page: 1, pageSize: 10, totalRecords: 11, totalPages: 2 } })
    const user = userEvent.setup()

    renderPage(<SystemManagementPage />)
    await user.click(await screen.findByRole('tab', { name: '调用明细' }))
    expect(screen.getByText('第 1 / 2 页')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '下一页' }))

    await waitFor(() => expect(getInvocationUsage).toHaveBeenCalledWith(expect.objectContaining({ page: 2, limit: 10 })))
    expect(await screen.findByText('second page scene')).toBeInTheDocument()
    expect(screen.getByText('第 2 / 2 页')).toBeInTheDocument()
    expect(screen.getByText('/', { selector: '.usage-token-value' })).toBeInTheDocument()
    expect(screen.queryByText('vendor-1')).not.toBeInTheDocument()
    expect(screen.queryByText('session-1')).not.toBeInTheDocument()
  })

  it('replaces a provider credential through the real admin flow', async () => {
    vi.mocked(listDataSources).mockResolvedValue([])
    vi.mocked(getAiConfiguration).mockResolvedValue(configuration)
    vi.mocked(getInvocationUsage).mockResolvedValue(usage)
    vi.mocked(getCredentialStatus)
      .mockResolvedValueOnce({ configured: false, fingerprint: null, writable: true })
      .mockResolvedValue({ configured: true, fingerprint: 'sha256:123456789abc', writable: true })
    vi.mocked(replaceCredential).mockResolvedValue({ configured: true, fingerprint: 'sha256:123456789abc', writable: true })
    const user = userEvent.setup()

    renderPage(<SystemManagementPage />)
    await user.click(await screen.findByRole('button', { name: '管理密钥' }))
    await user.type(screen.getByLabelText('新密钥'), 'provider-secret-value')
    await user.click(screen.getByRole('button', { name: '更新密钥' }))

    await waitFor(() => expect(replaceCredential).toHaveBeenCalledWith('qwen', 'provider-secret-value'))
    expect(await screen.findByText('密钥已更新，后续调用立即生效。')).toBeInTheDocument()
    expect(await screen.findByText('sha256:123456789abc')).toBeInTheDocument()
  })

  it('keeps official SLS synchronization available', async () => {
    vi.mocked(listDataSources).mockResolvedValue([{ code: 'ALIYUN_SLS', name: '阿里云 SLS', state: 'READY', detail: '推理日志可查询' }])
    vi.mocked(getAiConfiguration).mockResolvedValue(configuration)
    vi.mocked(getInvocationUsage).mockResolvedValue(usage)
    vi.mocked(syncAliyunOfficialUsage).mockResolvedValue({ scanned: 18, accepted: 15, duplicate: 1, unbound: 0, rejected_context: 1, rejected_schema: 1, imported: 14, provider_duplicates: 1, matched: 12, unmatched: 2, synced_at: '2026-08-10T09:00:00Z' })

    renderPage(<SystemManagementPage />)
    await userEvent.click(await screen.findByRole('button', { name: '立即同步 SLS' }))
    expect(syncAliyunOfficialUsage).toHaveBeenCalledOnce()
    expect(await screen.findByText('已导入 14 条，匹配 12 条')).toBeInTheDocument()
  })
})
