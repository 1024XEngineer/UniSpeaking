import { act, cleanup, renderHook, waitFor } from '@testing-library/react-native';

import type { RealtimeSessionSnapshot } from '@/features/realtime/RealtimeSessionController';
import type { TrainingTracker } from '@/infrastructure/analytics/AnalyticsClient';

let mockController: any;

jest.mock('@/model/AppModel', () => ({
  useAppModel: () => ({ speed: '自然' }),
}));

jest.mock('@/features/realtime/RealtimeSessionController', () => ({
  RealtimeSessionController: jest.fn().mockImplementation(() => mockController),
}));
jest.mock('@/features/realtime/ReactNativeWebRTCTransport', () => ({
  ReactNativeWebRTCTransport: jest.fn(),
}));
jest.mock('@/features/realtime/RealtimeSessionApi', () => ({
  RealtimeSessionApi: jest.fn(),
}));
jest.mock('@/features/realtime/SessionMessageSocket', () => ({
  SessionMessageSocket: jest.fn(),
}));
jest.mock('@/features/ielts/IeltsDialogueApi', () => ({
  IeltsDialogueApi: jest.fn(),
}));
jest.mock('@/features/audio/WavRecorder', () => ({
  WavRecorder: jest.fn(),
}));
jest.mock('@/features/audio/TurnAudioCapture', () => ({
  createTurnAudioCapture: jest.fn(() => ({})),
}));
jest.mock('@/infrastructure/auth/SecureTokenStore', () => ({
  SecureTokenStore: jest.fn(),
}));
jest.mock('@/infrastructure/config/runtimeConfig', () => ({
  getRuntimeConfig: () => ({ backendUrl: 'https://api.example.test' }),
}));
jest.mock('@/infrastructure/http/ApiClient', () => ({
  ApiClient: jest.fn(),
}));

import { useIeltsSession, type IeltsSessionControllerPort } from '../useIeltsSession';

const idleSnapshot: RealtimeSessionSnapshot = {
  state: 'idle',
  muted: false,
  sessionId: null,
  userTranscript: '',
  assistantTranscript: '',
  transcriptHistory: [],
  error: null,
};

function createController(): IeltsSessionControllerPort & {
  emit: (snapshot: RealtimeSessionSnapshot) => void;
  start: jest.Mock;
  setMuted: jest.Mock;
  end: jest.Mock;
  transitionPart2: jest.Mock;
  forcePart3Timeout: jest.Mock;
  restoreIeltsState: jest.Mock;
  waitForTurnEvaluations: jest.Mock;
} {
  let snapshot = idleSnapshot;
  const listeners = new Set<(next: RealtimeSessionSnapshot) => void>();
  const controller = {
    getSnapshot: () => snapshot,
    subscribe: jest.fn((listener: (next: RealtimeSessionSnapshot) => void) => {
      listeners.add(listener);
      listener(snapshot);
      return () => listeners.delete(listener);
    }),
    start: jest.fn(async () => undefined),
    setMuted: jest.fn(),
    end: jest.fn(async () => ({ ended: true })),
    waitForTurnEvaluations: jest.fn(async () => undefined),
    transitionPart2: jest.fn(async (event: unknown) => ({ event })),
    forcePart3Timeout: jest.fn(async () => ({ timedOut: true })),
    restoreIeltsState: jest.fn(async () => ({ restored: true })),
    emit(next: RealtimeSessionSnapshot) {
      snapshot = next;
      listeners.forEach((listener) => listener(snapshot));
    },
  };
  mockController = controller;
  return controller;
}

function analyticsFixture(): TrainingTracker {
  return {
    attempt: jest.fn(),
    started: jest.fn(),
    fail: jest.fn(),
    pause: jest.fn(),
    resume: jest.fn(),
    setVisible: jest.fn(),
    settle: jest.fn(),
    complete: jest.fn(),
    abandon: jest.fn(),
    isStarted: jest.fn(() => true),
    start: jest.fn(),
    stop: jest.fn(),
  } as unknown as TrainingTracker;
}

describe('useIeltsSession', () => {
  afterEach(async () => {
    await cleanup();
    mockController = undefined;
    jest.clearAllMocks();
  });

  it('returns an inert session when no IELTS config is provided', async () => {
    const analytics = analyticsFixture();
    const { result } = await renderHook(() => useIeltsSession(null, analytics));

    expect(result.current.snapshot).toEqual(idleSnapshot);
    expect(result.current.statusLabel).toBe('正在准备');
    expect(await result.current.end()).toBeNull();
    expect(await result.current.transitionPart2({ type: 'NEXT' } as any)).toBeNull();
    expect(await result.current.forcePart3Timeout()).toBeNull();
    expect(await result.current.restoreIeltsState()).toBeNull();
    await result.current.waitForTurnEvaluations();
    expect(analytics.attempt).not.toHaveBeenCalled();
  });

  it('starts a PART_2 session, mirrors snapshots, and delegates controls', async () => {
    const controller = createController();
    const analytics = analyticsFixture();
    const { result } = await renderHook(() =>
      useIeltsSession({ ieltsId: 'ielts-1', voiceId: 'Harvey', part: 'PART_2' }, analytics),
    );

    await waitFor(() => expect(controller.start).toHaveBeenCalledTimes(1));
    expect(controller.setMuted).toHaveBeenCalledWith(true);
    expect(analytics.attempt).toHaveBeenCalledTimes(1);

    await act(async () => {
      controller.emit({
        ...idleSnapshot,
        state: 'ready',
        sessionId: 'session-1',
        assistantTranscript: 'Tell me about this topic.',
      });
    });
    expect(result.current.sessionId).toBe('session-1');
    expect(result.current.statusLabel).toBe('可以开始说了');
    expect(analytics.started).toHaveBeenCalled();
    expect(analytics.resume).toHaveBeenCalled();

    await act(async () => {
      result.current.toggleMuted(true);
      await result.current.transitionPart2({ type: 'CUE_CARD_STARTED' } as any);
      await result.current.forcePart3Timeout();
      await result.current.restoreIeltsState();
      await result.current.waitForTurnEvaluations();
    });
    expect(controller.setMuted).toHaveBeenLastCalledWith(true);
    expect(controller.transitionPart2).toHaveBeenCalledWith({ type: 'CUE_CARD_STARTED' });
    expect(controller.forcePart3Timeout).toHaveBeenCalledTimes(1);
    expect(controller.restoreIeltsState).toHaveBeenCalledTimes(1);
    expect(controller.waitForTurnEvaluations).toHaveBeenCalledTimes(1);

    await result.current.end();
    await result.current.end();
    expect(controller.end).toHaveBeenCalledTimes(1);
    expect(analytics.complete).toHaveBeenCalledTimes(1);
  });

  it('reports startup failures and snapshot errors through analytics', async () => {
    const controller = createController();
    controller.start.mockRejectedValueOnce(new Error('连接失败'));
    const analytics = analyticsFixture();
    const { result } = await renderHook(() =>
      useIeltsSession({ ieltsId: 'ielts-2', voiceId: 'Aiden', part: 'PART_1' }, analytics),
    );

    await waitFor(() => expect(result.current.startupError).toBe('连接失败'));
    expect(analytics.fail).toHaveBeenCalledWith('REALTIME_ERROR');

    await act(async () => {
      controller.emit({
        ...idleSnapshot,
        state: 'error',
        error: { code: 'PEER_CONNECTION_FAILED', message: 'peer failed', retryable: true },
      });
    });
    expect(analytics.abandon).toHaveBeenCalledWith('REALTIME_ERROR');
  });

  it('abandons and ends once when the hook is unmounted', async () => {
    const controller = createController();
    const analytics = analyticsFixture();
    const { unmount } = await renderHook(() =>
      useIeltsSession({ ieltsId: 'ielts-3', voiceId: 'Mione', part: 'PART_3' }, analytics),
    );

    await unmount();
    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
    });
    await waitFor(() => expect(controller.end).toHaveBeenCalledTimes(1));
    expect(analytics.abandon).toHaveBeenCalledWith('USER_EXIT');
  });
});
