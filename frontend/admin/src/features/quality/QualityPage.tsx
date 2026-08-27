import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Activity, Bug, CheckCircle2, Plus, RefreshCw, Wrench, X } from 'lucide-react'
import {
  createQualityIssue,
  getQualitySummary,
  listQualityEvents,
  listQualityIssues,
  updateQualityIssue,
  type CreateQualityIssue,
  type IssuePlatform,
  type IssueSeverity,
  type IssueStatus,
  type IssueType,
  type QualityIssue,
} from './qualityApi'

const statusLabels: Record<IssueStatus, string> = {
  OPEN: '待处理', INVESTIGATING: '排查中', IN_PROGRESS: '修复中',
  RESOLVED: '已解决', VERIFIED: '已验证', IGNORED: '已忽略',
}
const severityLabels: Record<IssueSeverity, string> = {
  CRITICAL: '严重', HIGH: '高', MEDIUM: '中', LOW: '低',
}
const platformLabels: Record<IssuePlatform, string> = {
  WEB: 'Web', MOBILE: '移动端', BACKEND: '后端', CROSS_PLATFORM: '跨端',
}

const initialCreate: CreateQualityIssue = {
  issueType: 'BUG', platform: 'WEB', severity: 'MEDIUM', status: 'OPEN', title: '', description: '', assignee: '',
}

function formatDate(value: string | null) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '尚无事件'
}

export function QualityPage() {
  const queryClient = useQueryClient()
  const [status, setStatus] = useState<IssueStatus | ''>('')
  const [platform, setPlatform] = useState<IssuePlatform | ''>('')
  const [issueType, setIssueType] = useState<IssueType | ''>('')
  const [issuePage, setIssuePage] = useState(1)
  const [createOpen, setCreateOpen] = useState(false)
  const [selected, setSelected] = useState<QualityIssue | null>(null)
  const [draft, setDraft] = useState<CreateQualityIssue>(initialCreate)

  const filters = useMemo(() => ({
    status: status || undefined,
    platform: platform || undefined,
    issueType: issueType || undefined,
  }), [issueType, platform, status])
  const summary = useQuery({ queryKey: ['quality', 'summary'], queryFn: getQualitySummary, refetchInterval: 15_000 })
  const issues = useQuery({ queryKey: ['quality', 'issues', filters], queryFn: () => listQualityIssues(filters), refetchInterval: 15_000 })
  const pageSize = 20
  const totalIssuePages = Math.max(1, Math.ceil((issues.data?.length ?? 0) / pageSize))
  const visibleIssues = (issues.data ?? []).slice((issuePage - 1) * pageSize, issuePage * pageSize)
  const events = useQuery({
    queryKey: ['quality', 'events', selected?.issueId],
    queryFn: () => listQualityEvents(selected!.issueId),
    enabled: Boolean(selected?.issueId),
  })
  const refresh = async () => {
    await queryClient.invalidateQueries({ queryKey: ['quality'] })
  }
  const createMutation = useMutation({
    mutationFn: createQualityIssue,
    onSuccess: async (created) => {
      setCreateOpen(false)
      setDraft(initialCreate)
      setSelected(created)
      await refresh()
    },
  })
  const updateMutation = useMutation({
    mutationFn: ({ issueId, body }: { issueId: string; body: Parameters<typeof updateQualityIssue>[1] }) =>
      updateQualityIssue(issueId, body),
    onSuccess: async (updated) => {
      setSelected(updated)
      await refresh()
    },
  })

  const metrics = summary.data ? [
    { label: '活跃问题', value: summary.data.activeIssues, hint: '待处理、排查中和修复中' },
    { label: '严重问题', value: summary.data.criticalIssues, hint: '尚未验证或忽略' },
    { label: '7 天错误事件', value: summary.data.events7d, hint: `${summary.data.affectedUsers7d} 位受影响用户` },
    { label: '7 天已解决', value: summary.data.resolved7d, hint: `${summary.data.optimizations} 项优化进行中` },
  ] : []

  useEffect(() => { if (issuePage > totalIssuePages) setIssuePage(totalIssuePages) }, [issuePage, totalIssuePages])
  return (
    <div className="page-stack quality-page">
      <section className="page-heading compact-heading">
        <div><p className="eyebrow">QUALITY OPERATIONS</p><h1>BUG 与优化</h1><p>汇总 Web、移动端和 Java 后端的真实错误，并维护负责人、处理状态与修复结论。</p></div>
        <div className="quality-actions">
          <button className="icon-command" type="button" onClick={() => void refresh()} aria-label="刷新质量数据" title="刷新质量数据"><RefreshCw size={18} /></button>
          <button className="secondary-button command-button" type="button" onClick={() => setCreateOpen(true)}><Plus size={17} />新增问题</button>
        </div>
      </section>

      <section className="metric-line" aria-label="质量核心指标">
        {summary.isLoading && <div className="inline-loading"><span className="loading-mark" />正在读取质量数据</div>}
        {metrics.map((metric) => <div className="metric" key={metric.label}><span>{metric.label}</span><strong>{metric.value.toLocaleString('zh-CN')}</strong><small>{metric.hint}</small></div>)}
      </section>

      <section className="surface-panel quality-board">
        <header className="quality-toolbar">
          <div className="quality-toolbar__title"><Activity size={18} /><div><strong>问题清单</strong><span>{issues.data?.length ?? 0} 条当前结果</span></div></div>
          <div className="quality-filters">
            <select aria-label="问题类型" value={issueType} onChange={(event) => { setIssueType(event.target.value as IssueType | ''); setIssuePage(1) }}><option value="">全部类型</option><option value="BUG">BUG</option><option value="OPTIMIZATION">优化</option></select>
            <select aria-label="平台" value={platform} onChange={(event) => { setPlatform(event.target.value as IssuePlatform | ''); setIssuePage(1) }}><option value="">全部平台</option>{Object.entries(platformLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select>
            <select aria-label="处理状态" value={status} onChange={(event) => { setStatus(event.target.value as IssueStatus | ''); setIssuePage(1) }}><option value="">全部状态</option>{Object.entries(statusLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select>
          </div>
        </header>
        {issues.isError && <div className="quality-empty"><strong>质量数据暂时无法读取</strong><span>请检查 Java 后端与 PostgreSQL。</span></div>}
        {!issues.isError && !issues.isLoading && !issues.data?.length && <div className="quality-empty"><CheckCircle2 size={28} /><strong>当前筛选条件下没有问题</strong><span>新错误会自动聚合，也可以手工新增优化项。</span></div>}
        {issues.isLoading && <div className="inline-loading quality-loading"><span className="loading-mark" />正在载入问题</div>}
        {!!issues.data?.length && <><div className="table-scroll"><table className="data-table quality-table"><thead><tr><th>问题</th><th>平台</th><th>级别</th><th>状态</th><th>影响</th><th>最近发生</th><th>负责人</th></tr></thead><tbody>{visibleIssues.map((issue) => (
          <tr key={issue.issueId} onClick={() => setSelected(issue)} tabIndex={0} onKeyDown={(event) => { if (event.key === 'Enter') setSelected(issue) }}>
            <td className="quality-title-cell"><span className={`quality-kind quality-kind--${issue.issueType.toLowerCase()}`}>{issue.issueType === 'BUG' ? <Bug size={14} /> : <Wrench size={14} />}{issue.issueType === 'BUG' ? 'BUG' : '优化'}</span><strong>{issue.title}</strong><small>{issue.apiPath || issue.errorCode || (issue.source === 'TELEMETRY' ? '自动聚合' : '手工创建')}</small></td>
            <td>{platformLabels[issue.platform]}</td><td><span className={`severity severity--${issue.severity.toLowerCase()}`}>{severityLabels[issue.severity]}</span></td><td><span className={`state-badge state-badge--${['RESOLVED', 'VERIFIED'].includes(issue.status) ? 'ok' : issue.status === 'IGNORED' ? 'neutral' : 'waiting'}`}>{statusLabels[issue.status]}</span></td>
            <td className="numeric"><strong>{issue.occurrenceCount}</strong><small>{issue.affectedUsers} 位用户</small></td><td>{formatDate(issue.lastSeenAt || issue.updatedAt)}</td><td>{issue.assignee || '未分配'}</td>
          </tr>
        ))}</tbody></table></div><div className="list-pagination"><span>共 {issues.data.length} 条，第 {issuePage} / {totalIssuePages} 页</span><button type="button" disabled={issuePage <= 1} onClick={() => setIssuePage(page => page - 1)}>上一页</button><button type="button" disabled={issuePage >= totalIssuePages} onClick={() => setIssuePage(page => page + 1)}>下一页</button></div></>}
      </section>

      {createOpen && <IssueDialog title="新增 BUG 或优化" onClose={() => setCreateOpen(false)}>
        <IssueForm draft={draft} onChange={setDraft} />
        {createMutation.isError && <p className="form-error">{createMutation.error.message}</p>}
        <div className="dialog-actions"><button className="quiet-button" type="button" onClick={() => setCreateOpen(false)}>取消</button><button className="primary-button compact-primary" type="button" disabled={!draft.title.trim() || createMutation.isPending} onClick={() => createMutation.mutate(draft)}>创建记录</button></div>
      </IssueDialog>}

      {selected && <IssueDetail issue={selected} events={events.data || []} loadingEvents={events.isLoading} saving={updateMutation.isPending} onClose={() => setSelected(null)} onSave={(body) => updateMutation.mutate({ issueId: selected.issueId, body })} />}
    </div>
  )
}

function IssueDialog({ title, onClose, children }: { title: string; onClose: () => void; children: ReactNode }) {
  return <div className="dialog-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose() }}><section className="quality-dialog" role="dialog" aria-modal="true" aria-label={title}><header><div><p className="eyebrow">QUALITY RECORD</p><h2>{title}</h2></div><button className="icon-command" type="button" onClick={onClose} aria-label="关闭"><X size={19} /></button></header>{children}</section></div>
}

function IssueForm({ draft, onChange }: { draft: CreateQualityIssue; onChange: (draft: CreateQualityIssue) => void }) {
  return <div className="quality-form"><div className="quality-form-grid"><label>类型<select value={draft.issueType} onChange={(event) => onChange({ ...draft, issueType: event.target.value as IssueType })}><option value="BUG">BUG</option><option value="OPTIMIZATION">优化</option></select></label><label>平台<select value={draft.platform} onChange={(event) => onChange({ ...draft, platform: event.target.value as IssuePlatform })}>{Object.entries(platformLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label><label>级别<select value={draft.severity} onChange={(event) => onChange({ ...draft, severity: event.target.value as IssueSeverity })}>{Object.entries(severityLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label><label>状态<select value={draft.status} onChange={(event) => onChange({ ...draft, status: event.target.value as IssueStatus })}>{Object.entries(statusLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label></div><label>标题<input value={draft.title} maxLength={200} onChange={(event) => onChange({ ...draft, title: event.target.value })} placeholder="简明描述问题或优化目标" /></label><label>详细情况<textarea value={draft.description} onChange={(event) => onChange({ ...draft, description: event.target.value })} placeholder="复现条件、影响范围、验收标准" /></label><label>负责人<input value={draft.assignee} maxLength={120} onChange={(event) => onChange({ ...draft, assignee: event.target.value })} placeholder="姓名或团队" /></label></div>
}

function IssueDetail({ issue, events, loadingEvents, saving, onClose, onSave }: { issue: QualityIssue; events: Awaited<ReturnType<typeof listQualityEvents>>; loadingEvents: boolean; saving: boolean; onClose: () => void; onSave: (body: Parameters<typeof updateQualityIssue>[1]) => void }) {
  const [status, setStatus] = useState(issue.status)
  const [assignee, setAssignee] = useState(issue.assignee || '')
  const [resolution, setResolution] = useState(issue.resolution || '')
  const [note, setNote] = useState('')
  return <IssueDialog title={issue.title} onClose={onClose}><div className="quality-detail-meta"><span>{platformLabels[issue.platform]}</span><span>{severityLabels[issue.severity]}</span><span>{issue.occurrenceCount} 次</span><span>{issue.affectedUsers} 位用户</span><span>{issue.release || '无版本信息'}</span></div><div className="quality-form quality-update-form"><div className="quality-form-grid"><label>处理状态<select value={status} onChange={(event) => setStatus(event.target.value as IssueStatus)}>{Object.entries(statusLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label><label>负责人<input value={assignee} onChange={(event) => setAssignee(event.target.value)} placeholder="姓名或团队" /></label></div><label>修复或优化结论<textarea value={resolution} onChange={(event) => setResolution(event.target.value)} placeholder="记录处理方案、发布版本与验证结果" /></label><label>本次操作备注<input value={note} onChange={(event) => setNote(event.target.value)} placeholder="例如：已提交修复，等待灰度验证" /></label><button className="primary-button compact-primary" type="button" disabled={saving} onClick={() => onSave({ status, assignee, resolution, note })}>保存进展</button></div><section className="event-list"><header><strong>最近错误事件</strong><span>{loadingEvents ? '读取中' : `${events.length} 条`}</span></header>{!loadingEvents && !events.length && <p>手工问题暂无自动错误事件。</p>}{events.slice(0, 20).map((event) => <article key={event.eventId}><div><strong>{event.message || event.eventType}</strong><time>{formatDate(event.occurredAt)}</time></div><p>{[event.apiMethod, event.apiPath, event.httpStatus, event.errorCode].filter(Boolean).join(' · ') || event.route || '无接口信息'}</p><small>{[event.deviceModel, event.osName, event.osVersion, event.networkType].filter(Boolean).join(' · ') || event.release || '无设备信息'}</small></article>)}</section></IssueDialog>
}
