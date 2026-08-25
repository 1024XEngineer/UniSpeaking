import { act, fireEvent, render, waitFor } from '@testing-library/react-native';
import { Alert, BackHandler, Platform } from 'react-native';

const mockIelts = {
  settingsLoading: false, settings: null as any, categories: [] as any[], topics: [] as any[], topicsLoading: false, topicsError: null as any,
  topicTotal: 0, topicTotalPages: 0, generated: null as any, latestEvaluation: null as any, training: null as any, sessionBusy: false, sessionError: null,
  loadTopics: jest.fn(), refreshSettings: jest.fn(), finalizeEvaluation: jest.fn(), saveTargetScore: jest.fn(async () => undefined),
  prepareSession: jest.fn(), scoreCompletedPart: jest.fn(), formatBand: (value: number) => String(value), practiceTypeLabel: () => '暂无记录',
};
const mockSaveLevel = jest.fn(async () => undefined);
const mockInterviewStart = jest.fn(async () => undefined);
const mockInterviewConfirm = jest.fn(async () => undefined);
const mockInterviewPickResume = jest.fn(async () => undefined);
const mockInterviewEnd = jest.fn(async () => undefined);
const mockInterviewSetMuted = jest.fn();
let mockResumeMode: 'text' | 'file' = 'text';
let mockResumeFileName: string | null = null;
const mockSetResumeMode = jest.fn((value: 'text' | 'file') => { mockResumeMode = value; });
const mockSetResumeText = jest.fn();
const mockInterviewApi = { getReport: jest.fn() };
const mockInterviewSession: any = {
  state: 'starting', sessionId: null, elapsed: 2, userMuted: false, error: null,
  interviewState: { currentTopic: '自我介绍' }, currentQuestion: '请介绍自己',
};
const mockIeltsEnd = jest.fn(async () => undefined);
let mockIeltsSessionId: string | null = 'session-1';
const mockWaitForTurnEvaluations = jest.fn(async () => undefined);
const mockForcePart3Timeout = jest.fn(async () => undefined);
const mockTransitionPart2 = jest.fn(async (event: string) => ({
  sceneId: 'ielts-2', sessionId: 'session-1', phase: event === 'PREPARATION_COMPLETE' ? 'LONG_TURN' : 'FINISHED',
  completed: event !== 'PREPARATION_COMPLETE', controlInstruction: 'continue',
}));
const mockToggleMuted = jest.fn();
const mockAppModel: any = {
  addIeltsRecord: jest.fn(), hasCompletedOnboarding: false, level: 'starter', saveLevel: mockSaveLevel, signOut: jest.fn(),
  teacher: { voiceId: 'Harvey', name: 'Sophia', image: 1 },
};
const mockIeltsSnapshot: any = { state: 'ready', error: null, userTranscript: '', assistantTranscript: '', transcriptHistory: [], ieltsCompletionReady: false, ieltsDialogueState: null };
const mockPreparedInterview = {
  scene: null,
  material: {
    jobTitle: '产品经理', otherJobInformation: '', responsibilities: ['负责产品策略'], qualificationRequirements: [],
    requiredSkills: [], education: [], workExperiences: [], projectExperiences: [], skillsAndAbilities: [], interviewableExperienceClues: [],
  },
  jobTitle: '产品经理',
};

jest.mock('react-native-reanimated', () => {
  const { View } = require('react-native');
  return {
    __esModule: true, default: { View }, cancelAnimation: jest.fn(),
    Easing: { cubic: jest.fn(), ease: jest.fn(), linear: jest.fn(), inOut: (value: unknown) => value, out: (value: unknown) => value },
    interpolate: (_value: number, _input: number[], output: number[]) => output[0], runOnJS: (fn: (...args: unknown[]) => unknown) => fn,
    useAnimatedStyle: (factory: () => unknown) => factory(), useSharedValue: (value: unknown) => ({ value }),
    withDelay: (_delay: number, value: unknown) => value, withRepeat: (value: unknown) => value, withTiming: (value: unknown) => value,
  };
});
jest.mock('@/features/ielts/useIeltsFlowController', () => ({ useIeltsFlowController: () => mockIelts }));
jest.mock('@/features/ielts/useIeltsSession', () => ({
  useIeltsSession: () => ({
    snapshot: mockIeltsSnapshot,
    statusLabel: '可以开始说了', sessionId: mockIeltsSessionId, end: mockIeltsEnd,
    waitForTurnEvaluations: mockWaitForTurnEvaluations, forcePart3Timeout: mockForcePart3Timeout,
    transitionPart2: mockTransitionPart2,
    toggleMuted: mockToggleMuted,
  }),
}));
jest.mock('@/features/interview/useInterviewPreparation', () => ({
  useInterviewPreparation: () => ({ resumeFileName: mockResumeFileName, resumeText: '', resumeMode: mockResumeMode, setResumeText: mockSetResumeText, setResumeMode: mockSetResumeMode, isPreparing: false, error: '材料解析失败', pickResume: mockInterviewPickResume, start: mockInterviewStart, confirm: mockInterviewConfirm }),
}));
jest.mock('@/features/interview/useInterviewSession', () => ({
  createInterviewApi: jest.fn(() => mockInterviewApi),
  useInterviewSession: () => ({
    ...mockInterviewSession,
    end: mockInterviewEnd,
    setMuted: mockInterviewSetMuted,
  }),
}));
jest.mock('@/features/interview/InterviewReportView', () => {
  const { Text } = require('react-native');
  return { InterviewReportView: ({ sessionId }: { sessionId: string }) => <Text>报告 {sessionId}</Text> };
});
jest.mock('@/model/AppModel', () => ({ useAppModel: () => mockAppModel }));
jest.mock('@/navigation/learningStage', () => ({ useLearningStage: () => ({ setImmersiveLearning: jest.fn() }) }));
jest.mock('@/navigation/specialtyMemory', () => ({ rememberSpecialty: jest.fn() }));
jest.mock('react-native-safe-area-context', () => { const { View } = require('react-native'); return { SafeAreaView: View }; });

import { compactPerformanceSummary, formatSessionDuration, IeltsFlow, IeltsPart2Session, IeltsSession, InterviewFlow } from '../SpecialtyFlows';

afterEach(() => {
  mockAppModel.hasCompletedOnboarding = false;
  jest.useRealTimers();
});

beforeEach(() => {
  mockAppModel.hasCompletedOnboarding = false;
  mockIelts.settings = null;
  mockIelts.saveTargetScore.mockReset().mockResolvedValue(undefined);
  mockSaveLevel.mockReset().mockResolvedValue(undefined);
  mockInterviewStart.mockReset().mockResolvedValue(undefined);
  mockInterviewConfirm.mockReset().mockResolvedValue(undefined);
  mockInterviewPickResume.mockReset().mockResolvedValue(undefined);
  mockInterviewEnd.mockReset().mockResolvedValue(undefined);
  mockInterviewSetMuted.mockReset();
  mockResumeMode = 'text';
  mockResumeFileName = null;
  mockSetResumeMode.mockClear();
  mockSetResumeText.mockClear();
  Object.assign(mockInterviewSession, {
    state: 'starting', sessionId: null, elapsed: 2, userMuted: false, error: null,
    interviewState: { currentTopic: '自我介绍' }, currentQuestion: '请介绍自己',
  });
  mockIeltsEnd.mockReset().mockResolvedValue(undefined);
  mockIeltsSessionId = 'session-1';
  mockIelts.scoreCompletedPart.mockReset().mockResolvedValue(undefined);
  mockWaitForTurnEvaluations.mockReset().mockResolvedValue(undefined);
  mockForcePart3Timeout.mockReset().mockResolvedValue(undefined);
  mockTransitionPart2.mockReset().mockImplementation(async (event: string) => ({
    sceneId: 'ielts-2', sessionId: 'session-1', phase: event === 'PREPARATION_COMPLETE' ? 'LONG_TURN' : 'FINISHED',
    completed: event !== 'PREPARATION_COMPLETE', controlInstruction: 'continue',
  }));
  mockToggleMuted.mockReset();
  mockIeltsSnapshot.state = 'ready';
  mockIeltsSnapshot.ieltsInputReadyTick = undefined;
  mockIeltsSnapshot.ieltsDialogueCompleted = false;
  mockIeltsSnapshot.ieltsCompletionReady = false;
  mockIeltsSnapshot.ieltsPart2CompletionReady = false;
  mockIeltsSnapshot.ieltsStateRestored = false;
  mockIeltsSnapshot.ieltsPart2State = null;
});

describe('IeltsFlow intake', () => {
  it('formats IELTS timers and compact performance fallbacks', () => {
    expect(formatSessionDuration(0)).toBe('00:00');
    expect(formatSessionDuration(125)).toBe('02:05');
    expect(compactPerformanceSummary(null)).toBe('已评分');
    expect(compactPerformanceSummary('Great')).toBe('Great');
    expect(compactPerformanceSummary('回答组织完整，表达自然。')).toBe('回答组织完整…');
  });

  it('handles direct IELTS translation, exit confirmation, and duplicate finish controls', async () => {
    mockIeltsSessionId = null;
    const onExit = jest.fn();
    const onFinish = jest.fn();
    const alert = jest.spyOn(Alert, 'alert').mockImplementation(jest.fn());
    const view = await render(
      <IeltsSession
        examiner={{ id: 'direct', name: 'Examiner', accent: 'British', description: '', voiceId: 'voice', image: 1 } as any}
        part="p1"
        ieltsId="ielts-direct"
        voiceId="voice"
        autoAdvance={false}
        onExit={onExit}
        onFinish={onFinish}
      />,
    );
    await fireEvent.press(view.getByLabelText('打开字幕'));
    await fireEvent.press(view.getByLabelText('翻译'));
    await waitFor(() => expect(view.getByText('会话尚未连接，暂时无法翻译')).toBeTruthy());
    await fireEvent.press(view.getByLabelText('退出雅思训练'));
    const buttons = alert.mock.calls.at(-1)?.[2] as Array<{ onPress?: () => void }>;
    buttons[1].onPress?.();
    expect(onExit).toHaveBeenCalled();
    await fireEvent.press(view.getByLabelText('结束本题并进入下一题'));
    await fireEvent.press(view.getByLabelText('结束本题并进入下一题'));
    expect(onFinish).toHaveBeenCalledTimes(1);
    alert.mockRestore();
  });

  it('finishes direct Part 2 after silence and recovers from a non-Error transition failure', async () => {
    jest.useFakeTimers();
    mockIeltsSnapshot.state = 'assistant_speaking';
    const view = await render(
      <IeltsPart2Session
        examiner={{ id: 'direct', name: 'Examiner', accent: 'British', description: '', voiceId: 'voice', image: 1 } as any}
        cueCard={{ title: 'Describe a trip', points: ['where', 'when'] } as any}
        ieltsId="ielts-direct"
        voiceId="voice"
        onExit={jest.fn()}
        onFinish={jest.fn()}
      />,
    );
    mockIeltsSnapshot.state = 'ready';
    await act(async () => { view.rerender(
      <IeltsPart2Session examiner={{ name: 'Examiner', image: 1 } as any} cueCard={{ title: 'Describe a trip', points: ['where'] } as any} ieltsId="ielts-direct" voiceId="voice" onExit={jest.fn()} onFinish={jest.fn()} />,
    ); });
    await waitFor(() => expect(view.getByLabelText('提前开始作答')).toBeTruthy());
    await fireEvent(view.getByPlaceholderText('记录关键词…'), 'focus');
    await fireEvent.press(view.getByLabelText('提前开始作答'));
    mockIeltsSnapshot.ieltsInputReadyTick = 1;
    await act(async () => { view.rerender(
      <IeltsPart2Session examiner={{ name: 'Examiner', image: 1 } as any} cueCard={{ title: 'Describe a trip', points: ['where'] } as any} ieltsId="ielts-direct" voiceId="voice" onExit={jest.fn()} onFinish={jest.fn()} />,
    ); });
    await waitFor(() => expect(view.getByLabelText('结束 Part 2')).toBeTruthy());
    mockIeltsSnapshot.state = 'user_speaking';
    await act(async () => { view.rerender(
      <IeltsPart2Session examiner={{ name: 'Examiner', image: 1 } as any} cueCard={{ title: 'Describe a trip', points: ['where'] } as any} ieltsId="ielts-direct" voiceId="voice" onExit={jest.fn()} onFinish={jest.fn()} />,
    ); });
    mockTransitionPart2.mockRejectedValueOnce('offline');
    mockIeltsSnapshot.state = 'ready';
    await act(async () => { view.rerender(
      <IeltsPart2Session examiner={{ name: 'Examiner', image: 1 } as any} cueCard={{ title: 'Describe a trip', points: ['where'] } as any} ieltsId="ielts-direct" voiceId="voice" onExit={jest.fn()} onFinish={jest.fn()} />,
    ); });
    await act(async () => { await jest.advanceTimersByTimeAsync(3_000); });
    await waitFor(() => expect(view.getByText('无法结束 Part 2')).toBeTruthy());
    jest.useRealTimers();
  });

  beforeEach(() => {
    jest.clearAllMocks();
    mockIelts.settings = null;
    mockIelts.settingsLoading = false;
    mockIelts.topics = [];
    mockIelts.topicsLoading = false;
    mockIelts.topicsError = null;
    mockIelts.topicTotal = 0;
    mockIelts.topicTotalPages = 0;
    mockIelts.generated = null;
    mockIelts.latestEvaluation = null;
    mockIelts.training = null;
    mockIeltsSnapshot.state = 'ready';
    mockIeltsSnapshot.ieltsInputReadyTick = undefined;
    mockIeltsSnapshot.ieltsDialogueCompleted = false;
    mockIeltsSnapshot.ieltsCompletionReady = false;
    mockIeltsSnapshot.ieltsStateRestored = false;
    mockIeltsSnapshot.ieltsPart2State = null;
    mockIeltsSnapshot.ieltsPart2CompletionReady = false;
    mockAppModel.addIeltsRecord = jest.fn();
    mockAppModel.hasCompletedOnboarding = false;
  });

  it('persists selected target and language level before opening the IELTS home', async () => {
    const screen = await render(<IeltsFlow onExit={jest.fn()} />);
    expect(screen.getByText('目标 7.0')).toBeTruthy();
    await fireEvent.press(screen.getByText('目标 6.5'));
    await fireEvent.press(screen.getByText('下一步'));
    await waitFor(() => expect(screen.getByText('可以简单交流')).toBeTruthy());
    await fireEvent.press(screen.getByText('可以简单交流'));
    await fireEvent.press(screen.getByText('进入 IELTS 专项'));
    await waitFor(() => expect(mockIelts.saveTargetScore).toHaveBeenCalledWith('6.5'));
    await waitFor(() => expect(mockSaveLevel).toHaveBeenCalledWith('basic'));
    await waitFor(() => expect(screen.getByText('完整模拟一场 IELTS 口语考试')).toBeTruthy());
    screen.unmount();
  });

  it('shows the settings loading overlay before the first intake choice is available', async () => {
    mockIelts.settingsLoading = true;
    const screen = await render(<IeltsFlow onExit={jest.fn()} />);
    expect(screen.getByText('正在读取你的 IELTS 学习设置…')).toBeTruthy();
    screen.unmount();
  });

  it('keeps the intake form open and exposes a save failure', async () => {
    mockIelts.saveTargetScore.mockRejectedValueOnce(new Error('目标保存失败'));
    const screen = await render(<IeltsFlow onExit={jest.fn()} />);
    await fireEvent.press(screen.getByText('下一步'));
    await fireEvent.press(screen.getByText('进入 IELTS 专项'));
    await waitFor(() => expect(screen.getByText('目标保存失败')).toBeTruthy());
    expect(screen.getByText('进入 IELTS 专项')).toBeTruthy();
    screen.unmount();
  });

  it('skips onboarding when the account already has IELTS settings', async () => {
    mockAppModel.hasCompletedOnboarding = true;
    mockIelts.settings = { targetScore: 6.5, examinerId: 'daniel', currentStreakDays: 2, todayCompletedCount: 1 };
    const screen = await render(<IeltsFlow onExit={jest.fn()} />);
    await waitFor(() => expect(screen.getByText('完整模拟一场 IELTS 口语考试')).toBeTruthy());
    expect(screen.queryByText('目标 7.0')).toBeNull();
    screen.unmount();
  });


  it('moves from home to a topic and examiner, then keeps the chooser open when preparation fails', async () => {
    mockIelts.topics = [{
      id: 'topic-1', title: 'Food', categoryLabel: '事物', questionCount: 5,
      practiceCount: 0, latestPerformanceScore: null, latestPerformanceSummary: null, latestPracticeType: null,
    }];
    mockIelts.topicTotal = 1;
    mockIelts.topicTotalPages = 1;
    mockIelts.prepareSession.mockRejectedValueOnce(new Error('实时会话不可用'));
    const screen = await render(<IeltsFlow onExit={jest.fn()} />);
    await fireEvent.press(screen.getByText('下一步'));
    await fireEvent.press(screen.getByText('进入 IELTS 专项'));
    await waitFor(() => expect(screen.getByText('快速开始训练')).toBeTruthy());
    await fireEvent.press(screen.getByText('日常问答'));
    await waitFor(() => expect(screen.getByText('选择一个话题，正式开始后才会由考官揭晓具体问题。')).toBeTruthy());
    await waitFor(() => expect(mockIelts.loadTopics).toHaveBeenCalledWith('p1', 'ALL', '', 1));
    await fireEvent.press(screen.getByLabelText('Food，暂无记录'));
    await waitFor(() => expect(screen.getByText('选择本次考官')).toBeTruthy());
    await fireEvent.press(screen.getByText('确认考官并开始'));
    await waitFor(() => expect(mockIelts.prepareSession).toHaveBeenCalledWith(expect.objectContaining({ part: 'p1', topicId: 'topic-1', random: false })));
    expect(screen.getByText('选择本次考官')).toBeTruthy();
    screen.unmount();
  });

  it('opens the Part 1 realtime session after a successful examiner confirmation', async () => {
    mockIelts.topics = [{ id: 'topic-2', title: 'Work', categoryLabel: '生活', questionCount: 4, practiceCount: 0, latestPerformanceScore: null, latestPerformanceSummary: null, latestPracticeType: null }];
    mockIelts.topicTotal = 1;
    mockIelts.topicTotalPages = 1;
    mockIelts.generated = { ieltsId: 'ielts-2', title: 'Work' };
    mockIelts.prepareSession.mockResolvedValueOnce(mockIelts.generated);
    const screen = await render(<IeltsFlow onExit={jest.fn()} />);
    await fireEvent.press(screen.getByText('下一步'));
    await fireEvent.press(screen.getByText('进入 IELTS 专项'));
    await waitFor(() => expect(screen.getByText('快速开始训练')).toBeTruthy());
    await fireEvent.press(screen.getByText('日常问答'));
    await waitFor(() => expect(screen.getByLabelText('Work，暂无记录')).toBeTruthy());
    await fireEvent.press(screen.getByLabelText('Work，暂无记录'));
    await waitFor(() => expect(screen.getByText('选择本次考官')).toBeTruthy());
    await fireEvent.press(screen.getByText('确认考官并开始'));
    await waitFor(() => expect(screen.getByLabelText('结束本题并进入下一题')).toBeTruthy());
    screen.unmount();
  });

  it('confirms exit from a live Part 1 session and completes from provider state', async () => {
    const onExit = jest.fn();
    const alert = jest.spyOn(Alert, 'alert').mockImplementation(jest.fn());
    mockIelts.topics = [{ id: 'topic-exit', title: 'Home', categoryLabel: '生活', questionCount: 4, practiceCount: 0, latestPerformanceScore: null, latestPerformanceSummary: null, latestPracticeType: null }];
    mockIelts.generated = { ieltsId: 'ielts-exit', title: 'Home' };
    mockIelts.prepareSession.mockResolvedValueOnce(mockIelts.generated);
    const screen = await render(<IeltsFlow onExit={onExit} />);
    await fireEvent.press(screen.getByText('下一步'));
    await fireEvent.press(screen.getByText('进入 IELTS 专项'));
    await fireEvent.press(screen.getByText('日常问答'));
    await fireEvent.press(screen.getByLabelText('Home，暂无记录'));
    await fireEvent.press(screen.getByText('确认考官并开始'));
    await waitFor(() => expect(screen.getByLabelText('退出雅思训练')).toBeTruthy());

    await fireEvent.press(screen.getByLabelText('退出雅思训练'));
    const buttons = alert.mock.calls.at(-1)?.[2] as Array<{ onPress?: () => void }>;
    buttons[0].onPress?.();
    buttons[1].onPress?.();
    expect(onExit).toHaveBeenCalledTimes(1);

    mockIeltsSnapshot.ieltsCompletionReady = true;
    screen.rerender(<IeltsFlow onExit={onExit} />);
    await waitFor(() => expect(mockIeltsEnd).toHaveBeenCalled());
    alert.mockRestore();
    screen.unmount();
  });

  it('filters, searches, paginates, and starts a random topic without selecting a row', async () => {
    mockIelts.categories = [{ code: 'LIFE', label: '生活' }];
    mockIelts.topics = [{
      id: 'topic-3', title: 'Travel', categoryLabel: '生活', questionCount: 4, practiceCount: 2,
      latestPerformanceScore: 6.5, latestPerformanceSummary: '回答组织完整，表达自然。', latestPracticeType: 'PART_PRACTICE',
    }];
    mockIelts.topicTotal = 12;
    mockIelts.topicTotalPages = 3;
    const screen = await render(<IeltsFlow onExit={jest.fn()} />);
    await fireEvent.press(screen.getByText('下一步'));
    await fireEvent.press(screen.getByText('进入 IELTS 专项'));
    await fireEvent.press(screen.getByText('日常问答'));
    await waitFor(() => expect(screen.getByLabelText('搜索话题')).toBeTruthy());
    await fireEvent.press(screen.getAllByText('生活')[0]);
    await waitFor(() => expect(mockIelts.loadTopics).toHaveBeenCalledWith('p1', 'LIFE', '', 1), { timeout: 800 });
    await fireEvent.changeText(screen.getByLabelText('搜索话题'), 'travel');
    await fireEvent.press(screen.getByLabelText('清空搜索'));
    await fireEvent.press(screen.getByLabelText('第 3 页'));
    await fireEvent.press(screen.getByLabelText('上一页'));
    await fireEvent.press(screen.getByLabelText('下一页'));
    await fireEvent.press(screen.getByLabelText('随机练习'));
    await waitFor(() => expect(screen.getByText('选择本次考官')).toBeTruthy());
    screen.unmount();
  });

  it('shows the loading topic state', async () => {
    mockIelts.settings = { targetScore: 7 };
    mockIelts.topicsLoading = true;
    const loading = await render(<IeltsFlow onExit={jest.fn()} />);
    await fireEvent.press(loading.getByText('下一步'));
    await waitFor(() => expect(loading.getByText('进入 IELTS 专项')).toBeTruthy());
    await fireEvent.press(loading.getByText('进入 IELTS 专项'));
    await waitFor(() => expect(loading.getByText('快速开始训练')).toBeTruthy());
    await fireEvent.press(loading.getByText('日常问答'));
    expect(loading.getByText('正在读取题库…')).toBeTruthy();
    loading.unmount();

  });

  it('shows the topic error state', async () => {
    mockIelts.topicsError = '题库加载失败';
    const screen = await render(<IeltsFlow onExit={jest.fn()} />);
    await fireEvent.press(screen.getByText('下一步'));
    await waitFor(() => expect(screen.getByText('进入 IELTS 专项')).toBeTruthy());
    await fireEvent.press(screen.getByText('进入 IELTS 专项'));
    await waitFor(() => expect(screen.getByText('快速开始训练')).toBeTruthy());
    await fireEvent.press(screen.getByText('日常问答'));
    expect(screen.getByText('题库加载失败')).toBeTruthy();
    screen.unmount();
  });

  it('shows the empty topic state', async () => {
    const screen = await render(<IeltsFlow onExit={jest.fn()} />);
    await fireEvent.press(screen.getByText('下一步'));
    await waitFor(() => expect(screen.getByText('进入 IELTS 专项')).toBeTruthy());
    await fireEvent.press(screen.getByText('进入 IELTS 专项'));
    await waitFor(() => expect(screen.getByText('快速开始训练')).toBeTruthy());
    await fireEvent.press(screen.getByText('日常问答'));
    expect(screen.getByText('没有找到相关话题')).toBeTruthy();
    expect(screen.getByText('调整分类或搜索关键词后再试。')).toBeTruthy();
    screen.unmount();
  });

  it('selects another examiner and returns from examiner selection', async () => {
    mockIelts.settings = { targetScore: 7 };
    mockIelts.topics = [{ id: 'topic-examiner', title: 'Work', categoryLabel: '生活', questionCount: 4, practiceCount: 0, latestPerformanceScore: null, latestPerformanceSummary: null, latestPracticeType: null }];
    const screen = await render(<IeltsFlow onExit={jest.fn()} />);
    await fireEvent.press(screen.getByText('下一步'));
    await waitFor(() => expect(screen.getByText('进入 IELTS 专项')).toBeTruthy());
    await fireEvent.press(screen.getByText('进入 IELTS 专项'));
    await waitFor(() => expect(screen.getByText('快速开始训练')).toBeTruthy());
    await fireEvent.press(screen.getByText('日常问答'));
    await fireEvent.press(screen.getByLabelText('Work，暂无记录'));
    await waitFor(() => expect(screen.getByText('选择本次考官')).toBeTruthy());
    await fireEvent.press(screen.getAllByRole('radio')[1]);
    expect(screen.getAllByText(/口音/).length).toBeGreaterThan(0);
    await fireEvent.press(screen.getByLabelText('返回话题选择'));
    expect(screen.getByText('选择一个话题，正式开始后才会由考官揭晓具体问题。')).toBeTruthy();
    screen.unmount();
  });

  it('shows a completed Part 1 score and records it when returning home', async () => {
    const addIeltsRecord = jest.fn();
    mockAppModel.addIeltsRecord = addIeltsRecord;
    mockIelts.topics = [{ id: 'topic-4', title: 'Music', categoryLabel: '生活', questionCount: 4, practiceCount: 0, latestPerformanceScore: null, latestPerformanceSummary: null, latestPracticeType: null }];
    mockIelts.topicTotal = 1;
    mockIelts.topicTotalPages = 1;
    mockIelts.generated = { ieltsId: 'ielts-4', title: 'Music' };
    mockIelts.prepareSession.mockResolvedValueOnce(mockIelts.generated);
    mockIelts.finalizeEvaluation.mockImplementationOnce(async () => {
      mockIelts.latestEvaluation = {
        overallBandScore: 7, fluencyCoherenceScore: 7, lexicalResourceScore: 6.5,
        grammaticalRangeAccuracyScore: 6, pronunciationScore: 7.5, summary: '表现稳定',
        strengths: ['表达清晰'], improvements: ['增加细节'], recommendedExpressions: ['From my perspective'],
      };
    });
    mockAppModel.hasCompletedOnboarding = false;
    mockIelts.settings = null;
    const screen = await render(<IeltsFlow onExit={jest.fn()} />);
    await fireEvent.press(screen.getByText('下一步'));
    await waitFor(() => expect(screen.getByText('进入 IELTS 专项')).toBeTruthy());
    await fireEvent.press(screen.getByText('进入 IELTS 专项'));
    await fireEvent.press(screen.getByText('日常问答'));
    await waitFor(() => expect(screen.getByLabelText('Music，暂无记录')).toBeTruthy());
    await fireEvent.press(screen.getByLabelText('Music，暂无记录'));
    await fireEvent.press(screen.getByText('确认考官并开始'));
    await waitFor(() => expect(screen.getByLabelText('结束本题并进入下一题')).toBeTruthy());
    await fireEvent.press(screen.getByLabelText('结束本题并进入下一题'));
    await waitFor(() => expect(screen.getByText('本次专项表现')).toBeTruthy());
    await fireEvent.press(screen.getByText('返回训练中心'));
    expect(addIeltsRecord).toHaveBeenCalledWith(expect.objectContaining({ title: 'Music', estimatedBand: 7, type: 'Part 1' }));
    screen.unmount();
  });

});

describe('InterviewFlow input', () => {
  it('switches between resume text and file input modes', async () => {
    const screen = await render(<InterviewFlow onExit={jest.fn()} />);
    await fireEvent.changeText(screen.getByLabelText('简历文本'), '产品项目经历');
    expect(mockSetResumeText).toHaveBeenCalledWith('产品项目经历');
    await fireEvent.press(screen.getByText('上传文件'));
    screen.rerender(<InterviewFlow onExit={jest.fn()} />);
    await waitFor(() => expect(screen.getByLabelText('上传简历文件')).toBeTruthy());
    await fireEvent.press(screen.getByLabelText('上传简历文件'));
    expect(mockInterviewPickResume).toHaveBeenCalled();
    mockResumeFileName = 'resume.pdf';
    await act(async () => { screen.rerender(<InterviewFlow onExit={jest.fn()} />); });
    expect(screen.getByLabelText('重新选择简历文件')).toBeTruthy();
    await fireEvent.press(screen.getByText('粘贴文本'));
    expect(mockSetResumeMode).toHaveBeenCalledWith('text');
  });

  it('handles Android back navigation for IELTS intake, home, topics, and examiner', async () => {
    const originalOs = Platform.OS;
    Object.defineProperty(Platform, 'OS', { configurable: true, value: 'android' });
    const handlers: Array<(event: any) => boolean | null | undefined> = [];
    const backSpy = jest.spyOn(BackHandler, 'addEventListener').mockImplementation((_, handler) => {
      handlers.push(handler);
      return { remove: jest.fn() } as any;
    });
    const onExit = jest.fn();
    mockIelts.topics = [{ id: 'android-topic', title: 'Work', categoryLabel: '生活', questionCount: 3, practiceCount: 0, latestPerformanceScore: null, latestPerformanceSummary: null, latestPracticeType: null }];
    const screen = await render(<IeltsFlow onExit={onExit} />);
    expect(handlers.at(-1)?.({})).toBe(true);
    expect(onExit).toHaveBeenCalledTimes(1);
    await fireEvent.press(screen.getByText('下一步'));
    expect(handlers.at(-1)?.({})).toBe(true);
    await waitFor(() => expect(screen.getByText('下一步')).toBeTruthy());
    await fireEvent.press(screen.getByText('下一步'));
    await fireEvent.press(screen.getByText('进入 IELTS 专项'));
    await waitFor(() => expect(screen.getByText('快速开始训练')).toBeTruthy());
    expect(handlers.at(-1)?.({})).toBe(true);
    expect(onExit).toHaveBeenCalledTimes(2);
    await fireEvent.press(screen.getByText('日常问答'));
    await waitFor(() => expect(screen.getByLabelText('Work，暂无记录')).toBeTruthy());
    expect(handlers.at(-1)?.({})).toBe(true);
    await waitFor(() => expect(screen.getByText('快速开始训练')).toBeTruthy());
    await fireEvent.press(screen.getByText('日常问答'));
    await fireEvent.press(screen.getByLabelText('Work，暂无记录'));
    await waitFor(() => expect(screen.getByText('选择本次考官')).toBeTruthy());
    expect(handlers.at(-1)?.({})).toBe(true);
    await waitFor(() => expect(screen.getByLabelText('Work，暂无记录')).toBeTruthy());
    screen.unmount();
    backSpy.mockRestore();
    Object.defineProperty(Platform, 'OS', { configurable: true, value: originalOs });
  });

  it('handles Android back navigation across interview input, review, and finalizing', async () => {
    const originalOs = Platform.OS;
    Object.defineProperty(Platform, 'OS', { configurable: true, value: 'android' });
    const handlers: Array<(event: any) => boolean | null | undefined> = [];
    const backSpy = jest.spyOn(BackHandler, 'addEventListener').mockImplementation((_, handler) => {
      handlers.push(handler);
      return { remove: jest.fn() } as any;
    });
    const onExit = jest.fn();
    mockInterviewStart.mockResolvedValueOnce(mockPreparedInterview as any);
    const screen = await render(<InterviewFlow onExit={onExit} />);
    expect(handlers.at(-1)?.({})).toBe(true);
    expect(onExit).toHaveBeenCalled();
    await fireEvent.changeText(screen.getByLabelText('岗位 JD'), '负责产品策略');
    await fireEvent.press(screen.getByText('标准'));
    await fireEvent.press(screen.getByText('整理面试材料'));
    await waitFor(() => expect(screen.getByText('确认面试材料')).toBeTruthy());
    expect(handlers.at(-1)?.({})).toBe(true);
    await waitFor(() => expect(screen.getByText('填写岗位 JD')).toBeTruthy());
    screen.unmount();
    backSpy.mockRestore();
    Object.defineProperty(Platform, 'OS', { configurable: true, value: originalOs });
  });

  it('collects JD and difficulty, exposes preparation failure, and exits through its header', async () => {
    const onExit = jest.fn();
    const screen = await render(<InterviewFlow onExit={onExit} />);
    expect(screen.getByText('材料解析失败')).toBeTruthy();
    await fireEvent.changeText(screen.getByLabelText('岗位 JD'), '负责产品策略');
    await fireEvent.press(screen.getByText('标准'));
    await fireEvent.press(screen.getByText('整理面试材料'));
    await waitFor(() => expect(mockInterviewStart).toHaveBeenCalledWith({ jobDescription: '负责产品策略', difficulty: 'standard' }));
    await fireEvent.press(screen.getByLabelText('退出英文面试'));
    expect(onExit).toHaveBeenCalledTimes(1);
    screen.unmount();
  });

  it('reviews prepared interview material and supports returning to input', async () => {
    mockInterviewStart.mockImplementationOnce(async () => mockPreparedInterview as any);
    const screen = await render(<InterviewFlow onExit={jest.fn()} />);
    await fireEvent.changeText(screen.getByLabelText('岗位 JD'), '负责产品策略');
    await fireEvent.press(screen.getByText('标准'));
    await fireEvent.press(screen.getByText('整理面试材料'));
    await waitFor(() => expect(screen.getByText('确认面试材料')).toBeTruthy());
    expect(screen.getByText(/请补充：任职要求/)).toBeTruthy();
    await fireEvent.changeText(screen.getByLabelText('岗位名称'), '高级产品经理');
    await fireEvent.changeText(screen.getByLabelText('其他岗位信息'), '负责国际业务');
    await fireEvent.changeText(screen.getByLabelText('必备技能'), '用户研究\n数据分析');
    await fireEvent.changeText(screen.getByLabelText('任职要求'), '熟悉用户研究');
    expect(screen.queryByText(/请补充：任职要求/)).toBeNull();
    await fireEvent.press(screen.getByText('返回修改输入'));
    expect(screen.getByText('填写岗位 JD')).toBeTruthy();
    screen.unmount();
  });

  it('confirms reviewed material, runs the interview, and opens the final report', async () => {
    mockInterviewStart.mockResolvedValueOnce(mockPreparedInterview as any);
    mockInterviewConfirm.mockResolvedValueOnce({
      ...mockPreparedInterview,
      scene: { sceneId: 'interview-live', scenePrompt: 'prompt' },
      material: { ...mockPreparedInterview.material, qualificationRequirements: ['熟悉用户研究'] },
    } as any);
    const onExit = jest.fn();
    const screen = await render(<InterviewFlow onExit={onExit} />);
    await fireEvent.changeText(screen.getByLabelText('岗位 JD'), '负责产品策略');
    await fireEvent.press(screen.getByText('标准'));
    await fireEvent.press(screen.getByText('整理面试材料'));
    await waitFor(() => expect(screen.getByText('确认面试材料')).toBeTruthy());
    await fireEvent.changeText(screen.getByLabelText('任职要求'), '熟悉用户研究');
    await fireEvent.press(screen.getByText('确认并生成面试'));
    await waitFor(() => expect(screen.getByText('请介绍自己')).toBeTruthy());
    await fireEvent.press(screen.getByLabelText('结束面试'));
    expect(mockInterviewEnd).toHaveBeenCalled();

    mockInterviewSession.state = 'ended';
    mockInterviewSession.sessionId = 'interview-session-1';
    screen.rerender(<InterviewFlow onExit={onExit} />);
    await waitFor(() => expect(screen.getByText('正在生成面试复盘')).toBeTruthy());
    expect(screen.getByText('报告 interview-session-1')).toBeTruthy();
    await fireEvent.press(screen.getByText('返回面试首页'));
    expect(screen.getByText('填写岗位 JD')).toBeTruthy();
    screen.unmount();
  });

  it('renders interview error, ending, topic and fallback status branches', async () => {
    mockInterviewSession.currentQuestion = '';
    const screen = await render(<InterviewFlow onExit={jest.fn()} practiceScene={{ sceneId: 'existing', jobTitle: '工程师' }} />);
    expect(screen.getByText('正在连接 AI 面试官…')).toBeTruthy();

    mockInterviewSession.state = 'active';
    mockInterviewSession.error = new Error('provider offline');
    await act(async () => screen.rerender(<InterviewFlow onExit={jest.fn()} practiceScene={{ sceneId: 'existing', jobTitle: '工程师' }} />));
    expect(screen.getByLabelText('结束面试')).toBeTruthy();

    mockInterviewSession.error = null;
    mockInterviewSession.state = 'ending';
    await act(async () => screen.rerender(<InterviewFlow onExit={jest.fn()} practiceScene={{ sceneId: 'existing' }} />));
    await waitFor(() => expect(screen.getByText('AI 面试官正在提问…')).toBeTruthy());

    mockInterviewSession.state = 'active';
    mockInterviewSession.interviewState = { currentTopic: null };
    mockInterviewSession.currentQuestion = '';
    await act(async () => screen.rerender(<InterviewFlow onExit={jest.fn()} practiceScene={{ sceneId: 'existing' }} />));
    await waitFor(() => expect(screen.getByText('AI 面试官正在提问…')).toBeTruthy());
    screen.unmount();
  });

  it('keeps the IELTS home available when full mock preparation fails', async () => {
    mockAppModel.hasCompletedOnboarding = false;
    mockIelts.settings = null;
    mockIelts.prepareSession.mockRejectedValueOnce(new Error('模考准备失败'));
    const screen = await render(<IeltsFlow onExit={jest.fn()} />);
    await fireEvent.press(screen.getByText('下一步'));
    await waitFor(() => expect(screen.getByText('进入 IELTS 专项')).toBeTruthy());
    await fireEvent.press(screen.getByText('进入 IELTS 专项'));
    await waitFor(() => expect(screen.getByText('开始模考')).toBeTruthy());
    await fireEvent.press(screen.getByText('开始模考'));
    await waitFor(() => expect(mockIelts.prepareSession).toHaveBeenCalledWith(expect.objectContaining({ part: 'mock', random: true, topicId: null })));
    expect(screen.getByText('完整模拟一场 IELTS 口语考试')).toBeTruthy();
    screen.unmount();
  });

  it('runs all three full-mock parts and saves the aggregate report', async () => {
    jest.useFakeTimers();
    mockIelts.generated = {
      ieltsId: 'ielts-mock', title: 'Full mock',
      content: { part2: [{ question: 'Describe a journey', cue_points: ['where', 'when'], recommended_expressions: [] }] },
    };
    mockIelts.prepareSession.mockResolvedValue(mockIelts.generated);
    mockIelts.finalizeEvaluation.mockImplementation(async () => {
      mockIelts.latestEvaluation = {
        overallBandScore: 7, fluencyCoherenceScore: 7, lexicalResourceScore: 7,
        grammaticalRangeAccuracyScore: 6.5, pronunciationScore: 7.5,
        summary: '稳定完成全真模考', strengths: [], improvements: [], recommendedExpressions: [],
        partEvaluations: [],
      };
    });
    const onViewDetails = jest.fn();
    const screen = await render(<IeltsFlow onExit={jest.fn()} onViewDetails={onViewDetails} />);
    await fireEvent.press(screen.getByText('下一步'));
    await fireEvent.press(screen.getByText('进入 IELTS 专项'));
    await waitFor(() => expect(screen.getByText('开始模考')).toBeTruthy());
    await fireEvent.press(screen.getByText('开始模考'));
    await waitFor(() => expect(screen.getByText(/Part 1/)).toBeTruthy());

    mockIeltsSnapshot.ieltsCompletionReady = true;
    await act(async () => {
      screen.rerender(<IeltsFlow onExit={jest.fn()} onViewDetails={onViewDetails} />);
      await Promise.resolve();
    });
    mockIeltsSnapshot.ieltsCompletionReady = false;
    await waitFor(() => expect(screen.getByText('考官正在说明 Part 2 准备要求')).toBeTruthy());
    expect(mockIelts.scoreCompletedPart).toHaveBeenCalledWith('ielts-mock', 'session-1');

    mockIeltsSnapshot.ieltsStateRestored = true;
    mockIeltsSnapshot.ieltsPart2State = { phase: 'FINISHED' };
    await act(async () => {
      screen.rerender(<IeltsFlow onExit={jest.fn()} onViewDetails={onViewDetails} />);
      await Promise.resolve();
    });
    await waitFor(() => expect(screen.getByText('Part 2 已完成，考官正在结束本部分')).toBeTruthy());
    mockIeltsSnapshot.ieltsPart2CompletionReady = true;
    await act(async () => {
      screen.rerender(<IeltsFlow onExit={jest.fn()} onViewDetails={onViewDetails} />);
      await Promise.resolve();
    });
    await act(async () => {
      await jest.advanceTimersByTimeAsync(1_800);
    });
    mockIeltsSnapshot.ieltsStateRestored = false;
    mockIeltsSnapshot.ieltsPart2CompletionReady = false;
    await waitFor(() => expect(screen.getByText(/Part 3/)).toBeTruthy());

    mockIeltsSnapshot.ieltsCompletionReady = true;
    await act(async () => {
      screen.rerender(<IeltsFlow onExit={jest.fn()} onViewDetails={onViewDetails} />);
      await Promise.resolve();
    });
    await waitFor(() => expect(mockIelts.finalizeEvaluation).toHaveBeenCalledWith('ielts-mock', 'session-1'));
    await waitFor(() => expect(screen.getByText('本次模拟评分')).toBeTruthy());
    await fireEvent.press(screen.getByText('查看详情'));
    expect(onViewDetails).toHaveBeenCalledWith('session-1');
    expect(mockAppModel.addIeltsRecord).toHaveBeenCalledWith(expect.objectContaining({ type: '完整模考', mode: 'MOCK_TEST' }));
    screen.unmount();
  });

  it('enters Part 2, transitions through preparation, and exposes the timeout controls', async () => {
    mockAppModel.hasCompletedOnboarding = false;
    mockIelts.settings = null;
    mockIelts.generated = { ieltsId: 'ielts-part2', title: 'Travel', content: { part2: [{ question: 'Describe a trip', cue_points: ['where', 'when'], recommended_expressions: [] }] } };
    mockIelts.prepareSession.mockResolvedValueOnce(mockIelts.generated);
    const screen = await render(<IeltsFlow onExit={jest.fn()} />);
    await fireEvent.press(screen.getByText('下一步'));
    await waitFor(() => expect(screen.getByText('进入 IELTS 专项')).toBeTruthy());
    await fireEvent.press(screen.getByText('进入 IELTS 专项'));
    await waitFor(() => expect(screen.getByText('长陈述')).toBeTruthy());
    await fireEvent.press(screen.getByText('长陈述'));
    await waitFor(() => expect(screen.getByLabelText('随机练习')).toBeTruthy());
    mockIeltsSnapshot.state = 'assistant_speaking';
    await fireEvent.press(screen.getByLabelText('随机练习'));
    await waitFor(() => expect(screen.getByText('选择本次考官')).toBeTruthy());
    await fireEvent.press(screen.getByText('确认考官并开始'));
    await waitFor(() => expect(screen.getByText('考官正在说明 Part 2 准备要求')).toBeTruthy());
    mockIeltsSnapshot.state = 'ready';
    screen.rerender(<IeltsFlow onExit={jest.fn()} />);
    await waitFor(() => expect(screen.getByText(/准备时间/)).toBeTruthy());
    await fireEvent.press(screen.getByLabelText('提前开始作答'));
    await waitFor(() => expect(screen.getByText('可以开始说了')).toBeTruthy());
    screen.unmount();
  });

  it('runs the Part 2 long-turn completion and delayed finish path', async () => {
    mockIelts.generated = { ieltsId: 'ielts-part2-finish', title: 'Travel', content: { part2: [{ question: 'Describe a trip', cue_points: ['where'], recommended_expressions: [] }] } };
    mockIelts.prepareSession.mockResolvedValueOnce(mockIelts.generated);
    const screen = await render(<IeltsFlow onExit={jest.fn()} />);
    await fireEvent.press(screen.getByText('下一步'));
    await fireEvent.press(screen.getByText('进入 IELTS 专项'));
    await fireEvent.press(screen.getByText('长陈述'));
    mockIeltsSnapshot.state = 'assistant_speaking';
    await fireEvent.press(screen.getByLabelText('随机练习'));
    await fireEvent.press(screen.getByText('确认考官并开始'));
    mockIeltsSnapshot.state = 'ready';
    screen.rerender(<IeltsFlow onExit={jest.fn()} />);
    await waitFor(() => expect(screen.getByLabelText('提前开始作答')).toBeTruthy());
    await fireEvent.changeText(screen.getByPlaceholderText('记录关键词…'), 'trip notes');
    await fireEvent.press(screen.getByLabelText('提前开始作答'));
    await waitFor(() => expect(mockTransitionPart2).toHaveBeenCalledWith('PREPARATION_COMPLETE'));
    await waitFor(() => expect(screen.getByText('可以开始说了')).toBeTruthy());
    mockIeltsSnapshot.ieltsInputReadyTick = 1;
    await act(async () => {
      screen.rerender(<IeltsFlow onExit={jest.fn()} />);
      await Promise.resolve();
    });
    await waitFor(() => expect(screen.getByLabelText('结束 Part 2')).toBeTruthy());
    expect(screen.getByText('trip notes')).toBeTruthy();
    expect(mockToggleMuted).toHaveBeenCalledWith(false);
    await fireEvent.press(screen.getByLabelText('结束 Part 2'));
    await waitFor(() => expect(mockTransitionPart2).toHaveBeenCalledWith('ANSWER_COMPLETE'));
    await waitFor(() => expect(screen.getByText('Part 2 已完成，考官正在结束本部分')).toBeTruthy());
    mockIeltsSnapshot.ieltsPart2CompletionReady = true;
    jest.useFakeTimers();
    await act(async () => {
      screen.rerender(<IeltsFlow onExit={jest.fn()} />);
      await Promise.resolve();
    });
    await act(async () => {
      await jest.advanceTimersByTimeAsync(1_800);
    });
    expect(mockIeltsEnd).toHaveBeenCalled();
    screen.unmount();
  });

  it('restores Part 2 backend phases and exposes transition failures', async () => {
    mockIelts.generated = { ieltsId: 'ielts-part2-restore', title: 'Travel', content: { part2: [{ question: 'Describe a trip', cue_points: ['where'], recommended_expressions: [] }] } };
    mockIelts.prepareSession.mockResolvedValueOnce(mockIelts.generated);
    mockTransitionPart2.mockRejectedValueOnce('offline');
    const screen = await render(<IeltsFlow onExit={jest.fn()} />);
    await fireEvent.press(screen.getByText('下一步'));
    await fireEvent.press(screen.getByText('进入 IELTS 专项'));
    await fireEvent.press(screen.getByText('长陈述'));
    mockIeltsSnapshot.state = 'assistant_speaking';
    await fireEvent.press(screen.getByLabelText('随机练习'));
    await fireEvent.press(screen.getByText('确认考官并开始'));
    mockIeltsSnapshot.state = 'ready';
    screen.rerender(<IeltsFlow onExit={jest.fn()} />);
    await waitFor(() => expect(screen.getByLabelText('提前开始作答')).toBeTruthy());
    await fireEvent.press(screen.getByLabelText('提前开始作答'));
    await waitFor(() => expect(screen.getByText('无法开始 Part 2 作答')).toBeTruthy());

    mockIeltsSnapshot.ieltsStateRestored = true;
    mockIeltsSnapshot.ieltsPart2State = { phase: 'LONG_TURN' };
    screen.rerender(<IeltsFlow onExit={jest.fn()} />);
    await waitFor(() => expect(screen.getByLabelText('结束 Part 2')).toBeTruthy());
    mockIeltsSnapshot.ieltsPart2State = { phase: 'FINISHED' };
    screen.rerender(<IeltsFlow onExit={jest.fn()} />);
    await waitFor(() => expect(screen.getByText('Part 2 已完成，考官正在结束本部分')).toBeTruthy());
    screen.unmount();
  });

  it('runs the Part 3 answer timer, handles timeout failure, and auto-finishes on completion', async () => {
    mockIelts.generated = { ieltsId: 'ielts-part3', title: 'Society' };
    mockIelts.prepareSession.mockResolvedValueOnce(mockIelts.generated);
    mockForcePart3Timeout.mockRejectedValueOnce(new Error('timeout unavailable'));
    const screen = await render(<IeltsFlow onExit={jest.fn()} />);
    await fireEvent.press(screen.getByText('下一步'));
    await fireEvent.press(screen.getByText('进入 IELTS 专项'));
    await waitFor(() => expect(screen.getByText('深入讨论')).toBeTruthy());
    await fireEvent.press(screen.getByText('深入讨论'));
    await fireEvent.press(screen.getByLabelText('随机练习'));
    await fireEvent.press(screen.getByText('确认考官并开始'));
    await waitFor(() => expect(screen.getByText(/Part 3/)).toBeTruthy());

    jest.useFakeTimers();
    mockIeltsSnapshot.ieltsInputReadyTick = 1;
    await act(async () => {
      screen.rerender(<IeltsFlow onExit={jest.fn()} />);
      await Promise.resolve();
    });
    await act(async () => {
      await jest.advanceTimersByTimeAsync(60_000);
    });
    expect(mockForcePart3Timeout).toHaveBeenCalledTimes(1);

    mockIeltsSnapshot.ieltsInputReadyTick = 2;
    await act(async () => {
      screen.rerender(<IeltsFlow onExit={jest.fn()} />);
      await Promise.resolve();
    });
    mockIeltsSnapshot.ieltsCompletionReady = true;
    await act(async () => {
      screen.rerender(<IeltsFlow onExit={jest.fn()} />);
      await Promise.resolve();
    });
    await waitFor(() => expect(mockIeltsEnd).toHaveBeenCalled());
    screen.unmount();
  });

  it('finishes Part 2 at the preparation and long-turn limits', async () => {
    mockIelts.generated = { ieltsId: 'ielts-part2-timers', title: 'Travel', content: { part2: [{ question: 'Describe a trip', cue_points: [], recommended_expressions: [] }] } };
    mockIelts.prepareSession.mockResolvedValueOnce(mockIelts.generated);
    const screen = await render(<IeltsFlow onExit={jest.fn()} />);
    await fireEvent.press(screen.getByText('下一步'));
    await fireEvent.press(screen.getByText('进入 IELTS 专项'));
    await fireEvent.press(screen.getByText('长陈述'));
    mockIeltsSnapshot.state = 'assistant_speaking';
    await fireEvent.press(screen.getByLabelText('随机练习'));
    await fireEvent.press(screen.getByText('确认考官并开始'));
    jest.useFakeTimers();
    mockIeltsSnapshot.state = 'ready';
    await act(async () => {
      screen.rerender(<IeltsFlow onExit={jest.fn()} />);
      await Promise.resolve();
    });
    await act(async () => {
      await jest.advanceTimersByTimeAsync(60_000);
    });
    expect(mockTransitionPart2).toHaveBeenCalledWith('PREPARATION_COMPLETE');
    mockIeltsSnapshot.ieltsInputReadyTick = 1;
    await act(async () => {
      screen.rerender(<IeltsFlow onExit={jest.fn()} />);
      await Promise.resolve();
    });
    await act(async () => {
      await jest.advanceTimersByTimeAsync(120_000);
    });
    expect(mockTransitionPart2).toHaveBeenCalledWith('LONG_TURN_TIME_LIMIT');
    screen.unmount();
  });
});
