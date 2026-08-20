import { afterEach, describe, expect, it, vi } from 'vitest'
import { updateModel } from './systemApi'

describe('systemApi', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('updates model IDs containing slashes without putting them in the URL path', async () => {
    const response = {
      modelId: 'qwen/qwen3.5-plus',
      providerId: 'qiniu-maas',
      displayName: 'Qiniu MaaS Qwen 3.5 Plus',
      capability: 'LLM' as const,
      enabled: false,
      billingUnit: 'TOKENS' as const,
      inputPricePerMillion: 0,
      outputPricePerMillion: 0,
      characterPricePerMillion: 0,
      audioInputPricePerMinute: 0,
      audioOutputPricePerMinute: 0,
      requestPricePerCall: 0,
      currency: 'CNY',
    }
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: async () => response })
    vi.stubGlobal('fetch', fetchMock)

    await updateModel(response.modelId, { enabled: false })

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/admin/ai/models?modelId=qwen%2Fqwen3.5-plus',
      expect.objectContaining({ method: 'PATCH', body: '{"enabled":false}' }),
    )
  })
})
