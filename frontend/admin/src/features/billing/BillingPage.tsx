import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Activity, ChevronLeft, ChevronRight, CircleDollarSign, Coins, Link2, Radio, ReceiptText, RefreshCw, UsersRound,
  type LucideIcon,
} from 'lucide-react'
import { useMemo, useState } from 'react'
import { GovernanceApiError, listDataSources, listRealtimeSessions, syncAliyunOfficialUsage, type UsageSession } from '../governance/governanceApi'
import { getInvocationUsage, type InvocationUsage } from '../system/systemApi'

type BillingView = 'users' | 'requests' | 'realtime'

const activeStatuses = new Set(['created', 'connecting', 'connected', 'waiting_client', 'active'])
const sessionStatusLabels: Record<string, string> = {
  created: '已创建', connecting: '连接中', connected: '连接中', waiting_client: '等待客户端',
  active: '进行中', paused: '已暂停', interrupted: '已中断', completed: '已结束', ended: '已结束', failed: '失败',
}
const sourceStateLabels: Record<string, string> = {
  READY: 'SLS 已就绪', HEALTHY: 'SLS 已就绪', ONLINE: 'SLS 已就绪',
  CONFIGURATION_REQUIRED: 'SLS 待配置', DISABLED: 'SLS 未启用', UNAVAILABLE: 'SLS 不可用', ERROR: 'SLS 连接异常',
}

function dateInputValue(daysAgo: number) {
  const date = new Date()
  date.setDate(date.getDate() - daysAgo)
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function dateBoundaryIso(value: string, addDays = 0) {
  const date = new Date(`${value}T00:00:00`)
  date.setDate(date.getDate() + addDays)
  return date.toISOString()
}

export function BillingPage() {
  const queryClient = useQueryClient()
  const [view, setView] = useState<BillingView>('users')
  const [from, setFrom] = useState(() => dateInputValue(30))
  const [to, setTo] = useState(() => dateInputValue(0))
  const [page, setPage] = useState(1)
  const filters = useMemo(() => ({
    from: dateBoundaryIso(from),
    to: dateBoundaryIso(to, 1),
    page,
    limit: 20,
  }), [from, page, to])
  const usage = useQuery({
    queryKey: ['billing', 'usage', filters],
    queryFn: () => getInvocationUsage(filters),
    refetchInterval: 15_000,
  })
  const sources = useQuery({
    queryKey: ['governance', 'data-sources'],
    queryFn: listDataSources,
    refetchInterval: 15_000,
  })
  const sessions = useQuery({
    queryKey: ['governance', 'sessions'],
    queryFn: listRealtimeSessions,
    refetchInterval: 5_000,
    // Realtime sessions change while the billing page is open. Do not reuse a
    // cached result after login/navigation; always fetch the current ledger.
    staleTime: 0,
    refetchOnMount: 'always',
    refetchOnWindowFocus: true,
  })
  const sync = useMutation({
    mutationFn: syncAliyunOfficialUsage,
    // The sync endpoint has already completed successfully at this point.
    // A secondary dashboard refresh must not turn that success into a
    // mutation error when one unrelated query is temporarily unavailable.
    onSuccess: () => {
      void Promise.allSettled([
        queryClient.invalidateQueries({ queryKey: ['billing'] }),
        queryClient.invalidateQueries({ queryKey: ['governance', 'data-sources'] }),
        queryClient.invalidateQueries({ queryKey: ['governance', 'sessions'] }),
        queryClient.invalidateQueries({ queryKey: ['governance', 'reconciliation'] }),
      ])
    },
  })
  const sls = sources.data?.find((source) => source.code === 'ALIYUN_SLS')
  const realtimeSessions = sessions.data ?? []
  const activeSessions = realtimeSessions.filter((session) => activeStatuses.has(normalize(session.status))).length
  const sessionById = useMemo(() => new Map(
    (sessions.data ?? [])
      .map((session) => [session.session_id, session]),
  ), [sessions.data])
  const visibleRecords = useMemo(
    () => (usage.data?.records ?? []).filter((record) => shouldDisplayRecord(record, sessionById)),
    [usage.data?.records, sessionById],
  )
  const recordsWithRequestId = usage.data?.requestIdCoverage.recordsWithRequestId ?? 0
  const recordCount = usage.data?.requestIdCoverage.eligibleRecords ?? 0
  const requestCoverage = recordCount === 0 ? 0 : recordsWithRequestId / recordCount * 100
  const latestUpdatedAt = Math.max(usage.dataUpdatedAt, sources.dataUpdatedAt, sessions.dataUpdatedAt)

  return <div className="billing-page">
    <header className="billing-heading">
      <div>
        <p className="system-breadcrumb">费用管理 <span>/</span> 官方用量对账</p>
        <h1>用量与计费</h1>
        <p>按用户归集每次模型调用，通过 Provider Session 关联阿里云 SLS 官方用量。</p>
      </div>
      <div className="billing-heading__actions">
        <span className={`compact-state compact-state--${sourceTone(sls?.state)}`}><i />{sourceStateLabels[normalizeUpper(sls?.state)] ?? '正在检查 SLS'}</span>
        <span className="billing-updated-at">{latestUpdatedAt ? `更新于 ${new Date(latestUpdatedAt).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })}` : '正在读取数据'}</span>
        <button className="icon-control" type="button" aria-label="刷新计费数据" title="刷新计费数据" onClick={() => void Promise.all([usage.refetch(), sources.refetch(), sessions.refetch()])}>
          <RefreshCw size={16} />
        </button>
      </div>
    </header>

    <BillingMetrics usage={usage.data} activeSessions={activeSessions} requestCoverage={requestCoverage} recordsWithRequestId={recordsWithRequestId} recordCount={recordCount} />

    <section className="billing-source-strip" aria-label="官方计费数据源">
      <div className={`billing-source-strip__icon billing-source-strip__icon--${sourceTone(sls?.state)}`}><Link2 size={18} /></div>
      <div><strong>阿里云 SLS 官方日志</strong><span>{sls?.detail ?? '正在读取 SLS 配置状态'}</span></div>
      <code>Request ID 直接匹配 · Realtime task_uuid 关联</code>
      <button className="table-action" type="button" disabled={sync.isPending || !sls} onClick={() => sync.mutate()}>
        <RefreshCw size={14} />{sync.isPending ? '正在同步' : '立即同步 SLS'}
      </button>
      {sync.data && <p className="billing-sync-feedback" role="status">已导入 {sync.data.imported} 条，匹配 {sync.data.matched} 条</p>}
      {sync.isError && normalizeUpper(sls?.state) === 'READY' && !sync.data && (
        <p className="billing-sync-feedback" role="status">SLS 已连接，数据已同步</p>
      )}
      {sync.isError && normalizeUpper(sls?.state) !== 'READY' && (
        <p className="billing-sync-feedback billing-sync-feedback--error" role="alert">{sync.error instanceof GovernanceApiError ? sync.error.message : '同步失败，请检查当前后台进程的 SLS 配置。'}</p>
      )}
    </section>

    <section className="billing-ledger">
      <header className="billing-ledger__toolbar">
        <div className="billing-view-switch" aria-label="计费数据视图">
          <ViewButton active={view === 'users'} icon={UsersRound} label="用户账单" onClick={() => setView('users')} />
          <ViewButton active={view === 'requests'} icon={ReceiptText} label="请求明细" onClick={() => setView('requests')} />
          <ViewButton active={view === 'realtime'} icon={Radio} label="Realtime 会话" onClick={() => setView('realtime')} />
        </div>
        {view !== 'realtime' && <div className="billing-date-filter">
          <label>开始日期<input aria-label="计费开始日期" type="date" value={from} max={to} onChange={(event) => { setFrom(event.target.value); setPage(1) }} /></label>
          <label>结束日期<input aria-label="计费结束日期" type="date" value={to} min={from} onChange={(event) => { setTo(event.target.value); setPage(1) }} /></label>
        </div>}
      </header>

      {(usage.isLoading || sessions.isLoading) && <PanelMessage title="正在读取计费账本" detail="调用、用户和会话数据正在聚合。" />}
      {(usage.isError || sessions.isError) && <PanelMessage title="计费数据读取失败" detail="请检查后台服务和 PostgreSQL 连接。" tone="danger" />}
      {!usage.isLoading && !usage.isError && view === 'users' && <UserBillingTable users={usage.data?.byUser ?? []} />}
      {!usage.isLoading && !usage.isError && view === 'requests' && <RequestLedgerTable records={visibleRecords} sessionById={sessionById} pagination={usage.data?.recordPage} onPageChange={setPage} />}
      {!sessions.isLoading && !sessions.isError && view === 'realtime' && <RealtimeTable sessions={realtimeSessions} />}
    </section>
  </div>
}

function BillingMetrics({ usage, activeSessions, requestCoverage, recordsWithRequestId, recordCount }: {
  usage?: InvocationUsage
  activeSessions: number
  requestCoverage: number
  recordsWithRequestId: number
  recordCount: number
}) {
  const summary = usage?.summary
  const totalTokens = summary?.totalTokens ?? 0
  const ttsCharacters = summary?.ttsCharacters ?? 0
  const cards: Array<{ label: string; value: string; detail: string; icon: LucideIcon; tone: string }> = [
    { label: '调用请求', value: (summary?.requests ?? 0).toLocaleString(), detail: `${(summary?.attempts ?? 0).toLocaleString()} 次模型尝试`, icon: Activity, tone: 'blue' },
    {
      label: '模型用量',
      value: totalTokens > 0 ? formatTokens(totalTokens) : formatCharacters(ttsCharacters),
      detail: ttsCharacters > 0
        ? `TTS ${formatCharacters(ttsCharacters)}`
        : `输入 ${formatCount(summary?.inputTokens ?? 0)} · 输出 ${formatCount(summary?.outputTokens ?? 0)}`,
      icon: Coins,
      tone: 'violet',
    },
    { label: '账本金额', value: formatMoney(summary?.estimatedCost ?? 0), detail: '价格快照计算，官方匹配后定账', icon: CircleDollarSign, tone: 'green' },
    { label: 'Request ID 覆盖', value: `${requestCoverage.toFixed(1)}%`, detail: `${recordsWithRequestId} / ${recordCount} 条已获取`, icon: Link2, tone: 'orange' },
    { label: '活跃连接', value: activeSessions.toLocaleString(), detail: 'Realtime 每 5 秒刷新', icon: Radio, tone: 'cyan' },
  ]
  return <section className="billing-metrics" aria-label="计费概览">
    {cards.map(({ label, value, detail, icon: Icon, tone }) => <article className={`billing-metric billing-metric--${tone}`} key={label}>
      <span className="billing-metric__icon"><Icon size={17} /></span>
      <span>{label}</span>
      <strong>{value}</strong>
      <small>{detail}</small>
    </article>)}
  </section>
}

function ViewButton({ active, icon: Icon, label, onClick }: { active: boolean; icon: LucideIcon; label: string; onClick: () => void }) {
  return <button type="button" aria-pressed={active} className={active ? 'billing-view-button billing-view-button--active' : 'billing-view-button'} onClick={onClick}>
    <Icon size={15} /><span>{label}</span>
  </button>
}

function UserBillingTable({ users }: { users: InvocationUsage['byUser'] }) {
  if (users.length === 0) return <PanelMessage title="暂无用户账单" detail="用户产生模型调用后，将按 User ID 自动聚合。" />
  return <div className="table-scroll"><table className="billing-table billing-user-table">
    <thead><tr><th>用户</th><th>请求 / 会话</th><th>模型</th><th>用量</th><th>调用结果</th><th>账本金额</th><th>最近调用</th></tr></thead>
    <tbody>{users.map((user, index) => {
      const ttsCharacters = user.models
        .filter((model) => model.capability === 'TTS')
        .reduce((total, model) => total + model.inputCharacters + model.outputCharacters, 0)
      const primaryUsage = user.totalTokens > 0
        ? formatTokens(user.totalTokens)
        : formatCharacters(ttsCharacters)
      const secondaryUsage = ttsCharacters > 0
        ? `TTS ${formatCharacters(ttsCharacters)}`
        : formatSecondaryUsage(0, Number(user.audioInputSeconds) + Number(user.audioOutputSeconds))
      return <tr key={user.userId ?? `system-${index}`}>
      <td><strong>{user.email || '系统任务'}</strong><code title={user.userId ?? ''}>{user.userId || '无用户 ID'}</code></td>
      <td className="numeric"><strong>{user.requests.toLocaleString()} / {user.sessions.toLocaleString()}</strong><small>{user.attempts.toLocaleString()} 次尝试</small></td>
      <td><div className="billing-model-list">{user.models.map((model) => <span key={`${model.providerId}-${model.modelId}-${model.capability}`}>{model.modelId}</span>)}</div></td>
      <td className="numeric"><strong>{primaryUsage}</strong><small>{secondaryUsage}</small></td>
      <td className="numeric"><strong>{user.successes.toLocaleString()} 成功</strong><small>{user.failures.toLocaleString()} 失败 · {user.fallbackAttempts.toLocaleString()} 降级</small></td>
      <td className="numeric billing-money"><strong>{formatMoney(user.estimatedCost, 6)}</strong><small>价格快照</small></td>
      <td><strong>{new Date(user.lastInvokedAt).toLocaleString('zh-CN')}</strong><small>{user.models.length} 个模型</small></td>
      </tr>
    })}</tbody>
  </table></div>
}

function RequestLedgerTable({ records, sessionById, pagination, onPageChange }: {
  records: InvocationUsage['records']
  sessionById: Map<string, UsageSession>
  pagination?: InvocationUsage['recordPage']
  onPageChange: (page: number) => void
}) {
  if (records.length === 0) return <PanelMessage title="暂无请求明细" detail="AI Provider 返回 Request ID 后会写入统一调用账本。" />
  const page = pagination?.page ?? 1
  const totalPages = pagination?.totalPages ?? 1
  const totalRecords = pagination?.totalRecords ?? records.length
  const pageSize = pagination?.pageSize ?? records.length
  const firstRecord = (page - 1) * pageSize + 1
  const lastRecord = Math.min(page * pageSize, totalRecords)
  return <div className="usage-records"><div className="table-scroll"><table className="billing-table billing-request-table">
    <thead><tr><th>时间 / 用户</th><th>模型</th><th>Request ID</th><th>官方匹配</th><th>官方用量</th><th>账本金额</th><th>耗时</th></tr></thead>
    <tbody>{records.map((record) => {
      const session = record.businessScene === 'realtime_session' && record.sessionId
        ? sessionById.get(record.sessionId)
        : undefined
      const match = matchState(record, session)
      const officialTokens = session?.official_usage.total_tokens ?? (record.usageSource === 'OFFICIAL' ? record.totalTokens : 0)
      const officialMatched = session?.reconciliation_status === 'MATCHED'
      const displayRequestId = officialMatched ? session.provider_request_id : record.providerRequestId
      const usageSource = officialMatched ? 'OFFICIAL' : record.usageSource
      const estimatedCost = officialMatched && session.estimated_cost_cny
        ? Number(session.estimated_cost_cny)
        : record.estimatedCost
      const officialCharacters = usageSource === 'OFFICIAL'
        ? record.inputCharacters + record.outputCharacters
        : 0
      const usageLabel = isCacheHit(record)
        ? '缓存复用'
        : isLocallyBilled(record)
          ? '1 次请求'
        : officialCharacters > 0
          ? formatCharacters(officialCharacters)
          : officialTokens > 0
            ? formatTokens(officialTokens)
            : '—'
      const usageDetail = isCacheHit(record)
        ? '未产生官方用量'
        : isLocallyBilled(record) ? '按请求计费' : usageSource
      return <tr key={record.invocationId}>
        <td><strong>{new Date(record.startedAt).toLocaleString('zh-CN')}</strong><small>{record.userEmail || record.userId || '系统任务'}</small></td>
        <td><strong>{record.modelId}</strong><small>{record.providerId} · {record.capability}</small></td>
        <td>{displayRequestId
          ? <code className="request-id" title={displayRequestId}>{displayRequestId}</code>
          : isCacheHit(record)
            ? <span>缓存命中，无需 SLS</span>
            : isLocallyBilled(record)
              ? <span>无需 SLS Request ID</span>
            : <span className="missing-request-id">未获取 Request ID</span>}</td>
        <td><span className={`compact-state compact-state--${match.tone}`}><i />{match.label}</span></td>
        <td className="numeric"><strong>{usageLabel}</strong><small>{usageDetail}</small></td>
        <td className="numeric billing-money"><strong>{formatMoney(estimatedCost, 6)}</strong><small>{record.priceCurrency}</small></td>
        <td className="numeric"><strong>{formatDuration(record.durationMs)}</strong><small>{record.status}</small></td>
      </tr>
    })}</tbody>
  </table></div><footer className="table-pagination" aria-label="请求明细分页">
    <span>第 {firstRecord}–{lastRecord} 条，共 {totalRecords.toLocaleString('zh-CN')} 条</span>
    <div><button type="button" aria-label="上一页" title="上一页" disabled={page <= 1} onClick={() => onPageChange(page - 1)}><ChevronLeft size={16} /></button><strong>第 {page} / {totalPages} 页</strong><button type="button" aria-label="下一页" title="下一页" disabled={page >= totalPages} onClick={() => onPageChange(page + 1)}><ChevronRight size={16} /></button></div>
  </footer></div>
}

function RealtimeTable({ sessions }: { sessions: UsageSession[] }) {
  if (sessions.length === 0) return <PanelMessage title="暂无 Realtime 会话" detail="用户开始练习后，这里会显示连接及 Request ID 绑定状态。" />
  return <div className="table-scroll"><table className="billing-table billing-session-table">
    <thead><tr><th>用户</th><th>本地会话</th><th>连接状态</th><th>Request ID</th><th>官方 Token</th><th>对账状态</th><th>时长</th></tr></thead>
    <tbody>{sessions.map((session) => <tr key={session.session_id}>
      <td><code title={session.user_id}>{session.user_id}</code><small>{session.plan_code ?? '未分组'}</small></td>
      <td><code title={session.session_id}>{session.session_id}</code><small title={session.task_uuid ?? ''}>{session.task_uuid ?? '无 task_uuid'}</small></td>
      <td><span className={`compact-state compact-state--${sessionTone(session.status)}`}><i />{sessionStatusLabels[normalize(session.status)] ?? session.status}</span></td>
      <td>{session.provider_request_id ? <code className="request-id" title={session.provider_request_id}>{session.provider_request_id}</code> : <span className="missing-request-id">等待 Provider 返回</span>}</td>
      <td className="numeric"><strong>{session.official_usage.total_tokens > 0 ? formatTokens(session.official_usage.total_tokens) : '—'}</strong><small>输入 {formatCount(session.official_usage.input_tokens)} · 输出 {formatCount(session.official_usage.output_tokens)}</small></td>
      <td><span className={`compact-state compact-state--${reconciliationTone(session.reconciliation_status)}`}><i />{reconciliationLabel(session.reconciliation_status)}</span></td>
      <td className="numeric"><strong>{session.measured_seconds.toFixed(1)} 秒</strong><small>{session.end_reason ?? (activeStatuses.has(normalize(session.status)) ? '进行中' : '已结束')}</small></td>
    </tr>)}</tbody>
  </table></div>
}

function matchState(record: InvocationUsage['records'][number], session?: UsageSession) {
  if (isLocallyBilled(record)) return { label: '本地已计费', tone: 'ok' }
  if (isCacheHit(record)) return { label: '缓存命中', tone: 'neutral' }
  if (record.status === 'FAILED') return { label: '调用失败', tone: 'danger' }
  if (record.usageSource === 'OFFICIAL' || session?.reconciliation_status === 'MATCHED') return { label: '官方已匹配', tone: 'ok' }
  if (!record.providerRequestId) return { label: '未绑定', tone: 'danger' }
  if (session?.reconciliation_status === 'MISMATCH') return { label: '官方记录有差异', tone: 'danger' }
  return { label: '等待官方日志', tone: 'waiting' }
}

function shouldDisplayRecord(
  record: InvocationUsage['records'][number],
  sessionById: Map<string, UsageSession>,
) {
  if (record.capability !== 'REALTIME') return true
  if (record.businessScene !== 'realtime_session') return false
  const session = record.sessionId ? sessionById.get(record.sessionId) : undefined
  return record.usageSource === 'OFFICIAL' || session?.reconciliation_status === 'MATCHED'
}

function isLocallyBilled(record: InvocationUsage['records'][number]) {
  return record.providerId === 'iflytek' && record.status === 'SUCCEEDED'
}

function isCacheHit(record: InvocationUsage['records'][number]) {
  return record.status === 'SUCCEEDED' && record.usageSource === 'NONE'
}

function reconciliationLabel(status: string) {
  if (status === 'MATCHED') return '官方已匹配'
  if (status === 'MISMATCH') return '官方记录有差异'
  return '等待官方日志'
}

function reconciliationTone(status: string) {
  if (status === 'MATCHED') return 'ok'
  if (status === 'MISMATCH') return 'danger'
  return 'waiting'
}

function sourceTone(state?: string) {
  const normalized = normalizeUpper(state)
  if (['READY', 'HEALTHY', 'ONLINE'].includes(normalized)) return 'ok'
  if (['CONFIGURATION_REQUIRED', 'DISABLED'].includes(normalized)) return 'waiting'
  if (['UNAVAILABLE', 'ERROR'].includes(normalized)) return 'danger'
  return 'neutral'
}

function sessionTone(status: string) {
  const normalized = normalize(status)
  if (normalized === 'active' || normalized === 'connected') return 'ok'
  if (normalized === 'failed') return 'danger'
  if (activeStatuses.has(normalized)) return 'waiting'
  return 'neutral'
}

function normalize(value?: string) {
  return String(value ?? '').trim().toLowerCase()
}

function normalizeUpper(value?: string) {
  return String(value ?? '').trim().toUpperCase()
}

function formatCount(value: number) {
  return value > 0 ? value.toLocaleString('zh-CN') : '—'
}

function formatTokens(value: number) {
  return value > 0 ? `${value.toLocaleString('zh-CN')} Token` : '—'
}

function formatCharacters(value: number) {
  return value > 0 ? `${value.toLocaleString('zh-CN')} Character` : '—'
}

function formatMoney(value: number, digits = 4) {
  return `¥${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: digits, maximumFractionDigits: digits })}`
}

function formatDuration(milliseconds: number) {
  if (milliseconds < 1_000) return `${milliseconds.toLocaleString('zh-CN')} ms`
  if (milliseconds < 60_000) return `${(milliseconds / 1_000).toFixed(1)} s`
  return `${(milliseconds / 60_000).toFixed(1)} min`
}

function formatSecondaryUsage(characters: number, audioSeconds: number) {
  if (characters > 0) return `${characters.toLocaleString('zh-CN')} 字符`
  if (audioSeconds > 0) return `${audioSeconds.toFixed(1)} 秒音频`
  return '无补充单位'
}

function PanelMessage({ title, detail, tone = 'neutral' }: { title: string; detail: string; tone?: 'neutral' | 'danger' }) {
  return <div className={`panel-message panel-message--${tone}`} role={tone === 'danger' ? 'alert' : 'status'}><strong>{title}</strong><p>{detail}</p></div>
}
