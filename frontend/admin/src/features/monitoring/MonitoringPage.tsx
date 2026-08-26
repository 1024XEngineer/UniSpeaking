import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Activity, AlertTriangle, Bell, CheckCircle2, Clock3, ExternalLink, Gauge, Monitor, RefreshCw, Server, Smartphone, UserRound } from 'lucide-react'
import { getMonitoringOverview, grafanaDashboards, grafanaUrl } from './monitoringApi'
import { formatDuration } from './monitoringFormat'

type ClientPlatform = 'web' | 'mobile' | 'backend'
const platformLabels: Record<ClientPlatform, string> = { web: 'Web', mobile: 'Mobile', backend: 'Backend' }
const platformDashboard = (platform: ClientPlatform) => platform === 'backend' ? grafanaDashboards.backend : grafanaDashboards.client
const platformParams = (platform: ClientPlatform) => ({ 'var-platform': platform, 'var-service': platform === 'backend' ? 'backend' : 'client-telemetry' })

const fmt = (value: number, digits = 1) => Number.isFinite(value) ? value.toLocaleString('zh-CN', { maximumFractionDigits: digits }) : '-'
const time = (value: string) => new Date(value).toLocaleString('zh-CN', { hour12: false })
function trendPoints(values: number[], max: number, width = 800, height = 160) {
  if (!values.length) return ''
  return values.map((value, index) => `${(index / Math.max(values.length - 1, 1)) * width},${height - (value / max) * (height - 8)}`).join(' ')
}

function Metric({ icon: Icon, label, value, note, tone = '' }: { icon: typeof Activity; label: string; value: string; note: string; tone?: string }) {
  return <div className="monitoring-metric"><span className={`monitoring-metric-icon ${tone}`}><Icon size={18} /></span><div><span>{label}</span><strong className={tone}>{value}</strong><small>{note}</small></div></div>
}

function MiniIcon({ platform }: { platform: string }) { return platform === 'mobile' ? <Smartphone size={13} /> : platform === 'backend' ? <Server size={13} /> : <Monitor size={13} /> }

export function MonitoringPage() {
  const [selectedPlatform, setSelectedPlatform] = useState<ClientPlatform>('web')
  const query = useQuery({ queryKey: ['admin', 'monitoring'], queryFn: getMonitoringOverview, refetchInterval: 15_000 })
  if (query.isError) return <section className="monitoring-empty"><strong>运行监控暂时无法读取</strong><span>请检查管理后端与线上 Grafana 数据源连接。</span><button className="button button--secondary" onClick={() => void query.refetch()}><RefreshCw size={15} />重试</button></section>
  const data = query.data
  if (!data) return <div className="monitoring-loading">正在同步线上监控数据…</div>
  const summary = data.summary
  const selectedSummary = data.platformSummaries.find(item => item.platform === selectedPlatform) ?? data.platformSummaries[0]
  const severe = summary.activeAlerts
  const trendValues = data.trend.flatMap(point => [point.clientErrors, point.slowRequests, point.backendErrors])
  const trendMax = Math.max(...trendValues, 1)
  return <div className="page-stack monitoring-page">
    <section className="monitoring-hero"><div><p className="eyebrow">OBSERVABILITY</p><h1>系统运行监控 <CheckCircle2 className="hero-check" size={19} /></h1><p>快速定位 Web、Mobile 和后端接口的真实问题，详细排查进入 Grafana 查看。</p></div><div className="monitoring-hero-actions"><span>最后更新：{time(summary.generatedAt)}</span><span className="environment-badge">生产环境</span><a className="button button--primary" href={grafanaUrl(grafanaDashboards.overview)} target="_blank" rel="noreferrer"><Activity size={15} />查看 Grafana 总览<ExternalLink size={13} /></a><a className="button button--secondary" href={grafanaUrl(grafanaDashboards.backend)} target="_blank" rel="noreferrer">服务与接口<ExternalLink size={13} /></a><a className="button button--secondary" href={grafanaUrl(grafanaDashboards.logs)} target="_blank" rel="noreferrer">日志溯源<ExternalLink size={13} /></a><a className="button button--secondary" href={grafanaUrl(grafanaDashboards.performance)} target="_blank" rel="noreferrer">BUG 与告警<ExternalLink size={13} /></a><button className="icon-command" onClick={() => void query.refetch()} aria-label="刷新监控" title="刷新监控"><RefreshCw size={16} /></button></div></section>

    <section className="monitoring-metrics monitoring-metrics--six">
      <Metric icon={Server} label="后端服务状态" value={summary.backendStatus === 'UP' ? '正常' : '异常'} note={summary.backendStatus === 'UP' ? '运行良好' : '请立即检查'} tone={summary.backendStatus === 'UP' ? 'ok' : 'danger'} />
      <Metric icon={AlertTriangle} label="API 请求错误率" value={`${fmt(summary.clientErrorRate)}%`} note="最近 5 分钟" tone={summary.clientErrorRate > 1 ? 'warning' : ''} />
      <Metric icon={Gauge} label="API 5xx 错误率" value={`${fmt(summary.api5xxRate)}%`} note="最近 5 分钟" tone={summary.api5xxRate > 1 ? 'danger' : ''} />
      <Metric icon={Clock3} label="API P95" value={formatDuration(summary.apiP95Seconds)} note="近 24 小时" tone={summary.apiP95Seconds > 3 ? 'warning' : ''} />
      <Metric icon={Bell} label="未关闭问题" value={String(summary.activeAlerts)} note="待处理问题" tone={summary.activeAlerts ? 'danger' : 'ok'} />
      <Metric icon={UserRound} label="受影响用户" value={String(summary.affectedUsers)} note="近 24 小时" tone={summary.affectedUsers ? 'warning' : 'ok'} />
    </section>

    <section className="monitoring-main-grid"><div className="surface-panel monitoring-panel trend-panel"><div className="panel-heading"><div><h2>核心趋势（24h）</h2><span>API 错误 · 慢请求 · 后端异常</span></div><select aria-label="趋势时间范围" defaultValue="24h"><option value="24h">近 24 小时</option></select></div><div className="trend-legend"><span className="trend-legend--green">API 错误</span><span className="trend-legend--orange">慢请求</span><span className="trend-legend--red">后端异常</span></div><div className="trend-chart" aria-label="核心趋势图"><div className="trend-gridlines"><i /><i /><i /><i /></div><svg viewBox="0 0 800 160" preserveAspectRatio="none" role="img"><polyline className="trend-line trend-line--green" points={trendPoints(data.trend.map(point => point.clientErrors), trendMax)} /><polyline className="trend-line trend-line--orange" points={trendPoints(data.trend.map(point => point.slowRequests), trendMax)} /><polyline className="trend-line trend-line--red" points={trendPoints(data.trend.map(point => point.backendErrors), trendMax)} /></svg><div className="trend-axis"><span>24h 前</span><span>18h 前</span><span>12h 前</span><span>6h 前</span><span>现在</span></div></div></div>
      <div className="surface-panel monitoring-panel problem-summary"><div className="panel-heading"><div><h2>问题摘要</h2><span>生产数据库与 Prometheus 实时聚合</span></div></div><div className="summary-counters"><div><AlertTriangle size={17} /><span>未关闭问题</span><strong>{severe}</strong></div><div><Activity size={17} /><span>错误问题</span><strong>{data.problems.length}</strong></div><div><Bell size={17} /><span>近 7 天异常事件</span><strong>{data.recentEvents.length}</strong></div><div><CheckCircle2 size={17} /><span>近 7 天解决 BUG</span><strong>{summary.resolvedBugs7d}</strong></div></div><ul className="summary-list"><li>问题列表按近 7 天接口错误聚合，避免真实历史问题被隐藏</li><li>当前后端 5xx 错误率为 {fmt(summary.api5xxRate)}%</li><li>慢请求按耗时超过 1 秒统计，详情进入 Grafana</li></ul><a className="text-link" href={grafanaUrl(grafanaDashboards.performance)} target="_blank" rel="noreferrer">查看 BUG 与告警 <span>→</span> Grafana</a></div></section>

    <section className="monitoring-three-grid"><div className="surface-panel monitoring-panel compact-table"><PanelTitle title="错误接口 TOP 5" href={grafanaDashboards.logs} /><table><thead><tr><th>接口</th><th>平台</th><th>次数</th><th>最近发生</th></tr></thead><tbody>{data.problems.slice(0, 5).map(item => <tr key={`${item.platform}-${item.path}-${item.problem}`}><td><a href={grafanaUrl(grafanaDashboards.logs, { 'var-platform': item.platform, 'var-api_path': item.path, 'var-search': item.path || item.problem })} target="_blank" rel="noreferrer"><code>{item.path || item.problem}</code></a></td><td><MiniIcon platform={item.platform} /> {item.platform}</td><td>{item.count}</td><td>{time(item.lastSeen)}</td></tr>)}</tbody></table>{!data.problems.length && <EmptyText text="暂无接口错误" />}</div><div className="surface-panel monitoring-panel compact-table"><PanelTitle title="慢接口 TOP 5" href={grafanaDashboards.backend} /><table><thead><tr><th>接口</th><th>平均</th><th>P95</th><th>慢请求数</th></tr></thead><tbody>{data.slowEndpoints.map(item => <tr key={`${item.method}-${item.path}`}><td><a href={grafanaUrl(grafanaDashboards.backend, { 'var-api_path': item.path })} target="_blank" rel="noreferrer"><strong>{item.method}</strong> <code>{item.path}</code></a></td><td className={item.averageSeconds > 1 ? 'cell-danger' : ''}>{formatDuration(item.averageSeconds)}</td><td className={item.p95Seconds > 1 ? 'cell-danger' : ''}>{formatDuration(item.p95Seconds)}</td><td>{item.slowCount}</td></tr>)}</tbody></table>{!data.slowEndpoints.length && <EmptyText text="暂无耗时样本" />}</div><div className="surface-panel monitoring-panel client-overview"><PanelTitle title="客户端异常概览" href={platformDashboard(selectedPlatform)} params={platformParams(selectedPlatform)} /><div className="client-tabs" role="tablist" aria-label="异常平台分类">{(['web', 'mobile', 'backend'] as ClientPlatform[]).map(platform => <button key={platform} type="button" role="tab" aria-selected={selectedPlatform === platform} className={selectedPlatform === platform ? 'active' : ''} onClick={() => setSelectedPlatform(platform)}>{platformLabels[platform]}</button>)}</div><div className="client-stats"><div><span>{selectedPlatform === 'web' ? '页面事件 P95' : selectedPlatform === 'mobile' ? '移动端事件 P95' : '后端事件 P95'}</span><strong>{fmt(selectedSummary?.p95DurationMs ?? 0)} ms</strong></div><div><span>近 24h 错误事件</span><strong>{selectedSummary?.errorCount ?? 0}</strong></div><div><span>近 24h 异常用户</span><strong>{selectedSummary?.affectedUsers ?? 0}</strong></div></div><a className="text-link" href={grafanaUrl(grafanaDashboards.logs, platformParams(selectedPlatform))} target="_blank" rel="noreferrer">查看 {platformLabels[selectedPlatform]} 日志 <span>→</span></a></div></section>

    <section className="surface-panel monitoring-panel recent-events"><PanelTitle title="最近异常事件" href={grafanaDashboards.logs} /><div className="table-scroll"><table><thead><tr><th>时间</th><th>平台</th><th>用户</th><th>页面或接口</th><th>错误摘要</th><th>状态</th><th>日志</th></tr></thead><tbody>{data.recentEvents.slice(0, 6).map((item, index) => <tr key={`${item.timestamp}-${index}`}><td>{time(item.timestamp)}</td><td><MiniIcon platform={item.platform} /> {item.platform}</td><td>{item.userId}</td><td>{item.apiPath || item.page}</td><td>{item.errorMessage || item.errorType}</td><td><span className={`severity-pill severity-pill--${(item.httpStatus || 0) >= 500 ? 'high' : 'medium'}`}>{(item.httpStatus || 0) >= 500 ? '严重' : '中'}</span></td><td><a className="event-log-link" href={grafanaUrl(grafanaDashboards.logs, { 'var-platform': item.platform, 'var-api_path': item.apiPath, 'var-search': item.requestId !== '-' ? item.requestId : (item.apiPath || item.errorMessage) })} target="_blank" rel="noreferrer">查看<ExternalLink size={11} /></a></td></tr>)}</tbody></table></div>{!data.recentEvents.length && <EmptyText text="当前没有异常事件" />}</section>
  </div>
}

function PanelTitle({ title, href, params = {} }: { title: string; href: string; params?: Record<string, string> }) { return <div className="panel-heading"><h2>{title}</h2><a href={grafanaUrl(href, params)} target="_blank" rel="noreferrer">更多 <span>›</span></a></div> }
function EmptyText({ text }: { text: string }) { return <p className="monitoring-no-data">{text}</p> }
