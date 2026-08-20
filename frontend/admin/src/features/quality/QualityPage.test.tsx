import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { QualityPage } from './QualityPage'
import {
  createQualityIssue,
  getQualitySummary,
  listQualityEvents,
  listQualityIssues,
  updateQualityIssue,
  type QualityIssue,
} from './qualityApi'

vi.mock('./qualityApi', async (importOriginal) => {
  const original = await importOriginal<typeof import('./qualityApi')>()
  return {
    ...original,
    createQualityIssue: vi.fn(),
    getQualitySummary: vi.fn(),
    listQualityEvents: vi.fn(),
    listQualityIssues: vi.fn(),
    updateQualityIssue: vi.fn(),
  }
})

const issue: QualityIssue = {
  issueId: '10000000-0000-4000-8000-000000000001',
  fingerprint: 'abc123',
  issueType: 'BUG',
  source: 'TELEMETRY',
  platform: 'MOBILE',
  severity: 'HIGH',
  status: 'OPEN',
  title: '500 · /api/sessions',
  description: '移动端创建会话失败',
  errorCode: 'INTERNAL_SERVER_ERROR',
  apiPath: '/api/sessions',
  httpStatus: 500,
  release: 'mobile@1.0.0',
  assignee: null,
  resolution: null,
  occurrenceCount: 4,
  affectedUsers: 2,
  firstSeenAt: '2026-08-20T01:00:00Z',
  lastSeenAt: '2026-08-20T02:00:00Z',
  resolvedAt: null,
  createdBy: 'telemetry',
  updatedBy: 'telemetry',
  createdAt: '2026-08-20T01:00:00Z',
  updatedAt: '2026-08-20T02:00:00Z',
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={client}><QualityPage /></QueryClientProvider>)
}

function mockReads() {
  vi.mocked(getQualitySummary).mockResolvedValue({
    activeIssues: 1, criticalIssues: 0, optimizations: 0,
    events7d: 4, affectedUsers7d: 2, resolved7d: 0,
    generatedAt: '2026-08-20T02:00:00Z',
  })
  vi.mocked(listQualityIssues).mockResolvedValue([issue])
  vi.mocked(listQualityEvents).mockResolvedValue([])
}

describe('QualityPage', () => {
  it('shows aggregated web, mobile, and backend quality records', async () => {
    mockReads()
    renderPage()

    expect(await screen.findByText('500 · /api/sessions')).toBeInTheDocument()
    expect(screen.getByText('移动端', { selector: 'td' })).toBeInTheDocument()
    expect(screen.getByText('4', { selector: 'td strong' })).toBeInTheDocument()
    expect(screen.getByText('2 位用户')).toBeInTheDocument()
  })

  it('creates an optimization record from the admin page', async () => {
    mockReads()
    vi.mocked(createQualityIssue).mockResolvedValue({
      ...issue, issueId: '10000000-0000-4000-8000-000000000002',
      issueType: 'OPTIMIZATION', source: 'MANUAL', title: '缩短首次响应时间',
    })
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByRole('button', { name: '新增问题' }))
    const createDialog = screen.getByRole('dialog', { name: '新增 BUG 或优化' })
    await user.selectOptions(within(createDialog).getByLabelText('类型'), 'OPTIMIZATION')
    await user.selectOptions(within(createDialog).getByLabelText('平台'), 'CROSS_PLATFORM')
    await user.type(within(createDialog).getByLabelText('标题'), '缩短首次响应时间')
    await user.type(within(createDialog).getByLabelText('详细情况'), 'Web 和移动端首包时间需要降低')
    await user.click(within(createDialog).getByRole('button', { name: '创建记录' }))

    await waitFor(() => expect(vi.mocked(createQualityIssue).mock.calls[0]?.[0]).toEqual(expect.objectContaining({
      issueType: 'OPTIMIZATION',
      platform: 'CROSS_PLATFORM',
      title: '缩短首次响应时间',
    })))
  })

  it('updates owner, status, and resolution for an issue', async () => {
    mockReads()
    vi.mocked(updateQualityIssue).mockResolvedValue({
      ...issue, status: 'RESOLVED', assignee: '后端组', resolution: '已增加事务重试',
    })
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByText('500 · /api/sessions'))
    const updateDialog = screen.getByRole('dialog', { name: '500 · /api/sessions' })
    await user.selectOptions(within(updateDialog).getByLabelText('处理状态'), 'RESOLVED')
    await user.type(within(updateDialog).getByLabelText('负责人'), '后端组')
    await user.type(within(updateDialog).getByLabelText('修复或优化结论'), '已增加事务重试')
    await user.click(within(updateDialog).getByRole('button', { name: '保存进展' }))

    await waitFor(() => expect(updateQualityIssue).toHaveBeenCalledWith(issue.issueId, expect.objectContaining({
      status: 'RESOLVED', assignee: '后端组', resolution: '已增加事务重试',
    })))
  })
})
