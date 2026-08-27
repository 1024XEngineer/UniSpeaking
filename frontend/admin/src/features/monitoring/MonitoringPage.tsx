import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import {
  Activity, AlertTriangle, Bell, CheckCircle2, Clock3, ExternalLink, Gauge,
  Monitor, RefreshCw, Server, Smartphone, UserRound,
} from 'lucide-react'
import {
  getMonitoringOverview, grafanaDashboards, grafanaUrl,
  type MonitoringTrendPoint, type PerformanceStatus,
} from './monitoringApi'
import { formatMilliseconds } from './monitoringFormat'

type ClientPlatform = 'web' | 'mobile' | 'backend'

const platformLabels: Record<ClientPlatform, string> = { web: 'Web', mobile: 'Mobile', backend: 'Backend' }
const statusLabels: Record<PerformanceStatus, string> = { OPTIMIZED: '已优化', OBSERVING: '观察中', PENDING: '待处理', REGRESSED: '已回退' }
const platformDashboard = (platform: ClientPlatform) => platform === 'backend' ? grafanaDashboards.backend : grafanaDashboards.client
const platformParams = (platform: ClientPlatform) => ({
  'var-platform': platform, 'var-user_id': '.*', 'var-api_path': '.*', 'var-request_id': '.*',
  'var-service': platform === 'backend' ? 'backend' : 'client-telemetry',
})
const backendGrafanaParams = {
  'var-platform': 'web', 'var-user_id': '.*', 'var-api_path': '.*', 'var-request_id': '.*',
  'var-slow_threshold': '1.0', 'var-service': 'backend', 'var-level': '.*', 'var-http_status': '.*', 'var-search': '.*',
}
const performanceGrafanaParams = {
  'var-platform': 'web', 'var-user_id': '.*', 'var-api_path': '.*', 'var-request_id': '.*', 'var-slow_threshold': '1.0',
}

const fmt = (value: number, digits = 1) => Number.isFinite(value)
  ? value.toLocaleString('zh-CN', { maximumFractionDigits: digits })
  : '—'
const time = (value: string) => new Date(value).toLocaleString('zh-CN', { hour12: false })
const trendTime = (timestamp: number) => new Date(timestamp * 1000).toLocaleString('zh-CN', {
  month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false,
})

function Metric({ icon: Icon, label, value, note, tone = '' }: {
  icon: typeof Activity; label: string; value: string; note: string; tone?: string
}) {
  return <article className="monitoring-metric">
    <span className={`monitoring-metric-icon ${tone}`}><Icon size={20} /></span>
    <div><span>{label}</span><strong className={tone}>{value}</strong><small>{note}</small></div>
  </article>
}

function MiniIcon({ platform }: { platform: string }) {
  return platform === 'mobile' ? <Smartphone size={13} /> : platform === 'backend' ? <Server size={13} /> : <Monitor size={13} />
}

function TrendChart({ points }: { points: MonitoringTrendPoint[] }) {
  const series = [
    { key: 'clientErrors' as const, label: '客户端错误', tone: 'green', format: (v: number) => `${fmt(v, 0)} 次` },
    { key: 'backendErrors' as const, label: '后端错误', tone: 'orange', format: (v: number) => `${fmt(v, 0)} 次` },
    { key: 'slowRequests' as const, label: '慢请求', tone: 'blue', format: (v: number) => `${fmt(v, 0)} 次` },
  ]
  const validPoints = points.filter(point => series.some(item => point[item.key] != null && Number.isFinite(point[item.key]!)))
  if (!validPoints.length) return <div className="trend-empty trend-empty--large"><div className="trend-legend">{series.map(item => <span key={item.key}><i className={`trend-legend-dot trend-legend-dot--${item.tone}`} />{item.label}</span>)}</div><strong>最近 24 小时暂无趋势样本</strong><span>请稍后刷新或查看 Grafana 真实数据</span></div>
  const coords = series.map(item => {
    const values = validPoints.map(point => point[item.key]).filter((v): v is number => v != null && Number.isFinite(v))
    const min = Math.min(...values, 0)
    const max = Math.max(...values, 1)
    return { ...item, points: validPoints.map((point, index) => {
      const value = point[item.key]
      if (value == null) return null
      const x = validPoints.length === 1 ? 50 : index / (validPoints.length - 1) * 100
      const y = 88 - ((value - min) / (max - min || 1)) * 76
      return { x, y, value, timestamp: point.timestamp }
    }).filter(Boolean) as Array<{ x: number; y: number; value: number; timestamp: number }> }
  })
  return <div className="trend-chart-wrap">
    <div className="trend-legend">{coords.map(item => <span key={item.key}><i className={`trend-legend-dot trend-legend-dot--${item.tone}`} />{item.label}</span>)}</div>
    <svg className="trend-chart" viewBox="0 0 100 100" preserveAspectRatio="none" role="img" aria-label="客户端错误、后端错误与慢请求趋势">
      {[12, 38, 64, 88].map(y => <line key={y} x1="0" y1={y} x2="100" y2={y} className="trend-gridline" />)}
      {coords.map(item => <g key={item.key}><polyline className={`trend-series trend-series--${item.tone}`} points={item.points.map(point => `${point.x},${point.y}`).join(' ')} />{item.points.map(point => <circle key={`${item.key}-${point.timestamp}`} className={`trend-dot trend-dot--${item.tone}`} cx={point.x} cy={point.y} r="1.5"><title>{`${trendTime(point.timestamp)} · ${item.label}: ${item.format(point.value)}`}</title></circle>)}</g>)}
    </svg>
    <div className="trend-axis"><span>{trendTime(validPoints[0].timestamp)}</span><span>各指标按自身量纲展示</span><span>{trendTime(validPoints.at(-1)!.timestamp)}</span></div>
  </div>
}

export function MonitoringPage() {
  const [selectedPlatform, setSelectedPlatform] = useState<ClientPlatform>('web')
  const query = useQuery({
    queryKey: ['admin', 'monitoring', '24h'],
    queryFn: () => getMonitoringOverview('24h'),
    refetchInterval: 15_000,
  })
  if (query.isError) return <section className="monitoring-empty">
    <strong>运行监控暂时无法读取</strong><span>请检查管理后端与线上 Grafana 数据源连接。</span>
    <button className="button button--secondary" onClick={() => void query.refetch()}><RefreshCw size={15} />重试</button>
  </section>
  const data = query.data
  if (!data) return <div className="monitoring-loading">正在同步线上监控数据…</div>
  const { summary } = data
  const selectedSummary = data.platformSummaries.find(item => item.platform === selectedPlatform)

  return <div className="page-stack monitoring-page">
    <section className="monitoring-hero">
      <div><p className="eyebrow">OBSERVABILITY</p><h1>系统运行监控 <CheckCircle2 className="hero-check" size={19} /></h1><p>快速定位 Web、Mobile 和后端接口的真实问题，详细排查进入 Grafana 查看。</p></div>
      <div className="monitoring-hero-actions">
        <span>最后更新：{time(summary.generatedAt)}</span><span className="environment-badge">生产环境</span>
        <a className="button button--primary" href={grafanaUrl(grafanaDashboards.overview)} target="_blank" rel="noreferrer"><Activity size={15} />Grafana 总览<ExternalLink size={13} /></a>
        <a className="button button--secondary" href={grafanaUrl(grafanaDashboards.backend)} target="_blank" rel="noreferrer">服务与接口<ExternalLink size={13} /></a>
        <a className="button button--secondary" href={grafanaUrl(grafanaDashboards.logs)} target="_blank" rel="noreferrer">日志溯源<ExternalLink size={13} /></a>
        <a className="button button--secondary" href={grafanaUrl(grafanaDashboards.performance)} target="_blank" rel="noreferrer">BUG 与告警<ExternalLink size={13} /></a>
        <button className="icon-command" onClick={() => void query.refetch()} aria-label="刷新监控" title="刷新监控"><RefreshCw size={16} /></button>
      </div>
    </section>

    <section className="monitoring-metrics monitoring-metrics--six">
      <Metric icon={Server} label="后端服务状态" value={summary.backendStatus === 'UP' ? '正常' : '异常'} note={summary.backendStatus === 'UP' ? '当前请求可达' : '请立即检查'} tone={summary.backendStatus === 'UP' ? 'ok' : 'danger'} />
      <Metric icon={AlertTriangle} label="API 错误率" value={`${fmt(summary.apiErrorRate5m)}%`} note="最近 5 分钟" tone={summary.apiErrorRate5m > 3 ? 'danger' : summary.apiErrorRate5m > 1 ? 'warning' : ''} />
      <Metric icon={Gauge} label="API 5xx 数量" value={fmt(summary.api5xxCount24h, 0)} note="最近 24 小时" tone={summary.api5xxCount24h > 0 ? 'danger' : ''} />
      <Metric icon={Clock3} label="API P95" value={formatMilliseconds(summary.apiP95Milliseconds24h)} note="最近 24 小时" tone={summary.apiP95Milliseconds24h > 1000 ? 'warning' : ''} />
      <Metric icon={Bell} label="未关闭问题" value={fmt(summary.activeAlerts, 0)} note="待治理问题" tone={summary.activeAlerts ? 'danger' : 'ok'} />
      <Metric icon={UserRound} label="受影响用户" value={fmt(summary.affectedUsers24h, 0)} note="最近 24 小时" tone={summary.affectedUsers24h ? 'warning' : 'ok'} />
    </section>

    <section className="monitoring-main-grid">
      <div className="surface-panel monitoring-panel trend-panel">
        <div className="panel-heading"><div><h2>核心趋势</h2><span>最近 24 小时真实接口调用：客户端错误、后端错误与慢请求</span></div></div>
        <TrendChart points={data.trend} />
      </div>

    </section>

    <section className="monitoring-detail-grid">
      <div className="surface-panel monitoring-panel monitoring-table-panel error-endpoint-panel">
        <PanelTitle title="错误接口 TOP 5" href={grafanaDashboards.logs} params={backendGrafanaParams} />
        <div className="table-scroll"><table className="error-endpoint-table"><thead><tr><th>接口</th><th>平台</th><th>错误数</th><th>影响用户</th><th>最近发生</th></tr></thead><tbody>
          {data.problems.slice(0, 5).map(item => <tr key={`${item.platform}-${item.path}-${item.problem}`}>
            <td><a title={item.path || item.problem} href={grafanaUrl(grafanaDashboards.logs, { ...backendGrafanaParams, 'var-platform': item.platform, 'var-api_path': item.path, 'var-search': item.path || item.problem })} target="_blank" rel="noreferrer"><code>{item.path || item.problem}</code></a></td>
            <td><MiniIcon platform={item.platform} /> {item.platform}</td><td className="error-count">{item.count}</td><td>{item.affectedUsers}</td><td>{time(item.lastSeen)}</td>
          </tr>)}
        </tbody></table></div>{!data.problems.length && <EmptyText text="当前范围暂无接口错误" />}
      </div>

      <div className="surface-panel monitoring-panel monitoring-table-panel performance-panel">
        <PanelTitle title="性能优化 TOP 5" subtitle="上一 24h vs 最近 24h（P95 环比）" href={grafanaDashboards.performance} params={performanceGrafanaParams} />
        <div className="table-scroll"><table><thead><tr><th>接口</th><th>上期 P95</th><th>当前 P95</th><th>改善幅度</th><th>状态</th></tr></thead><tbody>
          {data.performanceEndpoints.map(item => <tr key={`${item.method}-${item.path}`}>
            <td><a title={`${item.method} ${item.path}`} href={grafanaUrl(grafanaDashboards.performance, { ...performanceGrafanaParams, 'var-api_path': item.path })} target="_blank" rel="noreferrer"><strong>{item.method}</strong> <code>{item.path}</code></a></td>
            <td>{formatMilliseconds(item.previousPeriodP95Milliseconds)}</td><td>{formatMilliseconds(item.currentPeriodP95Milliseconds)}</td>
            <td className={item.improvementRate == null ? '' : item.improvementRate >= 0 ? 'cell-positive' : 'cell-danger'}>{item.improvementRate == null ? '—' : `${item.improvementRate > 0 ? '+' : ''}${fmt(item.improvementRate)}%`}</td>
            <td><span className={`performance-status performance-status--${item.status.toLowerCase()}`}>{statusLabels[item.status]}</span></td>
          </tr>)}
        </tbody></table></div>{!data.performanceEndpoints.length && <EmptyText text="暂无可对比的 P95 基准；采集满两个 24 小时周期后自动展示" />}
        <a className="text-link slow-link" href={grafanaUrl(grafanaDashboards.performance)} target="_blank" rel="noreferrer">查看当前慢接口 →</a>
      </div>

      <div className="surface-panel monitoring-panel client-overview">
        <PanelTitle title="客户端异常概览" href={platformDashboard(selectedPlatform)} params={platformParams(selectedPlatform)} />
        <div className="client-tabs" role="tablist" aria-label="异常平台分类">{(['web', 'mobile', 'backend'] as ClientPlatform[]).map(platform => <button key={platform} type="button" role="tab" aria-selected={selectedPlatform === platform} className={selectedPlatform === platform ? 'active' : ''} onClick={() => setSelectedPlatform(platform)}>{platformLabels[platform]}</button>)}</div>
        <div className="client-stats"><div><span>事件耗时 P95</span><strong>{formatMilliseconds(selectedSummary?.p95DurationMs)}</strong></div><div><span>近 24h 错误事件</span><strong>{selectedSummary?.errorCount ?? 0}</strong></div><div><span>近 24h 异常用户</span><strong>{selectedSummary?.affectedUsers ?? 0}</strong></div></div>
        <a className="text-link" href={grafanaUrl(grafanaDashboards.logs, platformParams(selectedPlatform))} target="_blank" rel="noreferrer">查看 {platformLabels[selectedPlatform]} 日志 <span>→</span></a>
      </div>
    </section>

    <section className="surface-panel monitoring-panel recent-events">
      <PanelTitle title="最近异常事件" href={grafanaDashboards.logs} />
      <div className="table-scroll"><table><thead><tr><th>时间</th><th>平台</th><th>页面或接口</th><th>错误摘要</th><th>级别</th><th>日志</th></tr></thead><tbody>{data.recentEvents.slice(0, 5).map((item, index) => <tr key={`${item.timestamp}-${index}`}><td>{time(item.timestamp)}</td><td><MiniIcon platform={item.platform} /> {item.platform}</td><td title={item.apiPath || item.page}>{item.apiPath || item.page}</td><td title={item.errorMessage || item.errorType}>{item.errorMessage || item.errorType}</td><td><span className={`severity-pill severity-pill--${(item.httpStatus || 0) >= 500 ? 'high' : 'medium'}`}>{(item.httpStatus || 0) >= 500 ? '严重' : '中'}</span></td><td><a className="event-log-link" href={grafanaUrl(grafanaDashboards.logs, { 'var-platform': item.platform, 'var-api_path': item.apiPath, 'var-search': item.requestId !== '-' ? item.requestId : (item.apiPath || item.errorMessage) })} target="_blank" rel="noreferrer">查看<ExternalLink size={11} /></a></td></tr>)}</tbody></table></div>
      {!data.recentEvents.length && <EmptyText text="当前没有异常事件" />}
    </section>
  </div>
}

function PanelTitle({ title, subtitle, href, params = {} }: { title: string; subtitle?: string; href: string; params?: Record<string, string> }) {
  return <div className="panel-heading"><div><h2>{title}</h2>{subtitle && <span>{subtitle}</span>}</div><a href={grafanaUrl(href, params)} target="_blank" rel="noreferrer">更多 <span>›</span></a></div>
}
function EmptyText({ text }: { text: string }) { return <p className="monitoring-no-data">{text}</p> }
