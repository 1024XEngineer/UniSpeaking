import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { listDataSources, syncAliyunOfficialUsage } from '../governance/governanceApi'
import {
  createMockKeyPool,
  createMockModelRouter,
  type KeyDispatchResult,
  type ModelFailoverResult,
} from './gatewayPolicyMock'

const stateLabels: Record<string, string> = {
  READY: '运行正常',
  HEALTHY: '运行正常',
  DEGRADED: '能力受限',
  DISABLED: '未启用',
  UNAVAILABLE: '不可用',
  ERROR: '连接异常',
}

const stateTones: Record<string, string> = {
  READY: 'ok',
  HEALTHY: 'ok',
  DEGRADED: 'waiting',
  DISABLED: 'neutral',
  UNAVAILABLE: 'danger',
  ERROR: 'danger',
}

const keyStateLabels = { healthy: '健康', cooldown: '冷却中', exhausted: '已耗尽' } as const

export function SystemManagementPage() {
  const queryClient = useQueryClient()
  const sources = useQuery({
    queryKey: ['governance', 'data-sources'],
    queryFn: listDataSources,
    refetchInterval: 15_000,
  })
  const sync = useMutation({
    mutationFn: syncAliyunOfficialUsage,
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['governance', 'data-sources'] }),
        queryClient.invalidateQueries({ queryKey: ['governance', 'reconciliation'] }),
      ])
    },
  })

  const keyPool = useMemo(() => createMockKeyPool(), [])
  const modelRouter = useMemo(() => createMockModelRouter(), [])
  const [keySnapshot, setKeySnapshot] = useState(() => keyPool.snapshot())
  const [dispatchResult, setDispatchResult] = useState<KeyDispatchResult | null>(null)
  const [routingSnapshot, setRoutingSnapshot] = useState(() => modelRouter.snapshot())
  const [failoverResult, setFailoverResult] = useState<ModelFailoverResult | null>(null)

  const simulateDispatch = () => {
    setDispatchResult(keyPool.dispatch())
    const next = keyPool.snapshot()
    setKeySnapshot({ ...next, entries: next.entries.map((entry) => ({ ...entry })) })
  }

  const simulateFailover = () => {
    setFailoverResult(modelRouter.failover())
    const next = modelRouter.snapshot()
    setRoutingSnapshot({ ...next, fallbacks: [...next.fallbacks] })
  }

  return (
    <div className="page-stack system-page">
      <section className="page-heading compact-heading">
        <div>
          <p className="eyebrow">SYSTEM & PROVIDER POLICY</p>
          <h1>数据源与供应商策略</h1>
          <p>检查官方日志链路，并预演 Key 池轮换与模型故障切换。模拟策略不会读取真实凭据。</p>
        </div>
        <span className="quiet-badge">安全检查视图</span>
      </section>

      <section className="system-section surface-panel" aria-labelledby="source-heading">
        <header className="section-heading section-heading--action">
          <div><p className="eyebrow">DATA SOURCES</p><h2 id="source-heading">数据源状态</h2></div>
          <button className="secondary-button" type="button" disabled={sync.isPending} onClick={() => sync.mutate()}>
            {sync.isPending ? '正在同步 SLS…' : '立即同步 SLS'}
          </button>
        </header>

        {sources.isLoading && <PanelMessage title="正在检查数据源" detail="读取统一账本、阿里云 SLS 与 Prometheus 状态。" />}
        {sources.isError && <PanelMessage title="数据源状态读取失败" detail="请检查本地 Java 8090 管理服务后重试。" tone="danger" />}
        {sources.data?.length === 0 && <PanelMessage title="暂无数据源状态" detail="后端接入后将在这里返回各数据源的安全状态摘要。" />}
        {sources.data && sources.data.length > 0 && <div className="source-grid">
          {sources.data.map((source) => {
            const normalized = source.state.toUpperCase()
            return <article className="source-card" key={source.code}>
              <div className="source-card__heading"><span className={`status-dot status-dot--${stateTones[normalized] ?? 'neutral'}`} aria-hidden="true" /><strong>{source.name}</strong></div>
              <span className={`state-badge state-badge--${stateTones[normalized] ?? 'neutral'}`}>{stateLabels[normalized] ?? source.state}</span>
              <p>{source.detail}</p>
              <code>{source.code}</code>
            </article>
          })}
        </div>}

        {sync.data && <p className="action-feedback" role="status">已导入 {sync.data.imported} 条，匹配 {sync.data.matched} 条<span>扫描 {sync.data.scanned} · 未匹配 {sync.data.unmatched} · {new Date(sync.data.synced_at).toLocaleString('zh-CN')}</span></p>}
        {sync.isError && <p className="action-feedback action-feedback--error" role="alert">同步失败，可重试<span>未改变现有对账数据。</span></p>}
      </section>

      <section className="policy-grid" aria-label="网关模拟策略">
        <article className="policy-card surface-panel">
          <header>
            <div><p className="eyebrow">KEY POOL</p><h2>Key 池轮换分发</h2></div>
            <span className="mock-badge">模拟策略</span>
          </header>
          <p className="policy-summary">按 round-robin 从健康 Key 中分发，冷却和耗尽状态自动跳过。</p>
          <div className="key-list">
            {keySnapshot.entries.map((entry) => <div className="key-row" key={entry.id}>
              <span className={`status-dot status-dot--${entry.state === 'healthy' ? 'ok' : entry.state === 'cooldown' ? 'waiting' : 'danger'}`} aria-hidden="true" />
              <div><strong>{entry.id}</strong><small>{entry.maskedFingerprint} · 权重 {entry.weight}</small></div>
              <span>{keyStateLabels[entry.state]}</span>
              <code>{entry.dispatched} 次</code>
            </div>)}
          </div>
          <div className="policy-action">
            <button className="secondary-button" type="button" onClick={simulateDispatch}>模拟分发 Key</button>
            <p>{dispatchResult ? <>本次分发：<strong>{dispatchResult.keyId}</strong><span>{dispatchResult.reason}</span></> : '尚未执行模拟分发'}</p>
          </div>
        </article>

        <article className="policy-card surface-panel">
          <header>
            <div><p className="eyebrow">MODEL ROUTING</p><h2>模型自动切换</h2></div>
            <span className="mock-badge">模拟策略</span>
          </header>
          <p className="policy-summary">主模型异常时按备用顺序切换，路由动作保留为可替换网关接口。</p>
          <dl className="routing-list">
            <div><dt>主模型</dt><dd>{routingSnapshot.primary}</dd></div>
            <div><dt>当前路由</dt><dd><span className="status-dot status-dot--ok" aria-hidden="true" />{routingSnapshot.active}</dd></div>
            {routingSnapshot.fallbacks.map((model, index) => <div key={model}><dt>备用 {index + 1}</dt><dd>{model}</dd></div>)}
          </dl>
          <div className="policy-action">
            <button className="secondary-button" type="button" onClick={simulateFailover}>模拟故障切换</button>
            <p>{failoverResult ? <>已切换至备用模型：<strong>{failoverResult.to}</strong><span>{failoverResult.reason}</span></> : '尚未触发模型切换'}</p>
          </div>
        </article>
      </section>
    </div>
  )
}

function PanelMessage({ title, detail, tone = 'neutral' }: { title: string; detail: string; tone?: 'neutral' | 'danger' }) {
  return <div className={`panel-message panel-message--${tone}`} role={tone === 'danger' ? 'alert' : 'status'}><strong>{title}</strong><p>{detail}</p></div>
}
