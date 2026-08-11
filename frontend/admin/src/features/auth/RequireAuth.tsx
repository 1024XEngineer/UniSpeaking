import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from './AuthContext'

export function RequireAuth() {
  const { administrator, pending, error } = useAuth()
  const location = useLocation()

  if (pending) {
    return <main className="center-state"><span className="loading-mark" aria-hidden="true" /><p>正在验证管理会话…</p></main>
  }
  if (error) {
    return <main className="center-state"><strong>暂时无法连接管理服务</strong><p>请检查 Java 后端是否已启动，然后刷新页面。</p></main>
  }
  if (!administrator) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }
  return <Outlet />
}
