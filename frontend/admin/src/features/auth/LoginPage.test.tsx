import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ApiClientError } from './authApi'
import { LoginPage } from './LoginPage'

describe('LoginPage', () => {
  it('disables submission while login is pending', async () => {
    let resolveLogin: (() => void) | undefined
    const login = vi.fn(() => new Promise<void>((resolve) => {
      resolveLogin = resolve
    }))
    const user = userEvent.setup()

    render(<LoginPage login={login} onAuthenticated={() => undefined} />)
    await user.type(screen.getByLabelText('管理员账号'), 'admin@unispeaking.local')
    await user.type(screen.getByLabelText('密码'), 'correct horse battery staple')
    await user.click(screen.getByRole('button', { name: '登录管理后台' }))

    expect(screen.getByRole('button', { name: '正在验证…' })).toBeDisabled()
    resolveLogin?.()
  })

  it('shows a generic Chinese error for invalid credentials', async () => {
    const login = vi.fn().mockRejectedValue(new ApiClientError('AUTH_INVALID', '用户名或密码错误', 'request-1'))
    const user = userEvent.setup()

    render(<LoginPage login={login} onAuthenticated={() => undefined} />)
    await user.type(screen.getByLabelText('管理员账号'), 'admin@unispeaking.local')
    await user.type(screen.getByLabelText('密码'), 'wrong password')
    await user.click(screen.getByRole('button', { name: '登录管理后台' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('用户名或密码错误')
  })
})
