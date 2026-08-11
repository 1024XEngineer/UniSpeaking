import { useQuery } from '@tanstack/react-query'
import { getDashboardSummary } from '../governance/governanceApi'

function seconds(value: number) {
  return `${value.toLocaleString('zh-CN', { minimumFractionDigits: 1, maximumFractionDigits: 1 })} 秒`
}

export function OverviewPage() {
  const summary = useQuery({
    queryKey: ['governance', 'summary'],
    queryFn: getDashboardSummary,
    refetchInterval: 10_000,
  })

  const metrics = summary.data ? [
    { label: '用户总数', value: summary.data.total_users.toLocaleString('zh-CN'), hint: '服务器真实账户' },
    { label: '今日总额度', value: seconds(summary.data.quota_seconds), hint: `剩余 ${seconds(summary.data.remaining_seconds)}` },
    { label: '今日已用', value: seconds(summary.data.used_seconds), hint: '按用户自动汇总' },
    { label: '今日费用', value: `¥${Number(summary.data.estimated_cost_cny).toFixed(2)}`, hint: '最终用户费用汇总' },
  ] : []

  if (summary.isError) {
    return <ErrorState message="账户数据库或管理后端暂时不可用，请检查 PostgreSQL 和 Java 8090。" />
  }

  return (
    <div className="page-stack">
      <section className="page-heading">
        <div>
          <p className="eyebrow">OVERVIEW</p>
          <h1>用户、额度与费用</h1>
          <p>后台只展示每个用户自动汇总后的账户、每日额度和最终费用。</p>
        </div>
        <div className="sync-summary" aria-live="polite">
          <span className={`status-dot status-dot--${summary.isLoading ? 'waiting' : 'ok'}`} aria-hidden="true" />
          <div>
            <strong>{summary.isLoading ? '正在同步数据' : '管理链路已连接'}</strong>
            <span>{summary.data ? `更新于 ${new Date(summary.data.generated_at).toLocaleTimeString('zh-CN')}` : '请稍候'}</span>
          </div>
        </div>
      </section>

      <section className="metric-line" aria-label="核心指标">
        {summary.isLoading && <LoadingRows label="正在读取用量" />}
        {metrics.map((metric) => (
          <div className="metric" key={metric.label}>
            <span>{metric.label}</span>
            <strong>{metric.value}</strong>
            <small>{metric.hint}</small>
          </div>
        ))}
      </section>

    </div>
  )
}

function LoadingRows({ label }: { label: string }) {
  return <div className="inline-loading" role="status"><span className="loading-mark" aria-hidden="true" />{label}</div>
}

function ErrorState({ message }: { message: string }) {
  return <section className="error-state glass-surface" role="alert"><strong>数据暂时无法读取</strong><p>{message}</p></section>
}
