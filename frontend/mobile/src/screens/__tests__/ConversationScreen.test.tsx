import { fireEvent, render } from '@testing-library/react-native';

jest.mock('react-native-reanimated', () => {
  const { View } = require('react-native');
  return {
    __esModule: true,
    default: { View },
    cancelAnimation: jest.fn(),
    Easing: {
      cubic: jest.fn(),
      ease: jest.fn(),
      linear: jest.fn(),
      inOut: (value: unknown) => value,
      out: (value: unknown) => value,
    },
    interpolate: (_value: number, _input: number[], output: number[]) => output[0],
    runOnJS: (fn: (...args: unknown[]) => unknown) => fn,
    useAnimatedStyle: (factory: () => unknown) => factory(),
    useSharedValue: (value: unknown) => ({ value }),
    withDelay: (_delay: number, value: unknown) => value,
    withRepeat: (value: unknown) => value,
    withTiming: (value: unknown) => value,
  };
});

jest.mock('@/model/AppModel', () => ({
  useAppModel: () => ({
    teacher: {
      id: 'james',
      name: 'James',
      accent: 'American English',
      voiceId: 'Harvey',
      image: 1,
    },
  }),
}));

import { CallExperience, selectCallCaption } from '../ConversationScreen';

describe('selectCallCaption', () => {
  it('shows the learner transcript after their turn instead of an empty or stale AI transcript', () => {
    expect(
      selectCallCaption(
        {
          state: 'ready',
          error: null,
          userTranscript: 'I would like a latte.',
          assistantTranscript: '',
        },
        'James',
        '可以开始说了',
      ),
    ).toEqual({ speaker: '你', text: 'I would like a latte.' });
  });

  it('shows the current AI transcript while the assistant is speaking', () => {
    expect(
      selectCallCaption(
        {
          state: 'assistant_speaking',
          error: null,
          userTranscript: 'I would like a latte.',
          assistantTranscript: 'Would you like oat milk?',
        },
        'James',
        'AI 正在回答',
      ),
    ).toEqual({ speaker: 'James', text: 'Would you like oat milk?' });
  });
});

describe('CallExperience realtime binding', () => {
  it('keeps the learner subtitle visible when the AI response has already started', async () => {
    const screen = await render(
      <CallExperience
        onEnd={jest.fn()}
        transcriptSpeaker="James"
        transcriptEnglish="Would you like to keep practicing?"
        userTranscript="I would like to practice English today."
      />,
    );

    expect(screen.getByText('你')).toBeTruthy();
    expect(
      screen.getByText('I would like to practice English today.'),
    ).toBeTruthy();
    expect(screen.getByText('James')).toBeTruthy();
    expect(
      screen.getByText('Would you like to keep practicing?'),
    ).toBeTruthy();
  });

  it('renders controlled elapsed/transcript state and delegates mute/end actions', async () => {
    const onMutedChange = jest.fn();
    const onEnd = jest.fn();
    const screen = await render(
      <CallExperience
        onEnd={onEnd}
        elapsed={42}
        muted
        statusLabel="正在连接 AI"
        transcriptEnglish="Live assistant transcript."
        onMutedChange={onMutedChange}
      />,
    );

    expect(screen.getByText('已暂停 · 00:42')).toBeTruthy();
    expect(screen.getByText('Live assistant transcript.')).toBeTruthy();
    await fireEvent.press(screen.getByLabelText('恢复会话'));
    await fireEvent.press(screen.getByLabelText('结束当前会话'));

    expect(onMutedChange).toHaveBeenCalledWith(false);
    expect(onEnd).toHaveBeenCalledTimes(1);
  });
});
