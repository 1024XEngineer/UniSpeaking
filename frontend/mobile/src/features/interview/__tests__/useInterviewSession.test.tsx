import { act, cleanup, renderHook, waitFor } from '@testing-library/react-native';

import type { InterviewSessionSnapshot } from '../InterviewSessionController';
import type { TrainingTracker } from '@/infrastructure/analytics/AnalyticsClient';

let mockController: any;

jest.mock('@siteed/audio-studio', () => ({
  AudioStudioModule: { requestPermissionsAsync: jest.fn(async () => ({ granted: true })) },
  useAudioRecorder: () => ({
    startRecording: jest.fn(async () => undefined),
    stopRecording: jest.fn(async () => undefined),
  }),
}));
jest.mock('../InterviewSessionController', () => ({
  InterviewSessionController: jest.fn().mockImplementation(() => mockController),
}));
jest.mock('@/features/audio/ContinuousTurnRecorder', () => ({
  ContinuousTurnRecorder: jest.fn(),
}));
jest.mock('@/features/realtime/ReactNativeWebRTCTransport', () => ({
  ReactNativeWebRTCTransport: jest.fn(),
}));
jest.mock('@/features/realtime/SessionMessageSocket', () => ({
  SessionMessageSocket: jest.fn(),
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
jest.mock('../InterviewSessionApi', () => ({
  InterviewSessionApi: jest.fn(),
}));

import { useInterviewSession } from '../useInterviewSession';

const idleSnapshot: InterviewSessionSnapshot = {
  state: 'idle',
  status: 'idle',
  sessionId: null,
  transcripts: [],
  muted: true,
  userMuted: false,
  turnNo: 0,
  interviewState: null,
  reportStatus: null,
  currentQuestion: '',
  error: null,
};

function createController() {
  let snapshot = idleSnapshot;
  const listeners = new Set<(next: InterviewSessionSnapshot) => void>();
  const controller = {
    getSnapshot: () => snapshot,
    subscribe: jest.fn((listener: (next: InterviewSessionSnapshot) => void) => {
      listeners.add(listener);
      listener(snapshot);
      return () => listeners.delete(listener);
    }),
    start: jest.fn(async () => undefined),
    setMuted: jest.fn(),
    end: jest.fn(async () => ({ reportStatus: 'PROCESSING' })),
    emit(next: InterviewSessionSnapshot) {
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

describe('useInterviewSession', () => {
  afterEach(async () => {
    jest.useRealTimers();
    await cleanup();
    mockController = undefined;
    jest.clearAllMocks();
  });

  it('starts, reflects active snapshots, tracks elapsed time, and delegates mute/end', async () => {
    const controller = createController();
    const analytics = analyticsFixture();
    jest.useFakeTimers();
    const { result } = await renderHook(() =>
      useInterviewSession({ sceneId: 'scene-1', voice: 'Harvey' }, analytics),
    );

    await waitFor(() => expect(controller.start).toHaveBeenCalledTimes(1));
    expect(analytics.attempt).toHaveBeenCalledTimes(1);
    expect(result.current.state).toBe('idle');
    expect(result.current.elapsed).toBe(0);

    await act(async () => {
      controller.emit({
        ...idleSnapshot,
        state: 'active',
        status: 'active',
        sessionId: 'interview-1',
        currentQuestion: 'Tell me about yourself.',
        muted: false,
      });
    });
    expect(result.current.sessionId).toBe('interview-1');
    expect(result.current.currentQuestion).toBe('Tell me about yourself.');
    expect(analytics.started).toHaveBeenCalled();
    expect(analytics.resume).toHaveBeenCalled();

    await act(async () => {
      jest.advanceTimersByTime(2_000);
    });
    expect(result.current.elapsed).toBe(2);
    await act(async () => {
      result.current.setMuted(true);
    });
    expect(controller.setMuted).toHaveBeenCalledWith(true);

    await act(async () => {
      await result.current.end();
      await result.current.end();
    });
    expect(controller.end).toHaveBeenCalledTimes(1);
    expect(analytics.complete).toHaveBeenCalledTimes(1);
  });

  it('fails analytics when startup rejects and abandons an active error', async () => {
    const controller = createController();
    controller.start.mockRejectedValueOnce(new Error('start failed'));
    const analytics = analyticsFixture();
    const { result } = await renderHook(() =>
      useInterviewSession({ sceneId: 'scene-2', voice: 'Aiden', model: 'custom-model' }, analytics),
    );

    await waitFor(() => expect(analytics.fail).toHaveBeenCalledWith('REALTIME_ERROR'));
    expect(result.current.state).toBe('idle');
    await act(async () => {
      controller.emit({ ...idleSnapshot, state: 'error', status: 'error', error: new Error('peer failed') });
    });
    expect(analytics.abandon).toHaveBeenCalledWith('REALTIME_ERROR');
  });

  it('abandons and performs idempotent cleanup on unmount', async () => {
    const controller = createController();
    const analytics = analyticsFixture();
    const { unmount } = await renderHook(() =>
      useInterviewSession({ sceneId: 'scene-3', voice: 'Mione' }, analytics),
    );

    await unmount();
    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
    });
    await waitFor(() => expect(controller.end).toHaveBeenCalledTimes(1));
    expect(controller.subscribe).toHaveBeenCalledTimes(1);
    expect(analytics.abandon).toHaveBeenCalledWith('USER_EXIT');
  });

  it('reports end failures and abandons when analytics has started', async () => {
    const controller = createController();
    controller.end.mockRejectedValueOnce(new Error('end failed'));
    const analytics = analyticsFixture();
    const { result } = await renderHook(() =>
      useInterviewSession({ sceneId: 'scene-4', voice: 'Maia' }, analytics),
    );

    await waitFor(() => expect(controller.start).toHaveBeenCalled());
    await expect(result.current.end()).rejects.toThrow('end failed');
    expect(analytics.abandon).toHaveBeenCalledWith('REALTIME_ERROR');
  });
});
