import { act, fireEvent, render, waitFor } from '@testing-library/react-native';

const mockSession = {
  elapsed: 12,
  muted: false,
  statusLabel: '可以开始说了',
  state: 'ready',
  error: null,
  sessionId: 'session-1' as string | null,
  userTranscript: 'Hello',
  assistantTranscript: 'How can I help?',
  transcriptHistory: [],
  toggleMuted: jest.fn(),
  end: jest.fn<Promise<void>, []>(async () => undefined),
};
const mockTranslate = jest.fn();
const mockSaveSettings = jest.fn(async () => undefined);

jest.mock('react-native-reanimated', () => {
  const { View } = require('react-native');
  return {
    __esModule: true, default: { View }, cancelAnimation: jest.fn(),
    Easing: { cubic: jest.fn(), ease: jest.fn(), linear: jest.fn(), inOut: (value: unknown) => value, out: (value: unknown) => value },
    interpolate: (_value: number, _input: number[], output: number[]) => output[0], runOnJS: (fn: (...args: unknown[]) => unknown) => fn,
    useAnimatedStyle: (factory: () => unknown) => factory(), useSharedValue: (value: unknown) => ({ value }),
    withDelay: (_delay: number, value: unknown) => value, withRepeat: (value: unknown) => value,
    withTiming: (value: unknown, _config?: unknown, callback?: (finished: boolean) => void) => { callback?.(true); return value; },
  };
});

jest.mock('@/features/conversation/useFreeChatSession', () => ({ useFreeChatSession: jest.fn(() => mockSession) }));
jest.mock('@/features/conversation/TranscriptTranslationApi', () => ({ createTranscriptTranslationApi: () => ({ translateFreeChat: mockTranslate }) }));
jest.mock('react-native-safe-area-context', () => {
  const { View } = require('react-native');
  return { SafeAreaView: View, useSafeAreaInsets: () => ({ top: 0, bottom: 0 }) };
});
jest.mock('@/model/AppModel', () => ({
  useAppModel: () => ({
    nickname: 'Ada', speed: '自然', level: 'starter', saveConversationSettings: mockSaveSettings,
    teacher: { id: 'james', name: 'James', accent: 'American English', voiceId: 'Harvey', image: 1 },
  }),
}));

import { CallExperience, CallScreen, ConversationScreen, selectCallCaption } from '../ConversationScreen';

describe('ConversationScreen coverage', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    Object.assign(mockSession, { error: null, sessionId: 'session-1', state: 'ready', muted: false });
  });

  it('selects error, speaking, and status captions without stale text', () => {
    expect(selectCallCaption({ ...mockSession, error: { message: '连接失败' } } as any, 'James', '准备中')).toEqual({ speaker: '系统', text: '连接失败' });
    expect(selectCallCaption({ ...mockSession, state: 'user_speaking', userTranscript: '' } as any, 'James', '正在聆听')).toEqual({ speaker: '你', text: '正在聆听' });
    expect(selectCallCaption({ ...mockSession, state: 'assistant_speaking', assistantTranscript: '' } as any, 'James', 'AI 正在回答')).toEqual({ speaker: 'James', text: 'AI 正在回答' });
  });

  it('shows a translation failure and toggles subtitle visibility', async () => {
    const screen = await render(<CallExperience onEnd={jest.fn()} onTranslate={jest.fn(async () => { throw new Error('翻译服务不可用'); })} transcriptEnglish="Would you like tea?" />);
    await fireEvent.press(screen.getByLabelText('翻译'));
    await waitFor(() => expect(screen.getByText('翻译服务不可用')).toBeTruthy());
    await fireEvent.press(screen.getByLabelText('关闭字幕'));
    expect(screen.getByLabelText('打开字幕')).toBeTruthy();
    screen.unmount();
  });

  it('expands and collapses an existing translation and uses internal timer and mute state', async () => {
    jest.useFakeTimers();
    const screen = await render(
      <CallExperience onEnd={jest.fn()} transcriptEnglish="Would you like tea?" transcriptChinese="想喝茶吗？" />,
    );
    await fireEvent.press(screen.getByLabelText('翻译'));
    expect(screen.getByText('想喝茶吗？')).toBeTruthy();
    await fireEvent.press(screen.getByLabelText('收起翻译'));
    expect(screen.queryByText('想喝茶吗？')).toBeNull();
    await fireEvent.press(screen.getByLabelText('关闭麦克风'));
    expect(screen.getByLabelText('打开麦克风')).toBeTruthy();
    await fireEvent.press(screen.getByLabelText('打开麦克风'));
    await act(async () => { await jest.advanceTimersByTimeAsync(1_000); });
    await waitFor(() => expect(screen.getByText('00:01')).toBeTruthy());
    screen.unmount();
    jest.useRealTimers();
  });

  it('binds CallScreen mute, translation, and end actions to the realtime session', async () => {
    const onEnd = jest.fn();
    mockTranslate.mockResolvedValue('你好');
    const screen = await render(<CallScreen onEnd={onEnd} />);
    await fireEvent.press(screen.getByLabelText('关闭麦克风'));
    expect(mockSession.toggleMuted).toHaveBeenCalledTimes(1);
    await fireEvent.press(screen.getByLabelText('结束当前会话'));
    await waitFor(() => expect(mockSession.end).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(onEnd).toHaveBeenCalledTimes(1));
    screen.unmount();
  });

  it('reports translation before a backend session is connected', async () => {
    mockSession.sessionId = null;
    const screen = await render(<CallScreen onEnd={jest.fn()} />);
    await fireEvent.press(screen.getByLabelText('翻译'));
    await waitFor(() => expect(screen.getByText('会话尚未连接，暂时无法翻译')).toBeTruthy());
    screen.unmount();
  });

  it('delegates externally started calls and saves settings from the home screen', async () => {
    const onStartCall = jest.fn();
    const screen = await render(<ConversationScreen onStartCall={onStartCall} />);
    await fireEvent.press(screen.getByText('开始对话'));
    expect(onStartCall).toHaveBeenCalledTimes(1);
    await fireEvent.press(screen.getByLabelText('对话设置'));
    await fireEvent.press(screen.getByText('慢一些'));
    await fireEvent.press(screen.getByText('保存设置'));
    await waitFor(() => expect(mockSaveSettings).toHaveBeenCalledWith(expect.objectContaining({ speed: '慢一些' })));
    screen.unmount();
  });

  it('runs the internal call transition, immersive callback, and settings error branches', async () => {
    let finishEnd!: () => void;
    mockSession.end.mockReturnValueOnce(new Promise<void>((resolve) => { finishEnd = resolve; }));
    const immersive = jest.fn();
    const screen = await render(<ConversationScreen onImmersiveChange={immersive} />);
    const settings = screen.getByLabelText('对话设置');
    fireEvent(settings, 'pressIn');
    fireEvent(settings, 'hoverIn');
    fireEvent(settings, 'pressOut');
    fireEvent(settings, 'hoverOut');
    await fireEvent.press(settings);
    mockSaveSettings.mockRejectedValueOnce('offline');
    await fireEvent.press(screen.getByText('保存设置'));
    await waitFor(() => expect(mockSaveSettings).toHaveBeenCalled());
    await fireEvent.press(screen.getByText('取消'));
    await fireEvent.press(screen.getByText('开始对话'));
    await waitFor(() => expect(screen.getByLabelText('结束当前会话')).toBeTruthy());
    expect(immersive).toHaveBeenCalledWith(true);
    await fireEvent.press(screen.getByLabelText('结束当前会话'));
    expect(mockSession.end).toHaveBeenCalled();
    await act(async () => { finishEnd(); await Promise.resolve(); });
    screen.unmount();
    expect(immersive).toHaveBeenCalledWith(false);
  });
});
