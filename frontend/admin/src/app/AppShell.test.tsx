import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { AppShell } from './AppShell'

const administrator = {
  id: '00000000-0000-0000-0000-000000000001',
  login: 'admin@unispeaking.local',
  role: 'SUPER_ADMIN' as const,
}

describe('AppShell', () => {
  it('renders the user management modules and marks the current page', () => {
    render(
      <MemoryRouter initialEntries={['/users']}>
        <AppShell administrator={administrator} logout={async () => undefined} />
      </MemoryRouter>,
    )

    const labels = ['总览', '用户与权益', '用量与计费', 'BUG 与优化', '用户追踪', '模型供应商与费用']
    for (const label of labels) {
      expect(screen.getByRole('link', { name: label })).toBeInTheDocument()
    }
    expect(screen.queryByRole('link', { name: 'Realtime 监测' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: '用量对账' })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: '用户与权益' })).toHaveAttribute('aria-current', 'page')
  })

  it('links the user tracking item to the Umami dashboard', () => {
    render(
      <MemoryRouter>
        <AppShell administrator={administrator} logout={async () => undefined} />
      </MemoryRouter>,
    )

    expect(screen.getByRole('link', { name: '用户追踪' })).toHaveAttribute(
      'href',
      'https://cloud.umami.is/analytics/us/websites/3ae2dee9-d585-43a9-93f3-fcafcd14b258',
    )
    expect(screen.getByRole('link', { name: '用户追踪' })).toHaveAttribute('target', '_blank')
  })

  it('invokes logout once', async () => {
    const logout = vi.fn().mockResolvedValue(undefined)
    const user = userEvent.setup()
    render(
      <MemoryRouter>
        <AppShell administrator={administrator} logout={logout} />
      </MemoryRouter>,
    )

    await user.click(screen.getByRole('button', { name: '退出登录' }))

    expect(logout).toHaveBeenCalledOnce()
  })
})
