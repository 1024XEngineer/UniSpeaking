import { useQuery } from '@tanstack/react-query'
import { listRealtimeSessions } from './governanceApi'

const seconds = (value: number) => `${value.toLocaleString('zh-CN', { minimumFractionDigits: 1, maximumFractionDigits: 1 })} 秒`
const statusLabel: Record<string, string> = {
  created: '已创建', connecting: '连接中', connected: '连接中', waiting_client: '等待客户端',
  active: '进行中', paused: '已暂停', interrupted: '已中断', completed: '已结束', ended: '已结束', failed: '失败',
}
const activeStatuses = new Set(['created', 'connecting', 'connected', 'waiting_client', 'active'])

function normalizedStatus(status: string) {
  return String(status || '').trim().toLowerCase()
}

function statusTone(status: string) {
  const normalized = normalizedStatus(status)
  if (normalized === 'active' || normalized === 'connected') return 'ok'
  if (normalized === 'failed') return 'danger'
  return 'waiting'
}

export function MonitoringPage() {
  const query = useQuery({ queryKey: ['governance', 'sessions'], queryFn: listRealtimeSessions, refetchInterval: 5_000 })
  const active = query.data?.filter((session) => activeStatuses.has(normalizedStatus(session.status))).length ?? 0

  return (
    <div className="page-stack">
      <section className="page-heading compact-heading">
        <div><p className="eyebrow">REALTIME MONITORING</p><h1>连接状态与身份链路。</h1><p>不保存临时 Key 明文，只展示内部 Key ID、过期时间及供应商会话标识。</p></div>
        <span className={`state-badge state-badge--${active > 0 ? 'ok' : 'neutral'}`}>{active} 个活跃连接</span>
      </section>
      <section className="data-panel glass-surface">
        {query.isError && <PanelMessage title="会话数据读取失败" detail="请检查 UniSpeaking 后端和 PostgreSQL 是否可访问。" />}
        {query.isLoading && <PanelMessage title="正在监听 Realtime 会话" detail="页面每 5 秒刷新一次。" />}
        {query.data?.length === 0 && <PanelMessage title="暂无会话" detail="用户开始练习后，这里会显示真实会话记录。" />}
        {query.data && query.data.length > 0 && <div className="session-list">
          {query.data.map((session) => <article className="session-row" key={session.session_id}>
            <div className="session-row__summary">
              <span className={`status-dot status-dot--${statusTone(session.status)}`} aria-hidden="true" />
              <div><strong>{session.user_id}</strong><span>{statusLabel[normalizedStatus(session.status)] ?? session.status} · {seconds(session.measured_seconds)}</span></div>
              <span className="state-badge">{session.plan_code ?? '未分组'}</span>
            </div>
            <div className="identity-chain" aria-label="会话身份关联链">
              <Identity label="本地会话" value={session.session_id} />
              <i aria-hidden="true">→</i>
              <Identity
                label={session.temporary_key_expires_at ? `临时 Key ID · ${new Date(session.temporary_key_expires_at * 1000).toLocaleTimeString('zh-CN')} 到期` : '临时 Key ID'}
                value={session.temporary_key_id ?? '等待签发'}
              />
              <i aria-hidden="true">→</i>
              <Identity label="task_uuid" value={session.task_uuid ?? '等待供应商回传'} />
              <i aria-hidden="true">→</i>
              <Identity label="request_id" value={session.provider_request_id ?? '等待官方记录'} />
            </div>
          </article>)}
        </div>}
      </section>
    </div>
  )
}

function Identity({ label, value }: { label: string; value: string }) {
  return <div className="identity-node"><span>{label}</span><strong title={value}>{value}</strong></div>
}

function PanelMessage({ title, detail }: { title: string; detail: string }) {
  return <div className="panel-message" role="status"><strong>{title}</strong><p>{detail}</p></div>
}
