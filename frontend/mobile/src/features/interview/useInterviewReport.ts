import { useCallback, useEffect, useRef, useState } from 'react';

import type {
  CompletedInterviewReport,
  FailedInterviewReport,
  InterviewReport,
  InterviewReportResponse,
  InterviewSessionApi,
} from './InterviewSessionApi';

export const INTERVIEW_REPORT_POLL_DELAYS_MS = [2_000, 5_000, 10_000, 15_000] as const;

export type InterviewReportState = {
  status: InterviewReportResponse['status'] | 'IDLE';
  report: InterviewReport | null;
  failureReason: string | null;
  error: unknown | null;
  isRetrying: boolean;
};

export type InterviewReportActions = {
  retry: () => Promise<void>;
  refresh: () => Promise<void>;
};

export type InterviewReportResult = InterviewReportState & InterviewReportActions;

const initialState: InterviewReportState = {
  status: 'IDLE',
  report: null,
  failureReason: null,
  error: null,
  isRetrying: false,
};

function stateFromResponse(response: InterviewReportResponse): InterviewReportState {
  if (response.status === 'COMPLETED') {
    return { ...initialState, status: response.status, report: response.report };
  }
  return {
    ...initialState,
    status: response.status,
    failureReason: response.status === 'FAILED' ? response.failureReason : null,
  };
}

export function useInterviewReport(
  sessionId: string | null,
  api: Pick<InterviewSessionApi, 'getReport' | 'retryReport'>,
): InterviewReportResult {
  const [state, setState] = useState<InterviewReportState>(initialState);
  const mountedRef = useRef(true);
  const requestRef = useRef(0);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const pollIndexRef = useRef(0);

  const clearTimer = useCallback(() => {
    if (timerRef.current !== null) {
      clearTimeout(timerRef.current);
      timerRef.current = null;
    }
  }, []);

  const fetchReport = useCallback(async (method: 'getReport' | 'retryReport') => {
    if (!sessionId) return;
    const requestId = ++requestRef.current;
    clearTimer();
    try {
      const response = await api[method](sessionId);
      if (!mountedRef.current || requestId !== requestRef.current) return;
      setState(stateFromResponse(response));
      if (response.status === 'PROCESSING') {
        const delay = INTERVIEW_REPORT_POLL_DELAYS_MS[Math.min(
          pollIndexRef.current,
          INTERVIEW_REPORT_POLL_DELAYS_MS.length - 1,
        )];
        pollIndexRef.current += 1;
        timerRef.current = setTimeout(() => {
          timerRef.current = null;
          void fetchReport('getReport');
        }, delay);
      }
    } catch (error) {
      if (mountedRef.current && requestId === requestRef.current) {
        setState((current) => ({ ...current, error }));
      }
    }
  }, [api, clearTimer, sessionId]);

  useEffect(() => {
    mountedRef.current = true;
    requestRef.current += 1;
    pollIndexRef.current = 0;
    clearTimer();
    setState(sessionId ? initialState : initialState);
    if (sessionId) void fetchReport('getReport');
    return () => {
      mountedRef.current = false;
      requestRef.current += 1;
      clearTimer();
    };
  }, [clearTimer, fetchReport, sessionId]);

  const refresh = useCallback(async () => {
    pollIndexRef.current = 0;
    await fetchReport('getReport');
  }, [fetchReport]);

  const retry = useCallback(async () => {
    if (state.status !== 'FAILED' || !sessionId) return;
    pollIndexRef.current = 0;
    setState((current) => ({ ...current, isRetrying: true, error: null }));
    await fetchReport('retryReport');
    if (mountedRef.current) setState((current) => ({ ...current, isRetrying: false }));
  }, [fetchReport, sessionId, state.status]);

  return { ...state, retry, refresh };
}

export type InterviewReportViewResponse = CompletedInterviewReport | FailedInterviewReport;
