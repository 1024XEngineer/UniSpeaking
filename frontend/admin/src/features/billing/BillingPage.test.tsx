import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { listDataSources, listRealtimeSessions, syncAliyunOfficialUsage } from '../governance/governanceApi'
import { getInvocationUsage } from '../system/systemApi'
import { BillingPage } from './BillingPage'

vi.mock('../governance/governanceApi', () => ({
  listDataSources: vi.fn(),
  listRealtimeSessions: vi.fn(),
  syncAliyunOfficialUsage: vi.fn(),
}))

vi.mock('../system/systemApi', () => ({ getInvocationUsage: vi.fn() }))

const usage = {
  query: { from: '2026-08-01T00:00:00Z', to: '2026-08-19T00:00:00Z', userId: null, providerId: null, modelId: null, limit: 100 },
  recordPage: { page: 1, pageSize: 100, totalRecords: 5, totalPages: 1 },
  requestIdCoverage: { recordsWithRequestId: 3, eligibleRecords: 4 },
  summary: {
    requests: 2, attempts: 2, succeededAttempts: 2, fallbackAttempts: 0,
    inputTokens: 1200, outputTokens: 300, totalTokens: 1500,
    audioInputSeconds: 12, audioOutputSeconds: 8, averageDurationMs: 860,
    estimatedCost: 0.0234, currency: 'CNY',
  },
  byModel: [],
  byUser: [{
    userId: '00000000-0000-0000-0000-000000000002', email: 'billing-test@unispeaking.local',
    requests: 2, sessions: 1, attempts: 2, successes: 2, failures: 0, fallbackAttempts: 0,
    inputTokens: 1200, outputTokens: 300, totalTokens: 1500, inputCharacters: 18, outputCharacters: 0,
    audioInputSeconds: 12, audioOutputSeconds: 8, totalDurationMs: 1720, averageDurationMs: 860,
    estimatedCost: 0.0234, lastInvokedAt: '2026-08-18T08:00:00Z',
    models: [{
      providerId: 'qwen', modelId: 'qwen3.5-plus', capability: 'LLM', requests: 2, attempts: 2,
      successes: 2, inputTokens: 1200, outputTokens: 300, totalTokens: 1500,
      audioInputSeconds: 0, audioOutputSeconds: 0, totalDurationMs: 1720, estimatedCost: 0.0234,
    }],
  }],
  records: [
    {
      invocationId: 'invocation-1', logicalRequestId: 'logical-1', attemptNo: 1,
      userId: '00000000-0000-0000-0000-000000000002', userEmail: 'billing-test@unispeaking.local',
      sessionId: 'local-session-01', businessScene: 'realtime_session', routeKey: 'default',
      capability: 'LLM', providerId: 'qwen', modelId: 'qwen3.5-plus',
      providerRequestId: 'local-sdp-trace-01', startedAt: '2026-08-18T08:00:00Z',
      completedAt: '2026-08-18T08:00:00.860Z', durationMs: 860, firstTokenLatencyMs: 120,
      inputTokens: 1200, outputTokens: 300, totalTokens: 1500, inputCharacters: 0, outputCharacters: 0,
      audioInputSeconds: 0, audioOutputSeconds: 0, usageSource: 'PROVIDER', status: 'SUCCEEDED',
      errorCode: null, retryable: false, fallbackFromModelId: null, estimatedCost: 0.0234, priceCurrency: 'CNY',
    },
    {
      invocationId: 'invocation-2', logicalRequestId: 'logical-2', attemptNo: 1,
      userId: '00000000-0000-0000-0000-000000000002', userEmail: 'billing-test@unispeaking.local',
      sessionId: null, businessScene: 'tts', routeKey: 'default', capability: 'TTS', providerId: 'qwen',
      modelId: 'qwen3-tts-flash', providerRequestId: null, startedAt: '2026-08-18T08:01:00Z',
      completedAt: '2026-08-18T08:01:00.200Z', durationMs: 200, firstTokenLatencyMs: null,
      inputTokens: 0, outputTokens: 0, totalTokens: 0, inputCharacters: 18, outputCharacters: 0,
      audioInputSeconds: 0, audioOutputSeconds: 8, usageSource: 'PROVIDER', status: 'SUCCEEDED',
      errorCode: null, retryable: false, fallbackFromModelId: null, estimatedCost: 0, priceCurrency: 'CNY',
    },
    {
      invocationId: 'realtime-connect', logicalRequestId: 'logical-3', attemptNo: 1,
      userId: '00000000-0000-0000-0000-000000000002', userEmail: 'billing-test@unispeaking.local',
      sessionId: 'local-session-01', businessScene: 'realtime_connect', routeKey: 'default',
      capability: 'REALTIME', providerId: 'qwen', modelId: 'qwen3.5-omni-flash-realtime',
      providerRequestId: 'connect-request-visible', startedAt: '2026-08-18T08:02:00Z', completedAt: '2026-08-18T08:02:00.200Z',
      durationMs: 200, firstTokenLatencyMs: null, inputTokens: 0, outputTokens: 0, totalTokens: 0,
      inputCharacters: 0, outputCharacters: 0, audioInputSeconds: 0, audioOutputSeconds: 0,
      usageSource: 'NONE', status: 'SUCCEEDED', errorCode: null, retryable: false,
      fallbackFromModelId: null, estimatedCost: 0, priceCurrency: 'CNY',
    },
    {
      invocationId: 'realtime-unmatched', logicalRequestId: 'logical-4', attemptNo: 1,
      userId: '00000000-0000-0000-0000-000000000002', userEmail: 'billing-test@unispeaking.local',
      sessionId: 'local-session-unmatched', businessScene: 'realtime_session', routeKey: 'default',
      capability: 'REALTIME', providerId: 'qwen', modelId: 'qwen3.5-omni-flash-realtime',
      providerRequestId: 'handshake-only', startedAt: '2026-08-18T08:03:00Z', completedAt: '2026-08-18T08:03:20Z',
      durationMs: 20000, firstTokenLatencyMs: null, inputTokens: 0, outputTokens: 0, totalTokens: 0,
      inputCharacters: 0, outputCharacters: 0, audioInputSeconds: 20, audioOutputSeconds: 0,
      usageSource: 'ESTIMATED', status: 'SUCCEEDED', errorCode: null, retryable: false,
      fallbackFromModelId: null, estimatedCost: 0.002, priceCurrency: 'CNY',
    },
    {
      invocationId: 'llm-official', logicalRequestId: 'logical-5', attemptNo: 1,
      userId: '00000000-0000-0000-0000-000000000002', userEmail: 'billing-test@unispeaking.local',
      sessionId: null, businessScene: 'llm', routeKey: 'default', capability: 'LLM', providerId: 'qwen',
      modelId: 'qwen3.5-plus', providerRequestId: 'request-llm-official', startedAt: '2026-08-18T08:04:00Z',
      completedAt: '2026-08-18T08:04:03Z', durationMs: 3000, firstTokenLatencyMs: null,
      inputTokens: 970, outputTokens: 177, totalTokens: 1147, inputCharacters: 0, outputCharacters: 0,
      audioInputSeconds: 0, audioOutputSeconds: 0, usageSource: 'OFFICIAL', status: 'SUCCEEDED',
      errorCode: null, retryable: false, fallbackFromModelId: null, estimatedCost: 0.0016256, priceCurrency: 'CNY',
    },
    {
      invocationId: 'tts-official', logicalRequestId: 'logical-6', attemptNo: 1,
      userId: '00000000-0000-0000-0000-000000000002', userEmail: 'billing-test@unispeaking.local',
      sessionId: null, businessScene: 'tts', routeKey: 'default', capability: 'TTS', providerId: 'qwen',
      modelId: 'qwen3-tts-flash', providerRequestId: 'request-tts-official', startedAt: '2026-08-18T08:05:00Z',
      completedAt: '2026-08-18T08:05:01Z', durationMs: 1000, firstTokenLatencyMs: null,
      inputTokens: 0, outputTokens: 0, totalTokens: 0, inputCharacters: 51, outputCharacters: 0,
      audioInputSeconds: 0, audioOutputSeconds: 0, usageSource: 'OFFICIAL', status: 'SUCCEEDED',
      errorCode: null, retryable: false, fallbackFromModelId: null, estimatedCost: 0.00408, priceCurrency: 'CNY',
    },
    {
      invocationId: 'iflytek-billed', logicalRequestId: 'logical-7', attemptNo: 1,
      userId: '00000000-0000-0000-0000-000000000002', userEmail: 'billing-test@unispeaking.local',
      sessionId: null, businessScene: 'pronunciation_scoring', routeKey: 'default', capability: 'SCORING',
      providerId: 'iflytek', modelId: 'iflytek-suntone', providerRequestId: null,
      startedAt: '2026-08-18T08:06:00Z', completedAt: '2026-08-18T08:06:01Z', durationMs: 1000,
      firstTokenLatencyMs: null, inputTokens: 0, outputTokens: 0, totalTokens: 0, inputCharacters: 18,
      outputCharacters: 0, audioInputSeconds: 1, audioOutputSeconds: 0, usageSource: 'ESTIMATED',
      status: 'SUCCEEDED', errorCode: null, retryable: false, fallbackFromModelId: null,
      estimatedCost: 0.005, priceCurrency: 'CNY',
    },
    {
      invocationId: 'tts-cache-hit', logicalRequestId: 'logical-8', attemptNo: 1,
      userId: '00000000-0000-0000-0000-000000000002', userEmail: 'billing-test@unispeaking.local',
      sessionId: null, businessScene: 'tts', routeKey: 'default', capability: 'TTS', providerId: 'qwen',
      modelId: 'qwen3-tts-flash', providerRequestId: null, startedAt: '2026-08-18T08:07:00Z',
      completedAt: '2026-08-18T08:07:00.010Z', durationMs: 10, firstTokenLatencyMs: null,
      inputTokens: 0, outputTokens: 0, totalTokens: 0, inputCharacters: 0, outputCharacters: 0,
      audioInputSeconds: 0, audioOutputSeconds: 0, usageSource: 'NONE', status: 'SUCCEEDED',
      errorCode: null, retryable: false, fallbackFromModelId: null, estimatedCost: 0, priceCurrency: 'CNY',
    },
  ],
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={client}><BillingPage /></QueryClientProvider>)
}

describe('BillingPage', () => {
  it('aggregates users and exposes request-id matching without claiming an official bill', async () => {
    vi.mocked(getInvocationUsage).mockResolvedValue(usage)
    vi.mocked(listDataSources).mockResolvedValue([
      { code: 'POSTGRES', name: 'PostgreSQL 用户数据库', state: 'ONLINE', detail: '数据库已连接' },
      { code: 'ALIYUN_SLS', name: '阿里云 SLS', state: 'READY', detail: '官方日志可读取' },
    ])
    vi.mocked(listRealtimeSessions).mockResolvedValue([{
      session_id: 'local-session-01', user_id: '00000000-0000-0000-0000-000000000002', plan_code: 'free',
      status: 'active', measured_seconds: 81.2, remaining_seconds: 98.8, temporary_key_id: 'key-1',
      temporary_key_fingerprint: null, temporary_key_expires_at: null, task_uuid: 'sess-provider-1',
      provider_request_id: 'request-official-01', model_usage: { response_count: 0, total_tokens: 0, input_tokens: 0, output_tokens: 0 },
      official_usage: { response_count: 1, total_tokens: 793, input_tokens: 736, output_tokens: 57 },
      official_duration_ms: 51014, estimated_cost_cny: '0.0035688', pricing_status: 'estimated',
      reconciliation_status: 'MATCHED', reconciliation_reasons: [], end_reason: null,
    }])

    renderPage()

    expect(await screen.findByRole('heading', { name: '用量与计费' })).toBeInTheDocument()
    expect(await screen.findByText('billing-test@unispeaking.local')).toBeInTheDocument()
    expect(screen.getByText('¥0.023400')).toBeInTheDocument()
    expect(screen.getByText('75.0%')).toBeInTheDocument()
    expect(screen.getByText('3 / 4 条已获取')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '用户账单' })).toHaveAttribute('aria-pressed', 'true')

    await userEvent.click(screen.getByRole('button', { name: '请求明细' }))
    expect(screen.getByText('request-official-01')).toBeInTheDocument()
    expect(screen.getAllByText('官方已匹配').length).toBeGreaterThan(0)
    expect(screen.getByText('793 Token')).toBeInTheDocument()
    expect(screen.getAllByText('OFFICIAL').length).toBeGreaterThan(0)
    expect(screen.getByText('¥0.003569')).toBeInTheDocument()
    expect(screen.getAllByText('未获取 Request ID').length).toBeGreaterThan(0)
    expect(screen.queryByText('connect-request-visible')).not.toBeInTheDocument()
    expect(screen.queryByText('handshake-only')).not.toBeInTheDocument()
    expect(screen.getByText('request-llm-official')).toBeInTheDocument()
    expect(screen.getByText('request-tts-official')).toBeInTheDocument()
    expect(screen.getByText('51 字符')).toBeInTheDocument()
    expect(screen.getByText('本地已计费')).toBeInTheDocument()
    expect(screen.getByText('1 次请求')).toBeInTheDocument()
    expect(screen.getByText('缓存命中，无需 SLS')).toBeInTheDocument()
    expect(screen.getByText('缓存命中')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Realtime 会话' }))
    expect(screen.getByText('local-session-01')).toBeInTheDocument()
    expect(screen.getAllByText('进行中').length).toBeGreaterThan(0)
    expect(screen.queryByLabelText('计费开始日期')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('计费结束日期')).not.toBeInTheDocument()
  })

  it('pages request details and labels completed realtime sessions without an end reason', async () => {
    vi.mocked(getInvocationUsage).mockImplementation(async (filters) => filters.page === 2
      ? {
          ...usage,
          recordPage: { page: 2, pageSize: 1, totalRecords: 2, totalPages: 2 },
          records: [{ ...usage.records[4], invocationId: 'page-two', providerRequestId: 'request-page-two' }],
        }
      : { ...usage, recordPage: { page: 1, pageSize: 1, totalRecords: 2, totalPages: 2 }, records: [usage.records[5]] })
    vi.mocked(listDataSources).mockResolvedValue([])
    vi.mocked(listRealtimeSessions).mockResolvedValue([{
      session_id: 'completed-session', user_id: '00000000-0000-0000-0000-000000000002', plan_code: 'free',
      status: 'completed', measured_seconds: 81.2, remaining_seconds: 98.8, temporary_key_id: 'key-1',
      temporary_key_fingerprint: null, temporary_key_expires_at: null, task_uuid: 'sess-provider-2',
      provider_request_id: 'request-completed', model_usage: { response_count: 0, total_tokens: 0, input_tokens: 0, output_tokens: 0 },
      official_usage: { response_count: 1, total_tokens: 100, input_tokens: 80, output_tokens: 20 },
      official_duration_ms: 81000, estimated_cost_cny: '0.001', pricing_status: 'official',
      reconciliation_status: 'MATCHED', reconciliation_reasons: [], end_reason: null,
    }])

    renderPage()
    await userEvent.click(await screen.findByRole('button', { name: '请求明细' }))
    expect(screen.getByText('第 1 / 2 页')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '下一页' }))

    expect(await screen.findByText('request-page-two')).toBeInTheDocument()
    expect(getInvocationUsage).toHaveBeenCalledWith(expect.objectContaining({ page: 2, limit: 100 }))
    await userEvent.click(screen.getByRole('button', { name: 'Realtime 会话' }))
    expect(screen.getAllByText('已结束').length).toBeGreaterThanOrEqual(2)
  })

  it('runs manual SLS synchronization from the billing page', async () => {
    vi.mocked(getInvocationUsage).mockResolvedValue(usage)
    vi.mocked(listDataSources).mockResolvedValue([
      { code: 'ALIYUN_SLS', name: '阿里云 SLS', state: 'READY', detail: '官方日志可读取' },
    ])
    vi.mocked(listRealtimeSessions).mockResolvedValue([])
    vi.mocked(syncAliyunOfficialUsage).mockResolvedValue({
      scanned: 18, accepted: 15, duplicate: 1, unbound: 0, rejected_context: 1, rejected_schema: 1,
      imported: 14, provider_duplicates: 1, matched: 12, unmatched: 2, synced_at: '2026-08-18T08:00:00Z',
    })

    renderPage()
    await userEvent.click(await screen.findByRole('button', { name: '立即同步 SLS' }))

    expect(syncAliyunOfficialUsage).toHaveBeenCalledOnce()
    expect(await screen.findByText('已导入 14 条，匹配 12 条')).toBeInTheDocument()
  })
})
