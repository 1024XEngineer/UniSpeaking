import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useState } from 'react'
import { listUsageUsers, updateUserEntitlement, type UsageUser } from './governanceApi'

const seconds = (value: number) => `${value.toLocaleString('zh-CN', { minimumFractionDigits: 1, maximumFractionDigits: 1 })} 秒`

export function UsersPage() {
  const queryClient = useQueryClient()
  const query = useQuery({ queryKey: ['governance', 'users'], queryFn: listUsageUsers, refetchInterval: 10_000 })
  const [editingUserId, setEditingUserId] = useState<string | null>(null)
  const [draft, setDraft] = useState<EntitlementDraft | null>(null)
  const mutation = useMutation({
    mutationFn: ({ userId, request }: { userId: string; request: EntitlementDraft }) =>
      updateUserEntitlement(userId, request),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['governance', 'users'] })
      setEditingUserId(null)
      setDraft(null)
    },
  })

  const startEditing = (user: UsageUser) => {
    setEditingUserId(user.user_id)
    setDraft({ planCode: user.plan_code, planName: user.plan_name, quotaSeconds: user.quota_seconds, status: user.status === 'suspended' ? 'suspended' : 'active' })
    mutation.reset()
  }

  const cancelEditing = useCallback(() => {
    setEditingUserId(null)
    setDraft(null)
    mutation.reset()
  }, [mutation])

  useEffect(() => {
    if (!editingUserId) return

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !mutation.isPending) cancelEditing()
    }

    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [cancelEditing, editingUserId, mutation.isPending])

  const editingUser = query.data?.find((user) => user.user_id === editingUserId)

  return (
    <div className="page-stack">
      <section className="page-heading compact-heading">
        <div><p className="eyebrow">USERS & ENTITLEMENTS</p><h1>用户与每日额度</h1><p>按用户展示服务器账户、今日使用时长、剩余额度和最终费用。</p></div>
        <span className="quiet-badge">{query.data ? `${query.data.length} 个用户` : '同步中'}</span>
      </section>
      <section className="data-panel glass-surface">
        {query.isError && <PanelMessage title="用户数据读取失败" detail="请检查 PostgreSQL 和 Java 8090 的连接状态。" />}
        {query.isLoading && <PanelMessage title="正在读取真实账户" detail="数据来自 UniSpeaking PostgreSQL 用户库。" />}
        {query.data?.length === 0 && <PanelMessage title="还没有注册用户" detail="用户完成邮箱注册后会自动出现在这里。" />}
        {query.data && query.data.length > 0 && (
          <div className="table-scroll"><table className="data-table">
            <thead><tr><th>用户</th><th>套餐</th><th>今日额度</th><th>已用</th><th>剩余</th><th>今日费用</th><th>权限</th></tr></thead>
            <tbody>{query.data.map((user) => <tr key={user.user_id}>
              <td><strong>{user.display_name}</strong><small>{user.user_id}</small></td>
              <td><div className="stacked-badges"><span className="state-badge">{user.plan_name}</span><span className={`state-badge state-badge--${user.status === 'suspended' ? 'danger' : 'ok'}`}>{user.status === 'suspended' ? '已暂停' : '正常'}</span></div></td>
              <td className="numeric">{seconds(user.quota_seconds)}</td>
              <td className="numeric">{seconds(user.used_seconds)}</td>
              <td className="numeric emphasis">{seconds(user.remaining_seconds)}</td>
              <td className="numeric emphasis">¥{Number(user.estimated_cost_cny).toFixed(2)}</td>
              <td><button type="button" className="quiet-button" onClick={() => startEditing(user)}>编辑权限</button></td>
            </tr>)}</tbody>
          </table></div>
        )}
        {mutation.isSuccess && <p className="action-feedback" role="status">权限已更新</p>}
      </section>
      {editingUser && draft && (
        <div className="modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget && !mutation.isPending) cancelEditing() }}>
          <section className="entitlement-dialog" role="dialog" aria-modal="true" aria-labelledby="edit-entitlement-title" aria-describedby="edit-entitlement-description">
            <header className="entitlement-dialog__header">
              <div>
                <p className="eyebrow">EDIT ENTITLEMENT</p>
                <h2 id="edit-entitlement-title">编辑用户额度</h2>
                <p id="edit-entitlement-description" className="entitlement-dialog__identity">{editingUser.display_name}<span>{editingUser.user_id}</span></p>
              </div>
              <button type="button" className="modal-close" aria-label="关闭编辑权限" onClick={cancelEditing} disabled={mutation.isPending}>×</button>
            </header>
            <div className="entitlement-dialog__form">
              <label>套餐编码<input aria-label="套餐编码" value={draft.planCode} onChange={(event) => setDraft({ ...draft, planCode: event.target.value })} /></label>
              <label>套餐名称<input aria-label="套餐名称" value={draft.planName} onChange={(event) => setDraft({ ...draft, planName: event.target.value })} /></label>
              <label>每日额度（秒）<input aria-label="每日额度" type="number" min="0" max="86400" value={draft.quotaSeconds} onChange={(event) => setDraft({ ...draft, quotaSeconds: Number(event.target.value) })} /></label>
              <label>状态<select aria-label="状态" value={draft.status} onChange={(event) => setDraft({ ...draft, status: event.target.value as EntitlementDraft['status'] })}><option value="active">正常</option><option value="suspended">暂停</option></select></label>
            </div>
            <footer className="entitlement-dialog__footer">
              {mutation.isError && <small className="form-error" role="alert">{mutation.error.message}</small>}
              <div className="entitlement-dialog__actions">
                <button type="button" className="quiet-button" onClick={cancelEditing} disabled={mutation.isPending}>取消</button>
                <button type="button" className="primary-button" onClick={() => mutation.mutate({ userId: editingUser.user_id, request: draft })} disabled={mutation.isPending}>{mutation.isPending ? '保存中…' : '保存权限'}</button>
              </div>
            </footer>
          </section>
        </div>
      )}
    </div>
  )
}

type EntitlementDraft = {
  planCode: string
  planName: string
  quotaSeconds: number
  status: 'active' | 'suspended'
}

function PanelMessage({ title, detail }: { title: string; detail: string }) {
  return <div className="panel-message" role="status"><strong>{title}</strong><p>{detail}</p></div>
}
