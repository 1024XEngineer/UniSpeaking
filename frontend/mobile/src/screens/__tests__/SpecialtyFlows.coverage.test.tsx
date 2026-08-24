import { fireEvent, render, waitFor } from '@testing-library/react-native';

const mockIelts = {
  settingsLoading: false, settings: null, categories: [] as any[], topics: [] as any[], topicsLoading: false, topicsError: null,
  topicTotal: 0, topicTotalPages: 0, generated: null as any, latestEvaluation: null as any, training: null as any, sessionBusy: false, sessionError: null,
  loadTopics: jest.fn(), refreshSettings: jest.fn(), finalizeEvaluation: jest.fn(), saveTargetScore: jest.fn(async () => undefined),
  prepareSession: jest.fn(), formatBand: (value: number) => String(value), practiceTypeLabel: () => '暂无记录',
};
const mockSaveLevel = jest.fn(async () => undefined);
const mockInterviewStart = jest.fn(async () => undefined);
const mockAppModel: any = { addIeltsRecord: jest.fn(), hasCompletedOnboarding: false, level: 'starter', saveLevel: mockSaveLevel, signOut: jest.fn() };
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
    statusLabel: '可以开始说了', sessionId: 'session-1', end: jest.fn(async () => undefined),
    waitForTurnEvaluations: jest.fn(async () => undefined), forcePart3Timeout: jest.fn(async () => undefined),
  }),
}));
jest.mock('@/features/interview/useInterviewPreparation', () => ({
  useInterviewPreparation: () => ({ resumeFileName: null, resumeText: '', resumeMode: 'text', setResumeText: jest.fn(), setResumeMode: jest.fn(), isPreparing: false, error: '材料解析失败', pickResume: jest.fn(), start: mockInterviewStart, confirm: jest.fn() }),
}));
jest.mock('@/model/AppModel', () => ({ useAppModel: () => mockAppModel }));
jest.mock('@/navigation/learningStage', () => ({ useLearningStage: () => ({ setImmersiveLearning: jest.fn() }) }));
jest.mock('@/navigation/specialtyMemory', () => ({ rememberSpecialty: jest.fn() }));
jest.mock('react-native-safe-area-context', () => { const { View } = require('react-native'); return { SafeAreaView: View }; });

import { IeltsFlow, InterviewFlow } from '../SpecialtyFlows';

describe('IeltsFlow intake', () => {
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
    mockAppModel.addIeltsRecord = jest.fn();
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
    const screen = await render(<IeltsFlow onExit={jest.fn()} />);
    await fireEvent.press(screen.getByText('下一步'));
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
    await fireEvent.changeText(screen.getByLabelText('任职要求'), '熟悉用户研究');
    expect(screen.queryByText(/请补充：任职要求/)).toBeNull();
    await fireEvent.press(screen.getByText('返回修改输入'));
    expect(screen.getByText('填写岗位 JD')).toBeTruthy();
    screen.unmount();
  });

  it('keeps the IELTS home available when full mock preparation fails', async () => {
    mockIelts.prepareSession.mockRejectedValueOnce(new Error('模考准备失败'));
    const screen = await render(<IeltsFlow onExit={jest.fn()} />);
    await fireEvent.press(screen.getByText('下一步'));
    await fireEvent.press(screen.getByText('进入 IELTS 专项'));
    await waitFor(() => expect(screen.getByText('开始模考')).toBeTruthy());
    await fireEvent.press(screen.getByText('开始模考'));
    await waitFor(() => expect(mockIelts.prepareSession).toHaveBeenCalledWith(expect.objectContaining({ part: 'mock', random: true, topicId: null })));
    expect(screen.getByText('完整模拟一场 IELTS 口语考试')).toBeTruthy();
    screen.unmount();
  });
});
