import { useState } from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import type { AdminMe } from '../features/auth/authApi'

interface AppShellProps {
  administrator: AdminMe
  logout: () => Promise<void>
}

const navigation = [
  { label: '总览', to: '/', icon: 'overview', end: true },
  { label: '用户与权益', to: '/users', icon: 'users' },
  { label: 'Realtime 监测', to: '/monitoring', icon: 'pulse' },
  { label: '用量对账', to: '/reconciliation', icon: 'usage' },
  { label: '系统管理', to: '/system', icon: 'settings' },
] as const

const roleLabels: Record<AdminMe['role'], string> = {
  SUPER_ADMIN: '超级管理员',
  OPERATIONS: '运营',
  TECHNICAL: '技术',
  FINANCE: '财务',
  AUDITOR: '审计',
}

function NavIcon({ name }: { name: string }) {
  const paths: Record<string, React.ReactNode> = {
    overview: <><rect x="4" y="4" width="6" height="6" rx="1" /><rect x="14" y="4" width="6" height="6" rx="1" /><rect x="4" y="14" width="6" height="6" rx="1" /><rect x="14" y="14" width="6" height="6" rx="1" /></>,
    users: <><circle cx="9" cy="8" r="3" /><path d="M3.5 19c.6-3.2 2.4-5 5.5-5s4.9 1.8 5.5 5" /><path d="M16 6.5a3 3 0 0 1 0 5.8M17 14.5c2 .7 3.1 2.2 3.5 4.5" /></>,
    pulse: <><path d="M3 12h4l2.2-5 4.1 10 2.2-5H21" /><circle cx="12" cy="12" r="9" /></>,
    usage: <><path d="M5 19V9M12 19V5M19 19v-7" /><path d="M3 19h18" /></>,
    alerts: <><path d="M6 9a6 6 0 0 1 12 0c0 7 3 7 3 8H3c0-1 3-1 3-8Z" /><path d="M10 21h4" /></>,
    settings: <><circle cx="12" cy="12" r="3" /><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1-2.8 2.8-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.6v.2h-4V21a1.7 1.7 0 0 0-1-1.6 1.7 1.7 0 0 0-1.9.3l-.1.1L4.2 17l.1-.1a1.7 1.7 0 0 0 .3-1.9A1.7 1.7 0 0 0 3 14H2.8v-4H3a1.7 1.7 0 0 0 1.6-1 1.7 1.7 0 0 0-.3-1.9L4.2 7 7 4.2l.1.1A1.7 1.7 0 0 0 9 4.6 1.7 1.7 0 0 0 10 3v-.2h4V3a1.7 1.7 0 0 0 1 1.6 1.7 1.7 0 0 0 1.9-.3l.1-.1L19.8 7l-.1.1a1.7 1.7 0 0 0-.3 1.9 1.7 1.7 0 0 0 1.6 1h.2v4H21a1.7 1.7 0 0 0-1.6 1Z" /></>,
  }
  return <svg className="nav-icon" viewBox="0 0 24 24" aria-hidden="true">{paths[name]}</svg>
}

export function AppShell({ administrator, logout }: AppShellProps) {
  const [loggingOut, setLoggingOut] = useState(false)

  async function handleLogout() {
    setLoggingOut(true)
    try {
      await logout()
    } finally {
      setLoggingOut(false)
    }
  }

  return (
    <div className="app-shell">
      <aside className="sidebar glass-surface">
        <div className="brand">
          <span className="brand__mark"><img src="/admin/brand/unispeaking-mark-user.jpg" alt="" /></span>
          <div>
            <img className="brand__wordmark" src="/admin/brand/unispeaking-wordmark.png" alt="UniSpeaking" />
            <span>用户管理后台</span>
          </div>
        </div>

        <nav className="primary-nav" aria-label="管理后台主导航">
          {navigation.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={'end' in item ? item.end : false}
              className={({ isActive }) => `nav-link${isActive ? ' nav-link--active' : ''}`}
            >
              <NavIcon name={item.icon} />
              <span>{item.label}</span>
            </NavLink>
          ))}
        </nav>

        <div className="sidebar-status">
          <span className="status-dot status-dot--ok" aria-hidden="true" />
          <div>
            <strong>管理服务正常</strong>
            <span>数据源状态见系统管理</span>
          </div>
        </div>

        <div className="admin-profile">
          <span className="admin-avatar" aria-hidden="true">{administrator.login.slice(0, 1).toUpperCase()}</span>
          <div className="admin-profile__identity">
            <strong>{administrator.login}</strong>
            <span>{roleLabels[administrator.role]}</span>
          </div>
          <button type="button" className="icon-button" onClick={handleLogout} disabled={loggingOut} aria-label="退出登录">
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M9 5H5v14h4M14 8l4 4-4 4M8 12h10" /></svg>
          </button>
        </div>
      </aside>

      <div className="workspace">
        <header className="workspace-header">
          <div>
            <span className="workspace-kicker">UNISPEAKING CONTROL</span>
            <strong>用户、额度与费用</strong>
          </div>
          <span className="environment-badge">开发环境</span>
        </header>
        <main className="workspace-main">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
