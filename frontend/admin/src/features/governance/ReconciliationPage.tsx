import { useQuery } from '@tanstack/react-query'
import { Fragment, useState } from 'react'
import { listReconciliationRecords } from './governanceApi'

const labels: Record<string, string> = { PENDING: '等待官方记录', MATCHED: '已匹配', MISMATCH: '存在差异' }
const tones: Record<string, string> = { PENDING: 'waiting', MATCHED: 'ok', MISMATCH: 'danger' }

export function ReconciliationPage() {
  const query = useQuery({ queryKey: ['governance', 'reconciliation'], queryFn: listReconciliationRecords, refetchInterval: 15_000 })
  const mismatch = query.data?.filter((record) => record.status === 'MISMATCH').length ?? 0
  const [expanded, setExpanded] = useState<string | null>(null)

  return (
    <div className="page-stack">
      <section className="page-heading compact-heading">
        <div><p className="eyebrow">USAGE & RECONCILIATION</p><h1>本地预估，官方定账。</h1><p>以 task_uuid 匹配会话、request_id 去重，对比时长、Tokens 与目录价费用。</p></div>
        <span className={`state-badge state-badge--${mismatch > 0 ? 'danger' : 'ok'}`}>{mismatch} 条差异</span>
      </section>
      <section className="data-panel glass-surface">
        {query.isError && <PanelMessage title="对账数据读取失败" detail="请检查统一账本与阿里云 SLS 同步状态。" />}
        {query.isLoading && <PanelMessage title="正在合并官方记录" detail="官方审计数据可能延迟到达。" />}
        {query.data?.length === 0 && <PanelMessage title="暂无对账记录" detail="导入包含 task_uuid 的官方 JSON 后即可开始核对。" />}
        {query.data && query.data.length > 0 && <div className="table-scroll"><table className="data-table">
          <thead><tr><th>用户 / 会话</th><th>客户端用量</th><th>官方用量</th><th>官方时长</th><th>预估金额</th><th>状态</th><th>操作</th></tr></thead>
          <tbody>{query.data.map((record) => {
            const key = `${record.session_id}-${record.request_id ?? 'pending'}`
            const isExpanded = expanded === key
            return <Fragment key={key}>
              <tr>
                <td className="identity-cell"><strong>{record.user_id}</strong><small>{maskIdentifier(record.session_id)}</small><div className="identity-chain"><span>{maskIdentifier(record.task_uuid)}</span><i>·</i><span>{maskIdentifier(record.request_id)}</span></div></td>
                <td><TokenSummary usage={record.client_usage} total={record.client_tokens} /></td>
                <td><TokenSummary usage={record.official_usage} total={record.official_tokens} /></td>
                <td className="numeric">{record.official_duration_ms == null ? '—' : `${(record.official_duration_ms / 1000).toFixed(3)} 秒`}</td>
                <td className="numeric">{record.estimated_cost_cny == null ? '待计算' : `¥${Number(record.estimated_cost_cny).toFixed(6)}`}</td>
                <td><span className={`state-badge state-badge--${tones[record.status]}`}>{labels[record.status]}</span></td>
                <td><button className="quiet-button" type="button" aria-expanded={isExpanded} onClick={() => setExpanded(isExpanded ? null : key)}>{isExpanded ? '收起详情' : '查看详情'}</button></td>
              </tr>
              {isExpanded && <tr className="reconciliation-detail-row"><td colSpan={7}>
                <div className="reconciliation-detail-grid">
                  <Detail label="本地会话 ID" value={record.session_id} />
                  <Detail label="task_uuid" value={record.task_uuid ?? '—'} />
                  <Detail label="request_id" value={record.request_id ?? '—'} />
                  <Detail label="临时 Key ID" value={record.temporary_key_id ?? '—'} />
                  <Detail label="客户端明细" value={usageDetails(record.client_usage)} />
                  <Detail label="官方明细" value={usageDetails(record.official_usage)} />
                  <Detail label="差异原因" value={record.reasons.length === 0 ? '无' : record.reasons.join('、')} />
                </div>
              </td></tr>}
            </Fragment>
          })}</tbody>
        </table></div>}
      </section>
    </div>
  )
}

function TokenSummary({ usage, total }: { usage?: { input_tokens: number; output_tokens: number }; total: number }) {
  return <div className="token-summary">{usage ? <><span>输入 {usage.input_tokens.toLocaleString('zh-CN')}</span><span>输出 {usage.output_tokens.toLocaleString('zh-CN')}</span></> : <span>等待拆分数据</span>}<small>合计 {total.toLocaleString('zh-CN')}</small></div>
}

function Detail({ label, value }: { label: string; value: string }) {
  return <div className="reconciliation-detail"><span>{label}</span><code>{value}</code></div>
}

function usageDetails(usage?: { input_text_tokens?: number; input_audio_tokens?: number; output_text_tokens?: number; output_audio_tokens?: number }) {
  if (!usage) return '旧版数据未提供输入/输出拆分'
  return `输入：文本 ${(usage.input_text_tokens ?? 0).toLocaleString('zh-CN')} · 音频 ${(usage.input_audio_tokens ?? 0).toLocaleString('zh-CN')}；输出：文本 ${(usage.output_text_tokens ?? 0).toLocaleString('zh-CN')} · 音频 ${(usage.output_audio_tokens ?? 0).toLocaleString('zh-CN')}`
}

function maskIdentifier(value: string | null | undefined) {
  if (!value) return '—'
  const head = value.startsWith('sess_') ? 9 : 8
  const tail = value.startsWith('sess_') ? 3 : 4
  if (value.length <= head + tail + 1) return value
  return `${value.slice(0, head)}…${value.slice(-tail)}`
}

function PanelMessage({ title, detail }: { title: string; detail: string }) {
  return <div className="panel-message" role="status"><strong>{title}</strong><p>{detail}</p></div>
}
