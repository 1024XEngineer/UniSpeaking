import { fireEvent, render, waitFor } from '@testing-library/react-native';

jest.mock('react-native-reanimated', () => { const { View } = require('react-native'); return { __esModule: true, default: { View }, Easing: { cubic: jest.fn(), ease: jest.fn(), linear: jest.fn(), out: (v: unknown) => v }, interpolate: (_: unknown, _i: number[], o: number[]) => o[0], useAnimatedStyle: (f: () => unknown) => f(), useSharedValue: (v: unknown) => ({ value: v }), withTiming: (_: unknown, v: unknown) => v, runOnJS: (f: unknown) => f }; });
const mockController: any = {
  settingsLoading: false, settings: null, categories: [], topics: [], topicsLoading: false, topicsError: '题库服务不可用', topicTotal: 0, topicTotalPages: 1, generated: null, sessionBusy: false, sessionError: null,
  loadTopics: jest.fn(), refreshSettings: jest.fn(), finalizeEvaluation: jest.fn(), saveTargetScore: jest.fn(async () => undefined), prepareSession: jest.fn(), formatBand: (v: number) => String(v), practiceTypeLabel: () => '暂无记录',
};
jest.mock('@/features/ielts/useIeltsFlowController', () => ({ useIeltsFlowController: () => mockController }));
jest.mock('@/features/ielts/useIeltsSession', () => ({ useIeltsSession: () => ({ snapshot: { state: 'ready', error: null, userTranscript: '', assistantTranscript: '', transcriptHistory: [] }, statusLabel: 'ready', sessionId: 's', end: jest.fn(), waitForTurnEvaluations: jest.fn(), forcePart3Timeout: jest.fn() }) }));
jest.mock('@/model/AppModel', () => ({ useAppModel: () => ({ level: 'starter', hasCompletedOnboarding: false, saveLevel: jest.fn(), addIeltsRecord: jest.fn() }) }));
jest.mock('@/navigation/learningStage', () => ({ useLearningStage: () => ({ setImmersiveLearning: jest.fn() }) }));
jest.mock('@/navigation/specialtyMemory', () => ({ rememberSpecialty: jest.fn() }));
jest.mock('@/features/interview/useInterviewPreparation', () => ({ useInterviewPreparation: () => ({ resumeFileName: null, resumeText: '', resumeMode: 'text', setResumeText: jest.fn(), setResumeMode: jest.fn(), isPreparing: false, error: null, pickResume: jest.fn(), start: jest.fn(), confirm: jest.fn() }) }));
jest.mock('react-native-safe-area-context', () => { const { View } = require('react-native'); return { SafeAreaView: View }; });

import { IeltsFlow } from '../SpecialtyFlows';

it('keeps the topic chooser visible and reports backend topic errors', async () => {
  const view = await render(<IeltsFlow onExit={jest.fn()} />);
  await fireEvent.press(view.getByText('下一步'));
  await fireEvent.press(view.getByText('可以简单交流'));
  await fireEvent.press(view.getByText('进入 IELTS 专项'));
  await fireEvent.press(view.getByText('日常问答'));
  await waitFor(() => expect(view.getByText('题库服务不可用')).toBeTruthy());
  view.unmount();
});
