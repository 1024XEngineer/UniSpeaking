import { StrictMode } from 'react';
import { act, fireEvent, render, waitFor } from '@testing-library/react-native';
import { Pressable, Text, View } from 'react-native';

import type { RealtimeSessionSnapshot } from '@/features/realtime/RealtimeSessionController';
import type { TrainingTracker } from '@/infrastructure/analytics/AnalyticsClient';

import {
  type FreeChatControllerPort,
  useFreeChatSession,
} from '../useFreeChatSession';

const idleSnapshot: RealtimeSessionSnapshot = {
  state: 'idle',
  muted: false,
  sessionId: null,
  userTranscript: '',
  assistantTranscript: '',
  transcriptHistory: [],
  error: null,
};

function createController(): FreeChatControllerPort & {
  emit(snapshot: RealtimeSessionSnapshot): void;
  start: jest.Mock;
  setMuted: jest.Mock;
  interrupt: jest.Mock;
  end: jest.Mock;
} {
  let snapshot = idleSnapshot;
  let listener: ((next: RealtimeSessionSnapshot) => void) | null = null;
  return {
    getSnapshot: () => snapshot,
    subscribe: jest.fn((nextListener) => {
      listener = nextListener;
      nextListener(snapshot);
      return () => {
        listener = null;
      };
    }),
    start: jest.fn(async () => undefined),
    setMuted: jest.fn(),
    interrupt: jest.fn(),
    end: jest.fn(async () => undefined),
    emit(next) {
      snapshot = next;
      listener?.(snapshot);
    },
  };
}

function SessionProbe({
  createController,
  analytics,
}: {
  createController: () => FreeChatControllerPort;
  analytics?: TrainingTracker;
}) {
  const session = useFreeChatSession(
    {
      voice: 'Harvey',
      model: 'qwen3.5-omni-flash-realtime',
      speechSpeed: 'NATURAL',
    },
    createController,
    analytics,
  );
  return (
    <View>
      <Text testID="snapshot">
        {JSON.stringify({
          state: session.state,
          label: session.statusLabel,
          muted: session.muted,
          user: session.userTranscript,
          assistant: session.assistantTranscript,
          error: session.error,
          elapsed: session.elapsed,
        })}
      </Text>
      <Pressable accessibilityLabel="mute" onPress={session.toggleMuted} />
      <Pressable accessibilityLabel="interrupt" onPress={session.interrupt} />
      <Pressable accessibilityLabel="end" onPress={() => void session.end()} />
    </View>
  );
}

describe('useFreeChatSession', () => {
  it('starts once and exposes live state, transcripts, mute and interrupt actions', async () => {
    const controller = createController();
    const analytics = {
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
    const factory = jest.fn(() => controller);
    const screen = await render(<SessionProbe analytics={analytics} createController={factory} />);

    await waitFor(() => expect(controller.start).toHaveBeenCalledTimes(1));
    expect(analytics.attempt).toHaveBeenCalledTimes(1);
    expect(factory).toHaveBeenCalledWith({
      voice: 'Harvey',
      model: 'qwen3.5-omni-flash-realtime',
      speechSpeed: 'NATURAL',
    });

    await act(() => {
      controller.emit({
        ...idleSnapshot,
        state: 'assistant_speaking',
        sessionId: 'session-1',
        userTranscript: 'Hello.',
        assistantTranscript: 'Hi, how are you?',
      });
    });
    await waitFor(() =>
      expect(screen.getByTestId('snapshot').props.children).toContain(
        'AI 正在回答',
      ),
    );
    expect(analytics.started).toHaveBeenCalledTimes(1);
    expect(analytics.resume).toHaveBeenCalledTimes(1);
    await fireEvent.press(screen.getByLabelText('mute'));
    await fireEvent.press(screen.getByLabelText('interrupt'));

    expect(controller.setMuted).toHaveBeenCalledWith(true);
    expect(controller.interrupt).toHaveBeenCalledTimes(1);
    await fireEvent.press(screen.getByLabelText('end'));
    await waitFor(() => expect(analytics.complete).toHaveBeenCalledTimes(1));
  });

  it('does not end and restart the same controller during StrictMode effect replay', async () => {
    const controller = createController();
    const screen = await render(
      <StrictMode>
        <SessionProbe createController={() => controller} />
      </StrictMode>,
    );

    await waitFor(() => expect(controller.start).toHaveBeenCalledTimes(1));
    expect(controller.end).not.toHaveBeenCalled();

    screen.unmount();
    await waitFor(() => expect(controller.end).toHaveBeenCalledTimes(1));
  });

  it('stops incrementing elapsed time while the completed session is ending', async () => {
    jest.useFakeTimers();
    const controller = createController();
    const screen = await render(
      <SessionProbe createController={() => controller} />,
    );

    await act(async () => {
      controller.emit({ ...idleSnapshot, state: 'ready' });
      await Promise.resolve();
    });
    await act(() => {
      jest.advanceTimersByTime(2_000);
    });
    expect(screen.getByTestId('snapshot').props.children).toContain(
      '"elapsed":2',
    );

    await act(async () => {
      controller.emit({ ...idleSnapshot, state: 'ending' });
      await Promise.resolve();
    });
    await act(() => {
      jest.advanceTimersByTime(2_000);
    });
    expect(screen.getByTestId('snapshot').props.children).toContain(
      '"elapsed":2',
    );

    screen.unmount();
    jest.useRealTimers();
  });

  it('exposes startup errors and performs idempotent cleanup after ending', async () => {
    const controller = createController();
    controller.start.mockRejectedValue(new Error('麦克风权限被拒绝'));
    const screen = await render(
      <SessionProbe createController={() => controller} />,
    );

    await waitFor(() =>
      expect(screen.getByTestId('snapshot').props.children).toContain(
        '麦克风权限被拒绝',
      ),
    );
    await fireEvent.press(screen.getByLabelText('end'));
    screen.unmount();

    await waitFor(() => expect(controller.end).toHaveBeenCalledTimes(1));
  });
});
