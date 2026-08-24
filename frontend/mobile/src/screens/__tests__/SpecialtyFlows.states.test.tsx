import { fireEvent, render, waitFor } from '@testing-library/react-native';

const mockIelts: any = {
  settingsLoading: false, settings: null, categories: [], topics: [], topicsLoading: true, topicsError: null,
  topicTotal: 0, topicTotalPages: 1, generated: null, sessionBusy: false, sessionError: null,
  loadTopics: jest.fn(), refreshSettings: jest.fn(), finalizeEvaluation: jest.fn(), saveTargetScore: jest.fn(async () => undefined),
  prepareSession: jest.fn(), formatBand: (value: number) => String(value), practiceTypeLabel: () => '暂无记录',
};
jest.mock('react-native-reanimated', () => { const { View } = require('react-native'); return { __esModule: true, default: { View }, Easing: { cubic: jest.fn(), ease: jest.fn(), linear: jest.fn(), out: (v: unknown) => v }, interpolate: (_: unknown, _i: number[], o: number[]) => o[0], useAnimatedStyle: (f: () => unknown) => f(), useSharedValue: (v: unknown) => ({ value: v }), withTiming: (_: unknown, v: unknown) => v, runOnJS: (f: unknown) => f }; });
jest.mock('@/features/ielts/useIeltsFlowController', () => ({ useIeltsFlowController: () => mockIelts }));
jest.mock('@/features/ielts/useIeltsSession', () => ({ useIeltsSession: () => ({ snapshot: { state: 'ready', error: null, userTranscript: '', assistantTranscript: '', transcriptHistory: [] }, statusLabel: '可以开始说了', sessionId: 's', end: jest.fn(async () => undefined), waitForTurnEvaluations: jest.fn(async () => undefined), forcePart3Timeout: jest.fn(async () => undefined) }) }));
jest.mock('@/features/interview/useInterviewPreparation', () => ({ useInterviewPreparation: () => ({ resumeFileName: null, resumeText: '', resumeMode: 'text', setResumeText: jest.fn(), setResumeMode: jest.fn(), isPreparing: false, error: null, pickResume: jest.fn(), start: jest.fn(), confirm: jest.fn() }) }));
jest.mock('@/features/interview/useInterviewSession', () => ({ createInterviewApi: jest.fn(() => ({})), useInterviewSession: () => ({ state: 'starting', sessionId: null, elapsed: 2, userMuted: false, error: null, interviewState: { currentTopic: '自我介绍' }, currentQuestion: '请介绍自己', end: jest.fn(async () => undefined), setMuted: jest.fn() }) }));
jest.mock('@/model/AppModel', () => ({ useAppModel: () => ({ addIeltsRecord: jest.fn(), hasCompletedOnboarding: false, level: 'starter', saveLevel: jest.fn(), teacher: { voiceId: 'Harvey', name: 'Sophia', image: 1 } }) }));
jest.mock('@/navigation/learningStage', () => ({ useLearningStage: () => ({ setImmersiveLearning: jest.fn() }) }));
jest.mock('@/navigation/specialtyMemory', () => ({ rememberSpecialty: jest.fn() }));
jest.mock('react-native-safe-area-context', () => { const { View } = require('react-native'); return { SafeAreaView: View }; });

import { IeltsFlow, InterviewFlow } from '../SpecialtyFlows';

describe('IELTS topic state presentation', () => {
  it('renders loading and empty/error topic states after entering a part', async () => {
    const loading = await render(<IeltsFlow onExit={jest.fn()} />);
    await fireEvent.press(loading.getByText('下一步'));
    await fireEvent.press(loading.getByText('可以简单交流'));
    await fireEvent.press(loading.getByText('进入 IELTS 专项'));
    await fireEvent.press(loading.getByText('日常问答'));
    await waitFor(() => expect(loading.getByText('正在读取题库…')).toBeTruthy());
    loading.unmount();
  });

});
