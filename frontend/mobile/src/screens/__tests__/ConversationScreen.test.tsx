import { fireEvent, render, waitFor } from '@testing-library/react-native';

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

  it('renders completed dialogue turns in their original order', async () => {
    const screen = await render(
      <CallExperience
        onEnd={jest.fn()}
        transcriptEnglish="What kind of coffee do you prefer?"
        transcriptHistory={[
          { id: '0:assistant-1', owner: 0, content: 'Welcome. What would you like?' },
          { id: '1:user-1', owner: 1, content: 'I would like a latte.' },
        ]}
      />,
    );

    const transcriptTexts = screen.getAllByText(
      /Welcome\. What would you like\?|I would like a latte\.|What kind of coffee do you prefer\?/,
    );
    expect(transcriptTexts.map((node) => node.props.children)).toEqual([
      'Welcome. What would you like?',
      'I would like a latte.',
      'What kind of coffee do you prefer?',
    ]);
  });

  it('can hide learner subtitles while preserving assistant history', async () => {
    const screen = await render(
      <CallExperience
        initialSubtitles
        onEnd={jest.fn()}
        showUserTranscript={false}
        transcriptEnglish="What do you usually do on weekends?"
        transcriptHistory={[
          { id: 'assistant-1', owner: 0, content: 'Let us begin with your hometown.' },
          { id: 'user-1', owner: 1, content: 'I live in Shanghai.' },
        ]}
        userTranscript="I often read books."
      />,
    );

    expect(screen.getByText('Let us begin with your hometown.')).toBeTruthy();
    expect(screen.queryByText('I live in Shanghai.')).toBeNull();
    expect(screen.queryByText('I often read books.')).toBeNull();
  });

  it('does not flash the current learner caption when learner subtitles are disabled', async () => {
    const screen = await render(
      <CallExperience
        initialSubtitles
        onEnd={jest.fn()}
        showUserTranscript={false}
        transcriptEnglish="My answer must stay hidden."
        transcriptSpeaker="你"
        userTranscript="My answer must stay hidden."
      />,
    );

    expect(screen.queryByText('My answer must stay hidden.')).toBeNull();
    expect(screen.queryByText('你')).toBeNull();
  });

  it('translates an assistant message without replacing the dialogue history', async () => {
    const onTranslate = jest.fn(async () => '欢迎，请问您需要什么？');
    const screen = await render(
      <CallExperience
        onEnd={jest.fn()}
        onTranslate={onTranslate}
        transcriptEnglish="What would you like?"
        transcriptHistory={[
          { id: 'assistant-1', owner: 0, content: 'Welcome.' },
          { id: 'user-1', owner: 1, content: 'A coffee, please.' },
        ]}
      />,
    );

    await fireEvent.press(screen.getAllByLabelText('翻译')[0]);
    await waitFor(() => expect(screen.getByText('欢迎，请问您需要什么？')).toBeTruthy());
    expect(onTranslate).toHaveBeenCalledWith('Welcome.');
    expect(screen.getByText('A coffee, please.')).toBeTruthy();
  });
});
