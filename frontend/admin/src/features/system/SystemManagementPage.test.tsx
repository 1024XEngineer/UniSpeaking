import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { ReactNode } from 'react'
import { SystemManagementPage } from './SystemManagementPage'
import { getAiConfiguration, getCredentialStatus, replaceCredential, updateProvider, replaceRoute } from './systemApi'

vi.mock('./systemApi', () => ({
  getAiConfiguration: vi.fn(), updateProvider: vi.fn(), updateModel: vi.fn(),
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

function renderPage(ui: ReactNode) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>)
}

describe('SystemManagementPage', () => {
  it('shows real provider configuration and model pricing without duplicating the usage ledger', async () => {
    vi.mocked(getAiConfiguration).mockResolvedValue(configuration)
    vi.mocked(updateProvider).mockResolvedValue({ ...configuration.providers[0], enabled: false })
    const user = userEvent.setup()

    renderPage(<SystemManagementPage />)

    expect((await screen.findAllByText('通义千问')).length).toBeGreaterThan(0)
    expect(screen.getByText('数据库配置已生效')).toBeInTheDocument()
    expect(screen.getByText('deepseek-v4-flash', { selector: 'td small' })).toBeInTheDocument()
    expect(screen.getByText(/¥1 \/ ¥3/)).toBeInTheDocument()
    expect(screen.queryByText('调用与费用')).not.toBeInTheDocument()
    expect(screen.queryByText('数据源状态')).not.toBeInTheDocument()

    await user.click(screen.getByRole('checkbox', { name: '通义千问供应商状态' }))
    expect(updateProvider).toHaveBeenCalledWith('qwen', { enabled: false })
  })

  it('persists reordered failover routes', async () => {
    vi.mocked(getAiConfiguration).mockResolvedValue(configuration)
    vi.mocked(replaceRoute).mockResolvedValue({ routeKey: 'default', capability: 'LLM', modelIds: ['deepseek-v4-flash', 'qwen3.5-plus'] })
    const user = userEvent.setup()

    renderPage(<SystemManagementPage />)
    await screen.findByText('主备路由')
    await user.click(screen.getByRole('button', { name: '上移 deepseek-v4-flash' }))
    await user.click(screen.getByRole('button', { name: '保存路由' }))

    expect(replaceRoute).toHaveBeenCalledWith('LLM', ['deepseek-v4-flash', 'qwen3.5-plus'])
  })

  it('replaces a provider credential through the real admin flow', async () => {
    vi.mocked(getAiConfiguration).mockResolvedValue(configuration)
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

})
