import { act, fireEvent, render, waitFor } from '@testing-library/react-native';
import { Pressable, Text, View } from 'react-native';

import type { RealtimeSessionSnapshot } from '@/features/realtime/RealtimeSessionController';

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
}: {
  createController: () => FreeChatControllerPort;
}) {
  const session = useFreeChatSession(
    {
      voice: 'Harvey',
      model: 'qwen3.5-omni-flash-realtime',
      speechSpeed: 'NATURAL',
    },
    createController,
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
    const factory = jest.fn(() => controller);
    const screen = await render(<SessionProbe createController={factory} />);

    await waitFor(() => expect(controller.start).toHaveBeenCalledTimes(1));
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
    await fireEvent.press(screen.getByLabelText('mute'));
    await fireEvent.press(screen.getByLabelText('interrupt'));

    expect(controller.setMuted).toHaveBeenCalledWith(true);
    expect(controller.interrupt).toHaveBeenCalledTimes(1);
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
