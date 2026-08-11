import { useState } from 'react'
import type { FormEvent } from 'react'
import { ApiClientError, loginAdministrator } from './authApi'
import type { LoginRequest } from './authApi'

interface LoginPageProps {
  login?: (request: LoginRequest) => Promise<void>
  onAuthenticated?: () => void
}

export function LoginPage({
  login = loginAdministrator,
  onAuthenticated = () => undefined,
}: LoginPageProps) {
  const [loginName, setLoginName] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setPending(true)
    setError('')
    try {
      await login({ login: loginName.trim(), password })
      onAuthenticated()
    } catch (cause) {
      setError(cause instanceof ApiClientError
        ? cause.message
        : '登录失败，请稍后重试')
    } finally {
      setPending(false)
    }
  }

  return (
    <main className="login-page">
      <section className="login-intro" aria-labelledby="login-title">
        <p className="eyebrow">UNISPEAKING · REALTIME GOVERNANCE</p>
        <h1 id="login-title">把每一次会话，<br />变成可追踪的用量。</h1>
        <p className="login-summary">
          统一管理用户权益、临时 Key、Realtime 会话与官方账单。
        </p>
        <div className="trust-note">
          <span aria-hidden="true">●</span>
          永久主 Key 仅存在于服务端
        </div>
      </section>

      <section className="login-panel" aria-label="管理员登录">
        <div className="login-panel__heading brand--auth">
          <span className="brand__mark"><img src="/admin/brand/unispeaking-mark-user.jpg" alt="" /></span>
          <div>
            <img className="brand__wordmark" src="/admin/brand/unispeaking-wordmark.png" alt="UniSpeaking" />
            <span className="brand__caption">内部管理后台</span>
          </div>
        </div>

        <form onSubmit={submit}>
          <div className="field">
            <label htmlFor="admin-login">管理员账号</label>
            <input
              id="admin-login"
              name="login"
              type="text"
              autoComplete="username"
              value={loginName}
              onChange={(event) => setLoginName(event.target.value)}
              placeholder="name@unispeaking.com"
              required
              disabled={pending}
            />
          </div>

          <div className="field">
            <label htmlFor="admin-password">密码</label>
            <div className="password-field">
              <input
                id="admin-password"
                name="password"
                type={showPassword ? 'text' : 'password'}
                autoComplete="current-password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                minLength={12}
                maxLength={200}
                required
                disabled={pending}
              />
              <button
                type="button"
                className="text-button"
                aria-label={showPassword ? '隐藏密码' : '显示密码'}
                onClick={() => setShowPassword((visible) => !visible)}
              >
                {showPassword ? '隐藏' : '显示'}
              </button>
            </div>
          </div>

          {error && <p className="form-error" role="alert">{error}</p>}

          <button className="primary-button" type="submit" disabled={pending}>
            {pending ? '正在验证…' : '登录管理后台'}
          </button>
        </form>

        <p className="security-footnote">会话使用 HttpOnly Cookie，不在浏览器存储访问令牌。</p>
      </section>
    </main>
  )
}
