import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { ReactNode } from 'react'
import { SystemManagementPage } from './SystemManagementPage'
import { getAiConfiguration, getCredentialStatus, replaceCredential, updateProvider, replaceRoute, type CredentialStatus, type ProviderView } from './systemApi'

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

const qwenCredentialStatus: CredentialStatus = {
  configured: false, fingerprint: null, writable: true,
  fields: [
    { key: 'apiKey', label: 'API Key', required: true, secret: true, description: '百炼 / DashScope API Key', configured: false, fingerprint: null },
    { key: 'workspaceId', label: 'Workspace ID', required: false, secret: false, description: '部分地域的百炼接口需要配置', configured: false, fingerprint: null },
  ],
}

function configurationFor(provider: ProviderView) {
  return { ...configuration, providers: [provider] }
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

	it('keeps the provider switch and label synchronized while disabling it', async () => {
		const disabledProvider = { ...configuration.providers[0], enabled: false, configVersion: 2 }
		vi.mocked(getAiConfiguration)
			.mockResolvedValueOnce(configuration)
			.mockResolvedValue(configurationFor(disabledProvider))
		let finishUpdate: ((provider: ProviderView) => void) | undefined
		vi.mocked(updateProvider).mockReturnValue(new Promise((resolve) => { finishUpdate = resolve }))
		const user = userEvent.setup()

		renderPage(<SystemManagementPage />)
		const toggle = await screen.findByRole('checkbox', { name: '通义千问供应商状态' })
		await user.click(toggle)

		expect(toggle).not.toBeChecked()
		expect(screen.getByText('已停用')).toBeInTheDocument()
		finishUpdate?.(disabledProvider)
		expect(await screen.findByText('通义千问已停用，运行时路由已刷新。')).toBeInTheDocument()
		await waitFor(() => expect(toggle).not.toBeChecked())
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

  it('updates the complete Qwen credential form through the real admin flow', async () => {
    vi.mocked(getAiConfiguration).mockResolvedValue(configuration)
    vi.mocked(getCredentialStatus)
      .mockResolvedValueOnce(qwenCredentialStatus)
      .mockResolvedValue({ ...qwenCredentialStatus, configured: true, fingerprint: 'sha256:123456789abc', fields: qwenCredentialStatus.fields.map((field) => ({ ...field, configured: true, fingerprint: 'sha256:123456789abc' })) })
    vi.mocked(replaceCredential).mockResolvedValue({ ...qwenCredentialStatus, configured: true, fingerprint: 'sha256:123456789abc' })
    const user = userEvent.setup()

    renderPage(<SystemManagementPage />)
    await user.click(await screen.findByRole('button', { name: '管理密钥' }))
    await user.type(await screen.findByLabelText('API Key'), 'provider-secret-value')
    await user.type(screen.getByLabelText('Workspace ID'), 'workspace-123')
    await user.click(screen.getByRole('button', { name: '保存配置' }))

    await waitFor(() => expect(replaceCredential).toHaveBeenCalledWith('qwen', { apiKey: 'provider-secret-value', workspaceId: 'workspace-123' }))
    expect(await screen.findByText('凭据配置已更新。')).toBeInTheDocument()
    expect((await screen.findAllByText('sha256:123456789abc')).length).toBeGreaterThan(0)
  })

  it('shows the credential fields required by Aliyun CosyVoice', async () => {
    const provider = { ...configuration.providers[0], providerId: 'aliyun', adapterType: 'aliyun', displayName: '阿里云语音' }
    vi.mocked(getAiConfiguration).mockResolvedValue(configurationFor(provider))
    vi.mocked(getCredentialStatus).mockResolvedValue({
      configured: false, fingerprint: null, writable: true,
      fields: [
		{ key: 'apiKey', label: 'API Key', required: true, secret: true, description: '百炼密钥', configured: false, fingerprint: null },
		{ key: 'workspaceId', label: 'Workspace ID', required: true, secret: false, description: '业务空间', configured: false, fingerprint: null },
		{ key: 'region', label: 'Region', required: false, secret: false, description: '地域', configured: false, fingerprint: null },
      ],
    })
    const user = userEvent.setup()

    renderPage(<SystemManagementPage />)
    await user.click(await screen.findByRole('button', { name: '管理密钥' }))

		expect(await screen.findByLabelText('API Key')).toBeInTheDocument()
		expect(screen.getByLabelText('Workspace ID')).toBeInTheDocument()
		expect(screen.getByLabelText('Region')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '保存配置' })).toBeDisabled()
  })

  it('keeps Qiniu RTI and MaaS credential fields distinct', async () => {
    const qiniu = { ...configuration.providers[0], providerId: 'qiniu', adapterType: 'qiniu', displayName: '七牛云 RTI' }
    const qiniuMaas = { ...configuration.providers[0], providerId: 'qiniu-maas', adapterType: 'qiniu-maas', displayName: '七牛云 MaaS' }
    vi.mocked(getAiConfiguration).mockResolvedValue({ ...configuration, providers: [qiniu, qiniuMaas] })
    vi.mocked(getCredentialStatus).mockImplementation(async (providerId) => providerId === 'qiniu' ? {
      configured: false, fingerprint: null, writable: true,
      fields: [
        { key: 'accessKey', label: 'Access Key', required: true, secret: true, description: 'RTI AK', configured: false, fingerprint: null },
        { key: 'secretKey', label: 'Secret Key', required: true, secret: true, description: 'RTI SK', configured: false, fingerprint: null },
        { key: 'appId', label: 'App ID', required: true, secret: false, description: 'RTI App', configured: false, fingerprint: null },
      ],
    } : {
      configured: false, fingerprint: null, writable: true,
      fields: [{ key: 'apiKey', label: 'API Key', required: true, secret: true, description: 'MaaS Key', configured: false, fingerprint: null }],
    })
    const user = userEvent.setup()

    renderPage(<SystemManagementPage />)
    const buttons = await screen.findAllByRole('button', { name: '管理密钥' })
    await user.click(buttons[0])
    expect(await screen.findByLabelText('Access Key')).toBeInTheDocument()
    expect(screen.getByLabelText('Secret Key')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '关闭凭据设置' }))
    await user.click(buttons[1])
    expect(await screen.findByLabelText('API Key')).toBeInTheDocument()
    expect(screen.queryByLabelText('Secret Key')).not.toBeInTheDocument()
  })

})
