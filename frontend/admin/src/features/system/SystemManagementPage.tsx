import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Activity, ArrowDown, ArrowUp, Bot, BrainCircuit, ChevronLeft, ChevronRight,
  CircleDollarSign, Coins, Database, KeyRound, Mic2, Radio, RefreshCw,
  Route, Save, ServerCog, Sparkles, TriangleAlert, UsersRound, Volume2, X,
  type LucideIcon,
} from 'lucide-react'
import { useEffect, useState, type Dispatch, type SetStateAction } from 'react'
import { listDataSources, syncAliyunOfficialUsage } from '../governance/governanceApi'
import {
  getAiConfiguration, getCredentialStatus, getInvocationUsage, replaceCredential, replaceRoute,
  updateModel, updateProvider, type AiCapability, type InvocationUsage, type ModelView, type ProviderView,
} from './systemApi'

const stateLabels: Record<string, string> = { READY: '运行正常', HEALTHY: '运行正常', DEGRADED: '能力受限', DISABLED: '未启用', UNAVAILABLE: '不可用', ERROR: '连接异常' }
const stateTones: Record<string, string> = { READY: 'ok', HEALTHY: 'ok', DEGRADED: 'waiting', DISABLED: 'neutral', UNAVAILABLE: 'danger', ERROR: 'danger' }
const capabilityLabels: Record<AiCapability, string> = { REALTIME: '实时对话', LLM: '文本模型', SCORING: '发音评分', TTS: '语音合成', TRANSCRIPTION: '语音识别' }
const billingLabels: Record<ModelView['billingUnit'], string> = { TOKENS: 'Token', AUDIO_MINUTES: '音频分钟', CHARACTERS: '字符', REQUESTS: '请求', MIXED: '混合' }
const capabilityIcons: Record<AiCapability, LucideIcon> = { REALTIME: Radio, LLM: BrainCircuit, SCORING: Sparkles, TTS: Volume2, TRANSCRIPTION: Mic2 }
const businessSceneLabels: Record<string, string> = {
  realtime_connect: '实时连接', realtime_session: '实时会话', tts: '语音合成', llm: '文本生成',
  dialogue_turn_feedback: '对话反馈', dialogue_report: '对话报告', pronunciation_scoring: '发音评分', transcription: '语音识别',
}

const dateInputValue = (daysAgo: number) => {
  const date = new Date(); date.setDate(date.getDate() - daysAgo)
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

const dateBoundaryIso = (value: string, addDays = 0) => {
  const date = new Date(`${value}T00:00:00`); date.setDate(date.getDate() + addDays); return date.toISOString()
}

export function SystemManagementPage() {
  const queryClient = useQueryClient()
  const sources = useQuery({ queryKey: ['governance', 'data-sources'], queryFn: listDataSources, refetchInterval: 15_000 })
  const configuration = useQuery({ queryKey: ['ai', 'configuration'], queryFn: getAiConfiguration, refetchInterval: 15_000 })
  const [filters, setFilters] = useState<UsageFilters>({ from: dateInputValue(30), to: dateInputValue(0), userId: '', providerId: '', modelId: '', page: 1 })
  const usage = useQuery({
    queryKey: ['ai', 'usage', filters],
    queryFn: () => getInvocationUsage({
      userId: filters.userId,
      providerId: filters.providerId,
      modelId: filters.modelId,
      page: filters.page,
      limit: 10,
      from: filters.from ? dateBoundaryIso(filters.from) : undefined,
      to: filters.to ? dateBoundaryIso(filters.to, 1) : undefined,
    }),
    refetchInterval: 15_000,
  })
  const sync = useMutation({
    mutationFn: syncAliyunOfficialUsage,
    onSuccess: async () => Promise.all([
      queryClient.invalidateQueries({ queryKey: ['governance', 'data-sources'] }),
      queryClient.invalidateQueries({ queryKey: ['governance', 'reconciliation'] }),
    ]),
  })
  const refreshConfiguration = async () => queryClient.invalidateQueries({ queryKey: ['ai', 'configuration'] })
  const refreshAll = async () => Promise.all([sources.refetch(), configuration.refetch(), usage.refetch()])
  const lastUpdatedAt = Math.max(sources.dataUpdatedAt, configuration.dataUpdatedAt, usage.dataUpdatedAt)

  return <div className="system-page system-console">
    <header className="system-console__heading">
      <div>
        <p className="system-breadcrumb">系统管理 <span>/</span> 模型供应商与费用</p>
        <h1>模型供应商与费用</h1>
        <p>统一管理供应商、模型配置、路由策略与用户用量，确保业务稳定与成本可控。</p>
      </div>
      <div className="system-console__freshness">
        <span className={`compact-state compact-state--${configuration.data?.databaseBacked ? 'ok' : 'danger'}`}><i />{configuration.data?.databaseBacked ? '数据库配置已生效' : '应急配置模式'}</span>
        <span>{lastUpdatedAt ? `更新于 ${new Date(lastUpdatedAt).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })}` : '正在更新数据'}</span>
        <button className="icon-control" type="button" aria-label="刷新全部数据" title="刷新全部数据" onClick={() => void refreshAll()} disabled={sources.isFetching || configuration.isFetching || usage.isFetching}>
          <RefreshCw size={16} />
        </button>
      </div>
    </header>

    <OverviewMetrics usage={usage.data} />
    <DataSourcesSection sources={sources} sync={sync} />

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
    <UsageSection usage={usage} filters={filters} setFilters={setFilters} configuration={configuration.data} />
  </div>
}

function OverviewMetrics({ usage }: { usage?: InvocationUsage }) {
  const summary = usage?.summary
  const failedAttempts = Math.max(0, (summary?.attempts ?? 0) - (summary?.succeededAttempts ?? 0))
  const errorRate = summary?.attempts ? failedAttempts / summary.attempts * 100 : 0
  const activeUsers = usage?.byUser.filter((user) => user.userId).length ?? 0
  const cards: Array<{ label: string; value: string; detail: string; icon: LucideIcon; tone: string; trend: number[] }> = [
    { label: '调用请求', value: (summary?.requests ?? 0).toLocaleString(), detail: `${(summary?.attempts ?? 0).toLocaleString()} 次模型尝试`, icon: Activity, tone: 'blue', trend: [28, 34, 30, 48, 37, 42, 40, 57] },
    { label: 'Token 消耗', value: formatTokens(summary?.totalTokens ?? 0), detail: `输入 ${formatCount(summary?.inputTokens ?? 0)} · 输出 ${formatCount(summary?.outputTokens ?? 0)}`, icon: Coins, tone: 'violet', trend: [38, 52, 36, 58, 45, 61, 42, 67] },
    { label: '总费用（估算）', value: formatMoney(summary?.estimatedCost ?? 0), detail: '根据调用时价格快照计算', icon: CircleDollarSign, tone: 'green', trend: [24, 28, 27, 40, 36, 51, 47, 64] },
    { label: '错误率', value: `${errorRate.toFixed(2)}%`, detail: `${failedAttempts.toLocaleString()} 次失败调用`, icon: TriangleAlert, tone: 'orange', trend: [20, 51, 32, 45, 24, 49, 36, 62] },
    { label: '活跃用户', value: activeUsers.toLocaleString(), detail: '当前筛选时间范围', icon: UsersRound, tone: 'cyan', trend: [25, 31, 52, 34, 57, 42, 60, 68] },
  ]
  return <section className="overview-metrics" aria-label="模型运营概览">
    {cards.map((card) => <MetricCard key={card.label} {...card} />)}
  </section>
}

function MetricCard({ label, value, detail, icon: Icon, tone, trend }: { label: string; value: string; detail: string; icon: LucideIcon; tone: string; trend: number[] }) {
  return <article className={`overview-card overview-card--${tone}`}>
    <div className="overview-card__top"><span>{label}</span><span className="overview-card__icon"><Icon size={17} /></span></div>
    <strong>{value}</strong>
    <div className="overview-card__bottom"><small>{detail}</small><span className="mini-trend" aria-hidden="true">{trend.map((height, index) => <i key={`${height}-${index}`} style={{ height: `${height}%` }} />)}</span></div>
  </article>
}

function BlockHeader({ title, count, suffix, action }: { title: string; count?: string; suffix?: string; action?: React.ReactNode }) {
  return <header className="block-heading">
    <div><h2>{title}</h2>{count && <span>{count}</span>}{suffix && <small>{suffix}</small>}</div>
    {action}
  </header>
}

function DataSourcesSection({ sources, sync }: { sources: ReturnType<typeof useQuery<Awaited<ReturnType<typeof listDataSources>>, Error>>; sync: ReturnType<typeof useMutation<Awaited<ReturnType<typeof syncAliyunOfficialUsage>>, Error, void>> }) {
  return <section className="system-block source-status-block" aria-labelledby="source-heading">
    <BlockHeader title="数据源状态" action={<button className="table-action" type="button" disabled={sync.isPending} onClick={() => sync.mutate()}><RefreshCw size={14} />{sync.isPending ? '正在同步 SLS' : '立即同步 SLS'}</button>} />
    {sources.isLoading && <PanelMessage title="正在检查数据源" detail="读取统一账本、阿里云 SLS 与 Prometheus 状态。" />}
    {sources.isError && <PanelMessage title="数据源状态读取失败" detail="请检查管理服务和数据库连接。" tone="danger" />}
    {sources.data && <div className="source-grid">{sources.data.map((source) => {
      const normalized = source.state.toUpperCase()
      const SourceIcon = source.code.includes('POSTGRES') ? Database : source.code.includes('SLS') ? ServerCog : Activity
      return <article className="source-card" key={source.code}>
        <span className={`source-card__icon source-card__icon--${stateTones[normalized] ?? 'neutral'}`}><SourceIcon size={16} /></span>
        <div><strong>{source.name}</strong><p>{source.detail}</p><code>{source.code}</code></div>
        <span className={`compact-state compact-state--${stateTones[normalized] ?? 'neutral'}`}><i />{stateLabels[normalized] ?? source.state}</span>
      </article>
    })}</div>}
    {sync.data && <p className="action-feedback" role="status">已导入 {sync.data.imported} 条，匹配 {sync.data.matched} 条<span>扫描 {sync.data.scanned} · 未匹配 {sync.data.unmatched}</span></p>}
    {sync.isError && <p className="action-feedback action-feedback--error" role="alert">同步失败，可重试<span>未改变现有对账数据。</span></p>}
  </section>
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
  const [secret, setSecret] = useState('')
  const mutation = useMutation({ mutationFn: () => replaceCredential(provider.providerId, secret), onSuccess: () => { setSecret(''); void status.refetch() } })
  return <div className="modal-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose() }}><section className="entitlement-dialog" role="dialog" aria-modal="true" aria-labelledby="credential-title">
    <header className="entitlement-dialog__header"><div><p className="eyebrow">PROVIDER CREDENTIAL</p><h2 id="credential-title">{provider.displayName} 密钥</h2><p className="entitlement-dialog__identity">数据库只保存加密内容<span>{status.data?.fingerprint || '尚未配置'}</span></p></div><button className="modal-close" type="button" aria-label="关闭密钥设置" onClick={onClose}><X size={18} /></button></header>
    <div className="credential-form"><label>新密钥<input type="password" autoComplete="new-password" value={secret} onChange={(event) => setSecret(event.target.value)} placeholder="输入后将覆盖旧密钥" /></label>{status.data && !status.data.writable && <p className="form-error">服务器尚未配置凭据加密主密钥，当前只能使用环境变量。</p>}{mutation.isSuccess && <p className="form-success" role="status">密钥已更新，后续调用立即生效。</p>}{mutation.isError && <p className="form-error">{mutation.error.message}</p>}</div>
    <footer className="entitlement-dialog__footer"><span /><div className="entitlement-dialog__actions"><button className="quiet-button" type="button" onClick={onClose}>取消</button><button className="primary-button" type="button" disabled={!status.data?.writable || secret.length < 8 || mutation.isPending} onClick={() => mutation.mutate()}>{mutation.isPending ? '更新中…' : '更新密钥'}</button></div></footer>
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

type UsageFilters = { from: string; to: string; userId: string; providerId: string; modelId: string; page: number }

function UsageSection({ usage, filters, setFilters, configuration }: { usage: ReturnType<typeof useQuery<Awaited<ReturnType<typeof getInvocationUsage>>, Error>>; filters: UsageFilters; setFilters: Dispatch<SetStateAction<UsageFilters>>; configuration?: { providers: ProviderView[]; models: ModelView[] } }) {
  const summary = usage.data?.summary
  const [view, setView] = useState<'models' | 'users' | 'records'>('models')
  const updateFilter = (patch: Partial<Omit<UsageFilters, 'page'>>) => setFilters({ ...filters, ...patch, page: 1 })
  const failedAttempts = Math.max(0, (summary?.attempts ?? 0) - (summary?.succeededAttempts ?? 0))
  const errorRate = summary?.attempts ? failedAttempts / summary.attempts * 100 : 0
  return <section className="system-block usage-block">
    <header className="usage-block__header">
      <div><h2>调用与费用</h2><span>真实模型调用账本</span></div>
      <div className="usage-tabs" role="tablist" aria-label="用量查看方式">
        <button type="button" role="tab" aria-selected={view === 'models'} className={view === 'models' ? 'is-active' : ''} onClick={() => setView('models')}>模型消耗</button>
        <button type="button" role="tab" aria-selected={view === 'users'} className={view === 'users' ? 'is-active' : ''} onClick={() => setView('users')}>用户消耗</button>
        <button type="button" role="tab" aria-selected={view === 'records'} className={view === 'records' ? 'is-active' : ''} onClick={() => setView('records')}>调用明细</button>
      </div>
      <button className="icon-control" type="button" aria-label="刷新调用数据" title="刷新调用数据" disabled={usage.isFetching} onClick={() => void usage.refetch()}><RefreshCw size={15} /></button>
    </header>
    <div className="usage-filters">
      <label>开始日期<input aria-label="开始日期" type="date" value={filters.from} onChange={(event) => updateFilter({ from: event.target.value })} /></label>
      <span className="usage-filters__arrow">→</span>
      <label>结束日期<input aria-label="结束日期" type="date" value={filters.to} onChange={(event) => updateFilter({ to: event.target.value })} /></label>
      <label>供应商<select aria-label="供应商" value={filters.providerId} onChange={(event) => updateFilter({ providerId: event.target.value })}><option value="">全部供应商</option>{configuration?.providers.map((provider) => <option key={provider.providerId} value={provider.providerId}>{provider.displayName}</option>)}</select></label>
      <label>模型<select aria-label="模型" value={filters.modelId} onChange={(event) => updateFilter({ modelId: event.target.value })}><option value="">全部模型</option>{configuration?.models.map((model) => <option key={model.modelId} value={model.modelId}>{model.modelId}</option>)}</select></label>
      <label className="usage-user-filter">用户 ID<input aria-label="用户 ID" placeholder="用户 UUID" value={filters.userId} onChange={(event) => updateFilter({ userId: event.target.value })} /></label>
    </div>
    {usage.isLoading && <PanelMessage title="正在汇总模型调用" detail="按用户、模型和时间范围读取统一账本。" />}
    {usage.isError && <PanelMessage title="模型用量读取失败" detail={usage.error.message} tone="danger" />}
    {summary && <div className="usage-layout">
      <aside className="usage-summary">
        <div className="usage-summary__heading"><GaugeIcon /><span>核心指标</span><small>当前筛选范围</small></div>
        <div className="usage-summary__grid">
          <SummaryMetric label="调用请求" value={summary.requests.toLocaleString()} detail={`${summary.attempts.toLocaleString()} 次尝试`} />
          <SummaryMetric label="Token 消耗" value={formatTokens(summary.totalTokens)} detail={`入 ${formatCount(summary.inputTokens)} · 出 ${formatCount(summary.outputTokens)}`} />
          <SummaryMetric label="平均延迟" value={`${Number(summary.averageDurationMs).toFixed(0)} ms`} detail={`错误率 ${errorRate.toFixed(2)}%`} />
          <SummaryMetric label="估算费用" value={formatMoney(summary.estimatedCost)} detail={`${summary.fallbackAttempts.toLocaleString()} 次降级`} />
        </div>
      </aside>
      <div className="usage-view">
        {view === 'models' && <ModelUsageTable models={usage.data?.byModel ?? []} />}
        {view === 'users' && <UserUsageTable users={usage.data?.byUser ?? []} />}
        {view === 'records' && <InvocationRecordsTable records={usage.data?.records ?? []} pagination={usage.data?.recordPage} onPageChange={(page) => setFilters({ ...filters, page })} />}
      </div>
    </div>}
  </section>
}

function GaugeIcon() {
  return <span className="usage-summary__icon"><Route size={15} /></span>
}

function SummaryMetric({ label, value, detail }: { label: string; value: string; detail: string }) {
  return <div><span>{label}</span><strong>{value}</strong><small>{detail}</small></div>
}

function ModelUsageTable({ models }: { models: InvocationUsage['byModel'] }) {
  if (models.length === 0) return <PanelMessage title="当前范围没有模型用量" detail="完成模型调用后会自动生成汇总。" />
  return <div className="compact-table-scroll"><table className="compact-table usage-table model-usage-table"><thead><tr><th>Provider / 模型</th><th>能力</th><th>调用次数</th><th>成功率</th><th>Token 消耗</th><th>平均延迟</th><th>总费用（¥）</th></tr></thead><tbody>{models.map((model) => {
    const successRate = model.attempts ? model.successes / model.attempts * 100 : 0
    return <tr key={`${model.providerId}-${model.modelId}-${model.capability}`}><td><strong>{model.modelId}</strong><small>{model.providerId}</small></td><td><span className="model-type">{capabilityLabels[model.capability as AiCapability] ?? model.capability}</span></td><td className="numeric">{model.attempts.toLocaleString()}</td><td className="numeric">{successRate.toFixed(1)}%</td><td className="numeric">{formatTokens(model.totalTokens)}</td><td className="numeric">{Number(model.averageDurationMs).toFixed(0)} ms</td><td className="numeric emphasis">{Number(model.estimatedCost).toFixed(6)}</td></tr>
  })}</tbody></table></div>
}

function InvocationRecordsTable({ records, pagination, onPageChange }: { records: InvocationUsage['records']; pagination?: InvocationUsage['recordPage']; onPageChange: (page: number) => void }) {
  if (records.length === 0) return <PanelMessage title="当前范围没有调用记录" detail="新的模型调用会在完成后自动出现在这里。" />
  const page = pagination?.page ?? 1
  const totalPages = pagination?.totalPages ?? 1
  const totalRecords = pagination?.totalRecords ?? records.length
  const pageSize = pagination?.pageSize ?? 10
  const firstRecord = (page - 1) * pageSize + 1
  const lastRecord = Math.min(page * pageSize, totalRecords)
  return <div className="usage-records"><div className="compact-table-scroll"><table className="compact-table usage-table usage-detail-table"><thead><tr><th>调用时间</th><th>用户</th><th>模型</th><th>场景</th><th>状态</th><th>用量</th><th>耗时 / 费用</th></tr></thead><tbody>{records.map((record) => {
    const characters = record.inputCharacters + record.outputCharacters
    const audioSeconds = Number(record.audioInputSeconds + record.audioOutputSeconds)
    return <tr key={record.invocationId}>
    <td data-label="调用时间"><strong>{new Date(record.startedAt).toLocaleString('zh-CN')}</strong></td>
    <td data-label="用户"><strong>{record.userEmail || (record.userId ? '已绑定用户' : '系统任务')}</strong></td>
    <td data-label="模型"><strong>{record.modelId}</strong></td>
    <td data-label="场景"><strong title={record.businessScene}>{formatBusinessScene(record.businessScene)}</strong></td>
    <td data-label="状态"><span className={`compact-state compact-state--${record.status === 'SUCCEEDED' ? 'ok' : 'danger'}`}><i />{record.status === 'SUCCEEDED' ? '成功' : record.errorCode || '失败'}</span></td>
    <td data-label="用量" className="numeric"><span className="usage-values"><strong className="usage-token-value">{formatTokens(record.totalTokens)}</strong>{characters > 0 && <strong className="usage-unit-value">{characters.toLocaleString()} 字符</strong>}{audioSeconds > 0 && <strong className="usage-unit-value">{audioSeconds.toFixed(1)}s 音频</strong>}</span></td>
    <td data-label="耗时 / 费用" className="numeric emphasis"><span className="usage-values"><strong>{formatDuration(record.durationMs)}</strong><strong className="usage-cost">¥{Number(record.estimatedCost).toFixed(6)}</strong></span></td>
  </tr> })}</tbody></table></div><footer className="table-pagination" aria-label="调用明细分页">
    <span>第 {firstRecord}–{lastRecord} 条，共 {totalRecords.toLocaleString()} 条</span>
    <div><button type="button" aria-label="上一页" title="上一页" disabled={page <= 1} onClick={() => onPageChange(page - 1)}><ChevronLeft size={16} /></button><strong>第 {page} / {totalPages} 页</strong><button type="button" aria-label="下一页" title="下一页" disabled={page >= totalPages} onClick={() => onPageChange(page + 1)}><ChevronRight size={16} /></button></div>
  </footer></div>
}

function UserUsageTable({ users }: { users: InvocationUsage['byUser'] }) {
  const [expandedUsers, setExpandedUsers] = useState<Set<string>>(() => new Set())
  if (users.length === 0) return <PanelMessage title="当前范围没有用户用量" detail="用户完成模型调用后会自动生成汇总。" />
  const toggle = (key: string) => setExpandedUsers((current) => { const next = new Set(current); if (next.has(key)) next.delete(key); else next.add(key); return next })
  return <div className="compact-table-scroll"><table className="compact-table usage-table user-usage-table"><thead><tr><th>用户</th><th>请求 / 会话</th><th>调用结果</th><th>Token / 音频</th><th>总耗时</th><th>费用</th><th>最后调用</th></tr></thead><tbody>{users.map((user, index) => { const key = user.userId || `system-${index}`; return <UserUsageRows key={key} user={user} expanded={expandedUsers.has(key)} onToggle={() => toggle(key)} /> })}</tbody></table></div>
}

function UserUsageRows({ user, expanded, onToggle }: { user: InvocationUsage['byUser'][number]; expanded: boolean; onToggle: () => void }) {
  return <><tr><td><button className="usage-expand-button" type="button" aria-expanded={expanded} onClick={onToggle}><span aria-hidden="true">{expanded ? '▾' : '▸'}</span><strong>{user.email || '系统任务'}</strong></button><small>{user.userId || '无用户 ID'}</small></td><td className="numeric"><strong>{user.requests.toLocaleString()} / {user.sessions.toLocaleString()}</strong><small>{user.attempts.toLocaleString()} 次尝试</small></td><td className="numeric"><strong>{user.successes.toLocaleString()} 成功</strong><small>{user.failures.toLocaleString()} 失败 · {user.fallbackAttempts.toLocaleString()} 降级</small></td><td className="numeric"><strong>{formatTokens(user.totalTokens)}</strong><small>{Number(user.audioInputSeconds + user.audioOutputSeconds).toFixed(1)} 秒音频</small></td><td className="numeric"><strong>{formatDuration(user.totalDurationMs)}</strong><small>平均 {Number(user.averageDurationMs).toFixed(0)} ms</small></td><td className="numeric emphasis">¥{Number(user.estimatedCost).toFixed(6)}</td><td><strong>{new Date(user.lastInvokedAt).toLocaleString('zh-CN')}</strong><small>{user.models.length} 个模型</small></td></tr>{expanded && <tr className="user-model-detail"><td colSpan={7}><table><thead><tr><th>Provider / 模型</th><th>请求 / 尝试</th><th>Token</th><th>音频</th><th>总耗时</th><th>费用</th></tr></thead><tbody>{user.models.map((model) => <tr key={`${model.providerId}-${model.modelId}-${model.capability}`}><td><strong>{model.modelId}</strong><small>{model.providerId} · {capabilityLabels[model.capability as AiCapability] ?? model.capability}</small></td><td className="numeric"><strong>{model.requests.toLocaleString()} / {model.attempts.toLocaleString()}</strong><small>{model.successes.toLocaleString()} 成功</small></td><td className="numeric"><strong>{formatTokens(model.totalTokens)}</strong><small>入 {formatCount(model.inputTokens)} · 出 {formatCount(model.outputTokens)}</small></td><td className="numeric">{Number(model.audioInputSeconds + model.audioOutputSeconds).toFixed(1)} s</td><td className="numeric">{formatDuration(model.totalDurationMs)}</td><td className="numeric emphasis">¥{Number(model.estimatedCost).toFixed(6)}</td></tr>)}</tbody></table></td></tr>}</>
}

function formatCount(value: number) {
  return value > 0 ? value.toLocaleString() : '/'
}

function formatTokens(value: number) {
  return value > 0 ? `${value.toLocaleString()} Token` : '/'
}

function formatBusinessScene(value: string) {
  return businessSceneLabels[value] || value.replaceAll('_', ' ')
}

function formatMoney(value: number) {
  return `¥${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: value < 1 ? 4 : 2, maximumFractionDigits: value < 1 ? 4 : 2 })}`
}

function formatDuration(milliseconds: number) {
  if (milliseconds < 1_000) return `${milliseconds.toLocaleString()} ms`
  if (milliseconds < 60_000) return `${(milliseconds / 1_000).toFixed(1)} s`
  return `${(milliseconds / 60_000).toFixed(1)} min`
}

function PanelMessage({ title, detail, tone = 'neutral' }: { title: string; detail: string; tone?: 'neutral' | 'danger' }) {
  return <div className={`panel-message panel-message--${tone}`} role={tone === 'danger' ? 'alert' : 'status'}><strong>{title}</strong><p>{detail}</p></div>
}
