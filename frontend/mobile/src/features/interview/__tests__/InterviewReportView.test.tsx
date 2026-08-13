import { fireEvent, render, waitFor } from '@testing-library/react-native';

import type { InterviewReportResponse } from '../InterviewSessionApi';
import { InterviewReportView } from '../InterviewReportView';

const completed: InterviewReportResponse = {
  sessionId: 'session-1',
  sceneId: 'scene-1',
  status: 'COMPLETED',
  failureReason: null,
  report: {
    sessionId: 'session-1',
    sceneId: 'scene-1',
    overallScore: 82,
    summary: 'Clear.',
    dimensions: [],
    completedAt: 'now',
  },
};

describe('InterviewReportView', () => {
  afterEach(() => jest.useRealTimers());

  it('shows a network error and immediately refreshes the report', async () => {
    jest.useFakeTimers();
    const api = {
      getReport: jest.fn()
        .mockRejectedValueOnce(new Error('offline'))
        .mockResolvedValueOnce(completed),
      retryReport: jest.fn(),
    };
    const screen = await render(<InterviewReportView api={api} sessionId="session-1" />);

    await waitFor(() => expect(screen.getByText('网络错误')).toBeTruthy());
    expect(screen.getByText('报告加载暂时失败，正在自动重试。')).toBeTruthy();
    expect(screen.getByText('立即刷新')).toBeTruthy();

    await fireEvent.press(screen.getByRole('button', { name: '立即刷新' }));
    await waitFor(() => expect(screen.getByText('82', { exact: false })).toBeTruthy());
    expect(api.getReport).toHaveBeenCalledTimes(2);
    expect(api.retryReport).not.toHaveBeenCalled();
  });
});
