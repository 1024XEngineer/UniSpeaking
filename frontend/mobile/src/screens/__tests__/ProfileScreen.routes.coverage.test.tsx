import { cleanup, fireEvent, render, waitFor } from '@testing-library/react-native';

const mockApi = {
  getOverview: jest.fn(async () => ({
    account: { email: 'ada@example.com', nickname: 'Ada', displayName: 'Ada', avatarUrl: null },
    statistics: { weeklyPracticeSeconds: 0, trainingRecordCount: 0, consecutiveLearningDays: 0, lastSevenDays: [] },
    calendar: { month: '2026-08', checkedDates: [], checkedInToday: false },
  })),
  getAchievements: jest.fn(async () => ({ series: [] })),
  getInsights: jest.fn(async () => ({
    weeklyGoals: { weekStartsAt: '2026-08-24T00:00:00Z', weekEndsAt: '2026-08-31T00:00:00Z', durationTargetMinutes: 60, completedDurationSeconds: 0, remainingDurationSeconds: 3600, durationProgress: 0, durationAchieved: false, trainingCountTarget: 3, completedTrainingCount: 0, remainingTrainingCount: 3, countProgress: 0, countAchieved: false },
    trainingTypeDistribution: [], abilityTrends: [], weaknessAnalysis: { sampleCount: 0, minimumSampleCount: 3, reliable: false }, weaknesses: [], recommendations: [],
  })),
  getHelpCenter: jest.fn(async () => ({ categories: [] })),
  updateNickname: jest.fn(), uploadAvatar: jest.fn(), changePassword: jest.fn(), updateWeeklyGoals: jest.fn(),
};
const mockAppModel = {
  nickname: 'Ada', email: 'ada@example.com', speed: '自然', level: 'starter',
  teacher: { id: 'clara', name: 'Clara', image: 1 }, setNickname: jest.fn(),
  saveSpeed: jest.fn(), saveLevel: jest.fn(), saveTeacher: jest.fn(), signOut: jest.fn(),
};

jest.mock('@/features/profile/ProfileApi', () => ({ ProfileApi: jest.fn(() => mockApi) }));
jest.mock('@/model/AppModel', () => ({ useAppModel: () => mockAppModel }));
jest.mock('@/features/audio/useTeacherPreview', () => ({ useTeacherPreview: () => ({ playTeacher: jest.fn() }) }));

import { ProfileScreen } from '../ProfileScreen';

afterEach(cleanup);

it.each([
  ['个人概览', '本周学习时长'],
  ['学习目标与洞察', '本周训练类型占比'],
  ['会员权益', '免费版'],
  ['助手设置', 'AI 助手设置'],
  ['账号与安全', '登录信息'],
  ['帮助中心', 'HELP CENTER'],
  ['关于产品', '关于 UniSpeaking'],
])('routes ProfileScreen from %s and back', async (menu, destination) => {
  const view = await render(<ProfileScreen />);
  await waitFor(() => expect(view.getByText(menu)).toBeTruthy());
  fireEvent.press(view.getByText(menu));
  await waitFor(() => expect(view.getAllByText(destination).length).toBeGreaterThan(0));
  fireEvent.press(view.getByRole('button', { name: '返回' }));
  await waitFor(() => expect(view.getByText('个人概览')).toBeTruthy());
});
