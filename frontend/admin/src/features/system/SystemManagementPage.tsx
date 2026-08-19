import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  ArrowDown, ArrowUp, Bot, BrainCircuit, KeyRound, Mic2, Radio, RefreshCw,
  Save, Sparkles, Volume2, X,
  type LucideIcon,
} from 'lucide-react'
import { useEffect, useState } from 'react'
import {
  getAiConfiguration, getCredentialStatus, replaceCredential, replaceRoute,
  updateModel, updateProvider, type AiCapability, type ModelView, type ProviderView,
} from './systemApi'

const capabilityLabels: Record<AiCapability, string> = { REALTIME: '实时对话', LLM: '文本模型', SCORING: '发音评分', TTS: '语音合成', TRANSCRIPTION: '语音识别' }
const billingLabels: Record<ModelView['billingUnit'], string> = { TOKENS: 'Token', AUDIO_MINUTES: '音频分钟', CHARACTERS: '字符', REQUESTS: '请求', MIXED: '混合' }
const capabilityIcons: Record<AiCapability, LucideIcon> = { REALTIME: Radio, LLM: BrainCircuit, SCORING: Sparkles, TTS: Volume2, TRANSCRIPTION: Mic2 }
export function SystemManagementPage() {
  const queryClient = useQueryClient()
  const configuration = useQuery({ queryKey: ['ai', 'configuration'], queryFn: getAiConfiguration, refetchInterval: 15_000 })
  const refreshConfiguration = async () => queryClient.invalidateQueries({ queryKey: ['ai', 'configuration'] })
  const refreshAll = async () => configuration.refetch()
  const lastUpdatedAt = configuration.dataUpdatedAt

  return <div className="system-page system-console">
    <header className="system-console__heading">
      <div>
        <p className="system-breadcrumb">系统管理 <span>/</span> 模型供应商与费用</p>
        <h1>模型供应商与费用</h1>
        <p>统一管理供应商、模型价格与路由策略；用户账单及官方日志对账在“用量与计费”中查看。</p>
      </div>
      <div className="system-console__freshness">
        <span className={`compact-state compact-state--${configuration.data?.databaseBacked ? 'ok' : 'danger'}`}><i />{configuration.data?.databaseBacked ? '数据库配置已生效' : '应急配置模式'}</span>
        <span>{lastUpdatedAt ? `更新于 ${new Date(lastUpdatedAt).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })}` : '正在更新数据'}</span>
        <button className="icon-control" type="button" aria-label="刷新全部数据" title="刷新全部数据" onClick={() => void refreshAll()} disabled={configuration.isFetching}>
          <RefreshCw size={16} />
        </button>
      </div>
    </header>

    <div className="configuration-grid">
      <section className="system-block provider-block">
        <BlockHeader title="供应商" count={`${configuration.data?.providers.length ?? 0} 个供应商`} />
        {configuration.isLoading && <PanelMessage title="正在读取供应商" detail="从数据库加载当前生效配置。" />}
        {configuration.isError && <PanelMessage title="供应商配置读取失败" detail="数据库配置不可用，后端将使用环境变量应急路由。" tone="danger" />}
        {configuration.data && <ProviderTable providers={configuration.data.providers} onChanged={refreshConfiguration} />}
      </section>

      <section className="system-block model-block">
        <BlockHeader title="模型与价格" count={`${configuration.data?.models.length ?? 0} 个模型`} suffix="价格在调用时固化" />
        {configuration.data && <ModelTable models={configuration.data.models} onChanged={refreshConfiguration} />}
      </section>
    </div>

    {configuration.data && <RouteEditor models={configuration.data.models} routes={configuration.data.routes} onChanged={refreshConfiguration} />}
  </div>
}

function BlockHeader({ title, count, suffix, action }: { title: string; count?: string; suffix?: string; action?: React.ReactNode }) {
  return <header className="block-heading">
    <div><h2>{title}</h2>{count && <span>{count}</span>}{suffix && <small>{suffix}</small>}</div>
    {action}
  </header>
}


function ProviderTable({ providers, onChanged }: { providers: ProviderView[]; onChanged: () => Promise<unknown> }) {
  const [credentialProvider, setCredentialProvider] = useState<ProviderView | null>(null)
  const mutation = useMutation({ mutationFn: ({ provider, enabled }: { provider: ProviderView; enabled: boolean }) => updateProvider(provider.providerId, { enabled }), onSuccess: onChanged })
  return <>
    <div className="compact-table-scroll"><table className="compact-table provider-table"><thead><tr><th>供应商</th><th>渠道 ID</th><th>版本</th><th>状态</th><th>操作</th></tr></thead><tbody>{providers.map((provider) => <tr key={provider.providerId}>
      <td><span className="provider-identity"><span className={`provider-logo provider-logo--${provider.providerId}`}><Bot size={14} /></span><strong>{provider.displayName}</strong></span></td>
      <td><code>{provider.adapterType}</code></td>
      <td>v{provider.configVersion}</td>
      <td><label className="compact-toggle"><input aria-label={`${provider.displayName}供应商状态`} type="checkbox" checked={provider.enabled} disabled={mutation.isPending} onChange={(event) => mutation.mutate({ provider, enabled: event.target.checked })} /><span><i />{provider.enabled ? '已启用' : '已停用'}</span></label></td>
      <td><button className="table-action" type="button" aria-label="管理密钥" onClick={() => setCredentialProvider(provider)}><KeyRound size={13} />密钥</button></td>
    </tr>)}</tbody></table></div>
    {credentialProvider && <CredentialDialog provider={credentialProvider} onClose={() => setCredentialProvider(null)} />}
  </>
}

function CredentialDialog({ provider, onClose }: { provider: ProviderView; onClose: () => void }) {
  const status = useQuery({ queryKey: ['ai', 'credential', provider.providerId], queryFn: () => getCredentialStatus(provider.providerId) })
  const [values, setValues] = useState<Record<string, string>>({})
  const changedValues = Object.fromEntries(Object.entries(values).filter(([, value]) => value.trim()))
  const missingRequired = status.data?.fields.some((field) => field.required && !field.configured && !values[field.key]?.trim()) ?? true
  const mutation = useMutation({
    mutationFn: () => replaceCredential(provider.providerId, changedValues),
    onSuccess: () => { setValues({}); void status.refetch() },
  })
  const updateValue = (key: string, value: string) => {
    mutation.reset()
    setValues((current) => ({ ...current, [key]: value }))
  }
  return <div className="modal-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose() }}><section className="entitlement-dialog credential-dialog" role="dialog" aria-modal="true" aria-labelledby="credential-title">
    <header className="entitlement-dialog__header"><div><p className="eyebrow">PROVIDER CONFIGURATION</p><h2 id="credential-title">{provider.displayName} 凭据配置</h2><p className="entitlement-dialog__identity">所有字段加密保存，页面不回显原值<span>{status.data?.fingerprint || '尚未配置'}</span></p></div><button className="modal-close" type="button" aria-label="关闭凭据设置" onClick={onClose}><X size={18} /></button></header>
    <div className="credential-form">
      {status.isLoading && <p className="credential-form__loading">正在读取配置项…</p>}
      {status.isError && <p className="form-error">配置项读取失败，请稍后重试。</p>}
      {status.data?.fields.map((field) => <label key={field.key} className="credential-field">
        <span className="credential-field__label">{field.label}{field.required ? <i>必填</i> : <i className="optional">选填</i>}</span>
        <input
          aria-label={field.label}
          type={field.secret ? 'password' : 'text'}
          autoComplete={field.secret ? 'new-password' : 'off'}
          value={values[field.key] || ''}
          onChange={(event) => updateValue(field.key, event.target.value)}
          placeholder={field.configured ? '已配置，留空则保留当前值' : `请输入 ${field.label}`}
        />
        <small className="credential-field__meta"><span>{field.description}</span><code>{field.configured ? field.fingerprint : '未配置'}</code></small>
      </label>)}
      {status.data && !status.data.writable && <p className="form-error">服务器尚未配置凭据加密主密钥，当前只能使用环境变量。</p>}
      {mutation.isSuccess && <p className="form-success" role="status">凭据配置已更新。</p>}
      {mutation.isError && <p className="form-error">{mutation.error.message}</p>}
    </div>
    <footer className="entitlement-dialog__footer"><span /><div className="entitlement-dialog__actions"><button className="quiet-button" type="button" onClick={onClose}>取消</button><button className="primary-button" type="button" disabled={!status.data?.writable || missingRequired || Object.keys(changedValues).length === 0 || mutation.isPending} onClick={() => mutation.mutate()}>{mutation.isPending ? '更新中…' : '保存配置'}</button></div></footer>
  </section></div>
}

function ModelTable({ models, onChanged }: { models: ModelView[]; onChanged: () => Promise<unknown> }) {
  const [editing, setEditing] = useState<ModelView | null>(null)
  const mutation = useMutation({ mutationFn: ({ model, enabled }: { model: ModelView; enabled: boolean }) => updateModel(model.modelId, { enabled }), onSuccess: onChanged })
  return <>
    <div className="compact-table-scroll compact-table-scroll--models"><table className="compact-table model-table"><thead><tr><th>模型</th><th>类型</th><th>计费单位</th><th>价格</th><th>状态</th><th>操作</th></tr></thead><tbody>{models.map((model) => <tr key={model.modelId}>
      <td><strong>{model.displayName}</strong><small>{model.modelId}</small></td>
      <td><span className="model-type">{capabilityLabels[model.capability]}</span></td>
      <td>{billingLabels[model.billingUnit]}</td>
      <td><PriceSummary model={model} /></td>
      <td><label className="compact-toggle"><input aria-label={`${model.displayName}模型状态`} type="checkbox" checked={model.enabled} disabled={mutation.isPending} onChange={(event) => mutation.mutate({ model, enabled: event.target.checked })} /><span><i />{model.enabled ? '已启用' : '已停用'}</span></label></td>
      <td><button className="table-action" type="button" onClick={() => setEditing(model)}>编辑价格</button></td>
    </tr>)}</tbody></table></div>
    {editing && <ModelDialog model={editing} onClose={() => setEditing(null)} onChanged={onChanged} />}
  </>
}

function PriceSummary({ model }: { model: ModelView }) {
  const note = model.modelId === 'deepseek-v4-flash' ? '高峰价' : model.modelId === 'qwen3.5-plus' ? '≤128K' : model.modelId === 'qwen3.5-omni-plus-realtime' ? '参考价' : null
  if (model.billingUnit === 'TOKENS') return <span className="price-summary">¥{model.inputPricePerMillion} / ¥{model.outputPricePerMillion}<small>/ M Token {note && `· ${note}`}</small></span>
  if (model.billingUnit === 'CHARACTERS') return <span className="price-summary">¥{model.characterPricePerMillion}<small>/ M 字符</small></span>
  if (model.billingUnit === 'REQUESTS') return <span className="price-summary">¥{model.requestPricePerCall}<small>/ 次</small></span>
  if (model.billingUnit === 'MIXED') return <span className="price-summary">¥{model.inputPricePerMillion} / ¥{model.outputPricePerMillion}<small>Token · 音频 ¥{model.audioInputPricePerMinute} / ¥{model.audioOutputPricePerMinute}</small></span>
  return <span className="price-summary">¥{model.audioInputPricePerMinute} / ¥{model.audioOutputPricePerMinute}<small>/ 分钟 {note && `· ${note}`}</small></span>
}

function ModelDialog({ model, onClose, onChanged }: { model: ModelView; onClose: () => void; onChanged: () => Promise<unknown> }) {
  const [draft, setDraft] = useState(model)
  const mutation = useMutation({ mutationFn: () => updateModel(model.modelId, draft), onSuccess: async () => { await onChanged(); onClose() } })
  const priceField = (label: string, key: keyof Pick<ModelView, 'inputPricePerMillion' | 'outputPricePerMillion' | 'characterPricePerMillion' | 'audioInputPricePerMinute' | 'audioOutputPricePerMinute' | 'requestPricePerCall'>) => <label>{label}<input type="number" min="0" step="0.000001" value={draft[key]} onChange={(event) => setDraft({ ...draft, [key]: Number(event.target.value) })} /></label>
  return <div className="modal-backdrop"><section className="entitlement-dialog model-dialog" role="dialog" aria-modal="true" aria-labelledby="model-title">
    <header className="entitlement-dialog__header"><div><p className="eyebrow">MODEL PRICING</p><h2 id="model-title">编辑 {model.displayName}</h2><p className="entitlement-dialog__identity"><span>{model.modelId}</span></p></div><button className="modal-close" type="button" aria-label="关闭模型价格" onClick={onClose}><X size={18} /></button></header>
    <div className="entitlement-dialog__form"><label>显示名称<input value={draft.displayName} onChange={(event) => setDraft({ ...draft, displayName: event.target.value })} /></label><label>计费单位<select value={draft.billingUnit} onChange={(event) => setDraft({ ...draft, billingUnit: event.target.value as ModelView['billingUnit'] })}>{Object.entries(billingLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>{priceField('输入 / 百万 Token', 'inputPricePerMillion')}{priceField('输出 / 百万 Token', 'outputPricePerMillion')}{priceField('每百万字符', 'characterPricePerMillion')}{priceField('音频输入 / 分钟', 'audioInputPricePerMinute')}{priceField('音频输出 / 分钟', 'audioOutputPricePerMinute')}{priceField('每次调用', 'requestPricePerCall')}</div>
    <footer className="entitlement-dialog__footer">{mutation.isError ? <small className="form-error">{mutation.error.message}</small> : <span />}<div className="entitlement-dialog__actions"><button className="quiet-button" type="button" onClick={onClose}>取消</button><button className="primary-button" type="button" disabled={mutation.isPending} onClick={() => mutation.mutate()}>{mutation.isPending ? '保存中…' : '保存价格'}</button></div></footer>
  </section></div>
}

function RouteEditor({ models, routes, onChanged }: { models: ModelView[]; routes: Array<{ capability: AiCapability; modelIds: string[] }>; onChanged: () => Promise<unknown> }) {
  const [drafts, setDrafts] = useState<Record<string, string[]>>({})
  useEffect(() => setDrafts(Object.fromEntries(routes.map((route) => [route.capability, route.modelIds]))), [routes])
  const mutation = useMutation({ mutationFn: ({ capability, modelIds }: { capability: AiCapability; modelIds: string[] }) => replaceRoute(capability, modelIds), onSuccess: onChanged })
  const move = (capability: AiCapability, index: number, offset: number) => setDrafts((current) => { const list = [...(current[capability] || [])]; const target = index + offset; if (target < 0 || target >= list.length) return current; [list[index], list[target]] = [list[target], list[index]]; return { ...current, [capability]: list } })
  return <section className="system-block route-block">
    <BlockHeader title="主备路由" count="按顺序自动降级" />
    <div className="route-flow">{routes.map((route) => {
      const list = drafts[route.capability] || []
      const candidates = models.filter((model) => model.capability === route.capability && model.enabled && !list.includes(model.modelId))
      const CapabilityIcon = capabilityIcons[route.capability]
      return <article className="route-card" key={route.capability}>
          <header><span><CapabilityIcon size={15} />{capabilityLabels[route.capability]}</span><button className="icon-control icon-control--small" type="button" title="保存路由" aria-label="保存路由" disabled={mutation.isPending || list.length === 0} onClick={() => mutation.mutate({ capability: route.capability, modelIds: list })}><Save size={14} /></button></header>
          <ol>{list.map((modelId, index) => { const model = models.find((item) => item.modelId === modelId); return <li key={modelId}>
            <span className={`route-role route-role--${index === 0 ? 'primary' : 'fallback'}`}>{index === 0 ? '主用' : `备用 ${index}`}</span>
            <span className="route-model"><strong>{model?.displayName || modelId}</strong><code>{modelId}</code></span>
            <span className="route-rank-actions">
              <button type="button" title="上移" aria-label={`上移 ${modelId}`} disabled={index === 0} onClick={() => move(route.capability, index, -1)}><ArrowUp size={12} /></button>
              <button type="button" title="下移" aria-label={`下移 ${modelId}`} disabled={index === list.length - 1} onClick={() => move(route.capability, index, 1)}><ArrowDown size={12} /></button>
              <button type="button" title="移出路由" aria-label={`移出 ${modelId}`} disabled={list.length === 1} onClick={() => setDrafts({ ...drafts, [route.capability]: list.filter((id) => id !== modelId) })}><X size={12} /></button>
            </span>
          </li> })}</ol>
          {candidates.length > 0 && <label className="route-add"><span>添加备用</span><select value="" aria-label={`${capabilityLabels[route.capability]}添加备用模型`} onChange={(event) => { if (event.target.value) setDrafts({ ...drafts, [route.capability]: [...list, event.target.value] }) }}><option value="">选择模型</option>{candidates.map((model) => <option key={model.modelId} value={model.modelId}>{model.modelId}</option>)}</select></label>}
        </article>
    })}</div>
  </section>
}

function PanelMessage({ title, detail, tone = 'neutral' }: { title: string; detail: string; tone?: 'neutral' | 'danger' }) {
  return <div className={`panel-message panel-message--${tone}`} role={tone === 'danger' ? 'alert' : 'status'}><strong>{title}</strong><p>{detail}</p></div>
}
