import { act, fireEvent, render, waitFor } from '@testing-library/react-native';
import { Text, Pressable } from 'react-native';

import type { InterviewReportResponse } from '../InterviewSessionApi';
import { useInterviewReport } from '../useInterviewReport';

const processing: InterviewReportResponse = {
  sessionId: 'session-1', sceneId: 'scene-1', status: 'PROCESSING', report: null, failureReason: null,
};
const completed: InterviewReportResponse = {
  sessionId: 'session-1', sceneId: 'scene-1', status: 'COMPLETED', failureReason: null,
  report: { sessionId: 'session-1', sceneId: 'scene-1', overallScore: 82, summary: 'Clear.', dimensions: [], completedAt: 'now' },
};
const failed: InterviewReportResponse = {
  sessionId: 'session-1', sceneId: 'scene-1', status: 'FAILED', report: null, failureReason: 'timeout',
};

function Probe({ api, sessionId = 'session-1' }: { api: any; sessionId?: string | null }) {
  const report = useInterviewReport(sessionId, api);
  return (
    <>
      <Text testID="state">{report.status}</Text>
      <Text testID="score">{report.report?.overallScore ?? 'null'}</Text>
      <Text testID="error">{report.error ? 'error' : 'none'}</Text>
      <Pressable testID="retry" onPress={() => void report.retry()} />
      <Pressable testID="refresh" onPress={() => void report.refresh()} />
    </>
  );
}

describe('useInterviewReport', () => {
  afterEach(() => jest.useRealTimers());

  it('loads all three states and only retries after an explicit retry action', async () => {
    const api = { getReport: jest.fn().mockResolvedValue(failed), retryReport: jest.fn().mockResolvedValue(completed) };
    const screen = await render(<Probe api={api} />);
    await waitFor(() => expect(screen.getByTestId('state').props.children).toBe('FAILED'));
    expect(api.retryReport).not.toHaveBeenCalled();
    await fireEvent.press(screen.getByTestId('retry'));
    expect(api.retryReport).toHaveBeenCalledWith('session-1');
    await waitFor(() => expect(screen.getByTestId('score').props.children).toBe(82));
  });

  it('polls processing with 2/5/10/15 second backoff', async () => {
    jest.useFakeTimers();
    const api = { getReport: jest.fn().mockResolvedValueOnce(processing).mockResolvedValueOnce(completed), retryReport: jest.fn() };
    await render(<Probe api={api} />);
    await act(async () => { await Promise.resolve(); });
    expect(api.getReport).toHaveBeenCalledTimes(1);
    await act(async () => { jest.advanceTimersByTime(1_999); });
    expect(api.getReport).toHaveBeenCalledTimes(1);
    await act(async () => {
      jest.advanceTimersByTime(1);
      await Promise.resolve();
      await Promise.resolve();
    });
    expect(api.getReport).toHaveBeenCalledTimes(2);
  });

  it('shows a recoverable error and retries getReport with the same backoff', async () => {
    jest.useFakeTimers();
    const api = {
      getReport: jest.fn()
        .mockRejectedValueOnce(new Error('offline'))
        .mockRejectedValueOnce(new Error('still offline'))
        .mockRejectedValueOnce(new Error('still offline'))
        .mockResolvedValueOnce(completed),
      retryReport: jest.fn(),
    };
    const screen = await render(<Probe api={api} />);

    await act(async () => { await Promise.resolve(); });
    expect(screen.getByTestId('error').props.children).toBe('error');

    await act(async () => { jest.advanceTimersByTime(1_999); });
    expect(api.getReport).toHaveBeenCalledTimes(1);
    await act(async () => {
      jest.advanceTimersByTime(1);
      await Promise.resolve();
    });
    expect(api.getReport).toHaveBeenCalledTimes(2);

    await act(async () => {
      jest.advanceTimersByTime(4_999);
      await Promise.resolve();
    });
    expect(api.getReport).toHaveBeenCalledTimes(2);
    await act(async () => {
      jest.advanceTimersByTime(1);
      await Promise.resolve();
    });
    expect(api.getReport).toHaveBeenCalledTimes(3);

    await act(async () => {
      jest.advanceTimersByTime(9_999);
      await Promise.resolve();
    });
    expect(api.getReport).toHaveBeenCalledTimes(3);
    await act(async () => {
      jest.advanceTimersByTime(1);
      await Promise.resolve();
    });
    expect(api.getReport).toHaveBeenCalledTimes(4);

    await act(async () => { await Promise.resolve(); });
    expect(screen.getByTestId('state').props.children).toBe('COMPLETED');
    expect(screen.getByTestId('error').props.children).toBe('none');
  });

  it('recovers from a temporary retryReport error without retrying automatically', async () => {
    jest.useFakeTimers();
    const api = {
      getReport: jest.fn().mockResolvedValueOnce(failed).mockResolvedValueOnce(completed),
      retryReport: jest.fn().mockRejectedValueOnce(new Error('offline')),
    };
    const screen = await render(<Probe api={api} />);
    await act(async () => { await Promise.resolve(); });
    await fireEvent.press(screen.getByTestId('retry'));

    expect(api.retryReport).toHaveBeenCalledTimes(1);
    expect(screen.getByTestId('state').props.children).toBe('FAILED');
    expect(screen.getByTestId('error').props.children).toBe('error');
    await act(async () => { jest.advanceTimersByTime(1_999); });
    expect(api.getReport).toHaveBeenCalledTimes(1);
    await act(async () => {
      jest.advanceTimersByTime(1);
      await Promise.resolve();
      await Promise.resolve();
    });
    expect(api.getReport).toHaveBeenCalledTimes(2);
    await waitFor(() => expect(screen.getByTestId('state').props.children).toBe('COMPLETED'));
    expect(api.retryReport).toHaveBeenCalledTimes(1);
  });

  it('ignores a result from the previous session', async () => {
    let resolveFirst!: (value: InterviewReportResponse) => void;
    const completedForSecondSession: InterviewReportResponse = {
      ...completed,
      sessionId: 'session-2',
      report: { ...completed.report, sessionId: 'session-2', overallScore: 91 },
    };
    const api = {
      getReport: jest.fn()
        .mockImplementationOnce(() => new Promise<InterviewReportResponse>((resolve) => { resolveFirst = resolve; }))
        .mockResolvedValueOnce(completedForSecondSession),
      retryReport: jest.fn(),
    };
    const screen = await render(<Probe api={api} sessionId="session-1" />);
    await screen.rerender(<Probe api={api} sessionId="session-2" />);
    await waitFor(() => expect(api.getReport).toHaveBeenCalledTimes(2));

    await waitFor(() => expect(screen.getByTestId('score').props.children).toBe(91));
    await act(async () => {
      resolveFirst(processing);
      await Promise.resolve();
    });
    expect(screen.getByTestId('score').props.children).toBe(91);
    expect(api.getReport).toHaveBeenCalledTimes(2);
  });

  it('cancels an in-flight result and timer when unmounted', async () => {
    jest.useFakeTimers();
    let resolve!: (value: InterviewReportResponse) => void;
    const api = { getReport: jest.fn(() => new Promise<InterviewReportResponse>((r) => { resolve = r; })), retryReport: jest.fn() };
    const screen = await render(<Probe api={api} />);
    await act(async () => { await Promise.resolve(); });
    expect(api.getReport).toHaveBeenCalledTimes(1);
    expect(resolve).toBeDefined();
    await screen.unmount();
    resolve(processing);
    await act(async () => { await Promise.resolve(); });
    jest.runOnlyPendingTimers();
    expect(api.getReport).toHaveBeenCalledTimes(1);
  });
});
