import { Alert } from 'react-native';
import { cleanup, fireEvent, render, waitFor } from '@testing-library/react-native';

import {
  AccountSettings,
  AboutProduct,
  AssistantSettings,
  HelpArticle,
  HelpCategory,
  HelpCenter,
  Insights,
  Membership,
  Overview,
  ProfileHome,
} from '../ProfileScreen';

const mockApi = {
  getOverview: jest.fn(),
  getAchievements: jest.fn(),
  getInsights: jest.fn(),
  updateWeeklyGoals: jest.fn(),
  updateNickname: jest.fn(),
  uploadAvatar: jest.fn(),
  changePassword: jest.fn(),
  getHelpCenter: jest.fn(),
  getHelpCategory: jest.fn(),
  getHelpArticle: jest.fn(),
};

const mockAppModel = {
  nickname: 'Ada',
  email: 'ada@example.com',
  speed: '自然',
  level: 'starter',
  teacher: { id: 'clara', name: 'Clara' },
  setNickname: jest.fn(),
  saveSpeed: jest.fn(),
  saveLevel: jest.fn(),
  saveTeacher: jest.fn(),
  signOut: jest.fn(),
};
const mockImagePicker = {
  requestMediaLibraryPermissionsAsync: jest.fn(),
  launchImageLibraryAsync: jest.fn(),
};

jest.mock('@/features/profile/ProfileApi', () => ({
  ProfileApi: jest.fn(() => mockApi),
}));
jest.mock('@/model/AppModel', () => ({ useAppModel: () => mockAppModel }));
jest.mock('@/features/audio/useTeacherPreview', () => ({
  useTeacherPreview: () => ({ playTeacher: jest.fn() }),
}));
jest.mock('expo-image-picker', () => ({ __esModule: true, ...mockImagePicker }));

const overview = {
  account: { userId: 'u1', email: 'ada@example.com', nickname: 'Ada', displayName: 'Ada', avatarUrl: null, avatarUrlExpiresAt: null },
  statistics: {
    weeklyPracticeSeconds: 3900,
    trainingRecordCount: 8,
    consecutiveLearningDays: 4,
    lastSevenDays: [{ date: '2026-08-10', practiceSeconds: 600 }, { date: '2026-08-11', practiceSeconds: 0 }],
  },
  calendar: { month: '2026-08', checkedDates: ['2026-08-10'], checkedInToday: false },
};
const insights = {
  weeklyGoals: {
    weekStartsAt: '2026-08-10T00:00:00.000Z', weekEndsAt: '2026-08-17T00:00:00.000Z',
    durationTargetMinutes: 120, completedDurationSeconds: 3600, remainingDurationSeconds: 3600,
    durationProgress: 50, durationAchieved: false, trainingCountTarget: 5, completedTrainingCount: 2,
    remainingTrainingCount: 3, countProgress: 40, countAchieved: false,
  },
  trainingTypeDistribution: [{ type: 'FREE_CHAT', durationSeconds: 3600, percentage: 100 }],
  abilityTrends: [{ sessionId: 's1', completedAt: '2026-08-12', trainingType: 'FREE_CHAT', scores: { accuracy: 7.5, fluency: 6.5, grammar: null, vocabulary: null, naturalness: null } }],
  weaknessAnalysis: { sampleCount: 3, minimumSampleCount: 2, reliable: true },
  weaknesses: [{ dimension: 'fluency', rank: 1, averageScore: 6.5, recentChange: 0.2, basis: '停顿偏多' }],
  recommendations: [{ dimension: 'fluency', trainingType: 'FREE_CHAT', reason: '每天练习' }],
};

beforeEach(() => {
  jest.clearAllMocks();
  mockApi.getOverview.mockResolvedValue(overview);
  mockApi.getAchievements.mockResolvedValue({ series: [{ seriesId: 'a1', category: '练习', title: '开口新星', unit: '次', currentValue: 2, currentLevel: 1, currentTitle: '初阶', nextLevel: 2, nextTitle: '进阶', nextThreshold: 5, completed: false, milestones: [{ achievementId: 'a1', level: 1, title: '初阶', description: '完成两次', threshold: 2, unlocked: true, unlockedAt: '2026-08-10' }] }] });
  mockApi.getInsights.mockResolvedValue(insights);
  mockApi.updateWeeklyGoals.mockResolvedValue(insights);
  mockApi.updateNickname.mockResolvedValue({ nickname: 'Grace', displayName: 'Grace' });
  mockApi.uploadAvatar.mockResolvedValue({ avatarUrl: 'https://example.com/avatar.png', avatarUrlExpiresAt: '2026-08-30' });
  mockApi.changePassword.mockResolvedValue({ reauthenticationRequired: true });
  mockApi.getHelpCenter.mockResolvedValue({ categories: [{ id: 'account', title: '账号问题', description: '登录与安全', articleCount: 1 }] });
  mockApi.getHelpCategory.mockResolvedValue({ id: 'account', title: '账号问题', description: '登录与安全', articles: [{ id: 'password', title: '如何修改密码', summary: '在账号设置中修改。' }] });
  mockApi.getHelpArticle.mockResolvedValue({ id: 'password', categoryId: 'account', title: '如何修改密码', summary: '在账号设置中修改。', updatedAt: '2026-08-10' });
  mockAppModel.saveSpeed.mockResolvedValue(undefined);
  mockAppModel.saveLevel.mockResolvedValue(undefined);
  mockAppModel.saveTeacher.mockResolvedValue(undefined);
  mockImagePicker.requestMediaLibraryPermissionsAsync.mockResolvedValue({ granted: true });
  mockImagePicker.launchImageLibraryAsync.mockResolvedValue({ canceled: false, assets: [{ uri: 'file:///avatar.png', mimeType: 'image/png', fileName: 'avatar.png', fileSize: 512 }] });
  jest.spyOn(Alert, 'alert').mockImplementation(jest.fn());
});

afterEach(() => {
  cleanup();
  jest.restoreAllMocks();
});

describe('ProfileScreen exported pages', () => {
  it('renders the about product page and returns to profile', async () => {
    const onBack = jest.fn();
    const view = await render(<AboutProduct onBack={onBack} />);
    expect(view.getAllByText('关于 UniSpeaking').length).toBeGreaterThan(0);
    await fireEvent.press(view.getByRole('button', { name: '返回' }));
    expect(onBack).toHaveBeenCalledTimes(1);
    view.unmount();
  });

  it('loads the profile home, routes menu items, and opens the edit modal', async () => {
    const onOpen = jest.fn();
    const onLogout = jest.fn();
    const view = await render(<ProfileHome onOpen={onOpen} onLogout={onLogout} />);
    await waitFor(() => expect(view.getByText('Ada')).toBeTruthy());
    await fireEvent.press(view.getByText('学习目标与洞察'));
    expect(onOpen).toHaveBeenCalledWith('insights');
    await fireEvent.press(view.getByLabelText('编辑用户名和头像'));
    expect(view.getByText('编辑个人资料')).toBeTruthy();
    const nicknameInput = view.getByDisplayValue('Ada');
    await fireEvent.changeText(nicknameInput, 'Grace');
    await fireEvent.press(view.getByText('保存修改'));
    await waitFor(() => expect(mockApi.updateNickname).toHaveBeenCalledWith('Grace'));
    await fireEvent.press(view.getByLabelText('编辑用户名和头像'));
    await fireEvent.changeText(view.getByDisplayValue('Ada'), '');
    await fireEvent.press(view.getByText('保存修改'));
    expect(view.getByText('用户名需为 1 到 32 个字符')).toBeTruthy();
    await fireEvent.press(view.getByText('取消'));
    await fireEvent.press(view.getByText('退出登录'));
    expect(onLogout).toHaveBeenCalledTimes(1);
    view.unmount();
  });

  it('keeps the profile form usable when the current client cannot load the photo picker', async () => {
    const view = await render(<ProfileHome onOpen={jest.fn()} onLogout={jest.fn()} />);
    await waitFor(() => expect(view.getByText('Ada')).toBeTruthy());
    await fireEvent.press(view.getByLabelText('编辑用户名和头像'));

    await fireEvent.press(view.getByText('选择新头像'));
    await waitFor(() => expect(view.getByText('当前客户端不支持选择照片，请更新 Expo Go 或使用最新开发构建')).toBeTruthy());
    expect(view.getByText('保存修改')).toBeTruthy();
    view.unmount();
  });


  it('loads overview data, changes the calendar month, and exposes achievement retry failures', async () => {
    const view = await render(<Overview onBack={jest.fn()} />);
    await waitFor(() => expect(view.getByText('本周学习时长')).toBeTruthy());
    expect(view.getByText('开口新星')).toBeTruthy();
    fireEvent.press(view.getByLabelText('上个月'));
    await waitFor(() => expect(mockApi.getOverview).toHaveBeenCalledTimes(2));

    mockApi.getAchievements.mockRejectedValueOnce(new Error('成就服务暂不可用'));
    const failed = await render(<Overview onBack={jest.fn()} />);
    await waitFor(() => expect(failed.getByText('成就服务暂不可用')).toBeTruthy());
    fireEvent.press(failed.getByText('重新加载'));
    await waitFor(() => expect(mockApi.getAchievements).toHaveBeenCalledTimes(3));
  });

  it('shows and retries the overview request failure while retaining the achievement area', async () => {
    mockApi.getOverview.mockReset();
    mockApi.getOverview.mockRejectedValueOnce(new Error('概览暂不可用')).mockResolvedValue(overview);
    const view = await render(<Overview onBack={jest.fn()} />);
    await waitFor(() => expect(view.getByText('概览暂不可用')).toBeTruthy());
    fireEvent.press(view.getByText('重新加载'));
    await waitFor(() => expect(view.getByText('本周学习时长')).toBeTruthy());
    view.unmount();
  });

  it('renders insights and persists valid weekly goals while validating invalid values', async () => {
    const view = await render(<Insights onBack={jest.fn()} />);
    await waitFor(() => expect(view.getByText('本周训练类型占比')).toBeTruthy());
    fireEvent.press(view.getByText('调整目标'));
    await waitFor(() => expect(view.getByText('调整每周目标')).toBeTruthy());
    fireEvent.press(view.getByText('保存目标'));
    await waitFor(() => expect(mockApi.updateWeeklyGoals).toHaveBeenCalledWith({ durationTargetMinutes: 120, trainingCountTarget: 5 }));
  });

  it('renders insights empty states and retries a failed request', async () => {
    mockApi.getInsights.mockReset();
    mockApi.getInsights.mockRejectedValueOnce(new Error('洞察暂不可用')).mockResolvedValue({
      ...insights,
      trainingTypeDistribution: [], abilityTrends: [],
      weaknessAnalysis: { sampleCount: 1, minimumSampleCount: 3, reliable: false }, weaknesses: [], recommendations: [],
    });
    const view = await render(<Insights onBack={jest.fn()} />);
    await waitFor(() => expect(view.getByText('洞察暂不可用')).toBeTruthy());
    fireEvent.press(view.getByText('重新加载'));
    await waitFor(() => expect(view.getByText('本周暂无有效训练记录')).toBeTruthy());
    expect(view.getByText('完成训练后将显示能力趋势')).toBeTruthy();
    expect(view.getByText(/至少需要/)).toBeTruthy();
    view.unmount();
  });

  it('shows membership plans and synchronizes assistant setting success and failure paths', async () => {
    const membership = await render(<Membership onBack={jest.fn()} />);
    expect(membership.getByText('免费版')).toBeTruthy();
    expect(membership.getByText('特训版')).toBeTruthy();
    expect(membership.getAllByText('暂未开放')).toHaveLength(2);

    const settings = await render(<AssistantSettings onBack={jest.fn()} />);
    fireEvent.press(settings.getByText('慢一些'));
    await waitFor(() => expect(mockAppModel.saveSpeed).toHaveBeenCalledWith('慢一些'));
    mockAppModel.saveLevel.mockRejectedValueOnce(new Error('网络中断'));
    fireEvent.press(settings.getByText('可以简单交流'));
    await waitFor(() => expect(Alert.alert).toHaveBeenCalledWith('设置保存失败', '网络中断'));
  });

  it('updates account nickname, reports password validation, and signs out after a password change', async () => {
    const logout = jest.fn().mockResolvedValue(undefined);
    const view = await render(<AccountSettings onBack={jest.fn()} onLogout={logout} />);
    fireEvent.press(view.getByText('展示用户名'));
    await waitFor(() => expect(view.getByText('修改用户名')).toBeTruthy());
    fireEvent.press(view.getByText('保存用户名'));
    await waitFor(() => expect(mockApi.updateNickname).toHaveBeenCalledWith('Ada'));
    expect(mockAppModel.setNickname).toHaveBeenCalledWith('Grace');

    fireEvent.press(view.getByText('登录密码'));
    await waitFor(() => expect(view.getByText('修改密码')).toBeTruthy());
    fireEvent.press(view.getByText('确认修改'));
    await waitFor(() => expect(view.getByText('密码长度需为 6 到 72 位')).toBeTruthy());
    fireEvent.press(view.getByText('退出当前账号'));
    expect(logout).toHaveBeenCalled();
  });

  it('loads an article and renders its refreshed timestamp and body', async () => {
    mockApi.getHelpArticle.mockReset();
    mockApi.getHelpArticle.mockResolvedValue({ id: 'password', categoryId: 'account', title: '如何修改密码', summary: '在账号设置中修改。', updatedAt: '2026-08-10' });
    const article = await render(<HelpArticle articleId="password" onBack={jest.fn()} />);
    await waitFor(() => expect(mockApi.getHelpArticle).toHaveBeenCalledWith('password'));
    await waitFor(() => expect(article.getByText('如何修改密码')).toBeTruthy());
    expect(article.getByText(/更新时间：2026-08-10/)).toBeTruthy();
    expect(article.getByText('在账号设置中修改。')).toBeTruthy();
  });

  it('loads help center and category content and opens navigation targets', async () => {
    const openCategory = jest.fn();
    const center = await render(<HelpCenter onBack={jest.fn()} onOpenCategory={openCategory} />);
    await waitFor(() => expect(center.getByText('账号问题')).toBeTruthy());
    fireEvent.press(center.getByLabelText('打开账号问题'));
    expect(openCategory).toHaveBeenCalledWith('account');

    const openArticle = jest.fn();
    const category = await render(<HelpCategory categoryId="account" onBack={jest.fn()} onOpenArticle={openArticle} />);
    await waitFor(() => expect(category.getByText('如何修改密码')).toBeTruthy());
    fireEvent.press(category.getByLabelText('打开如何修改密码'));
    expect(openArticle).toHaveBeenCalledWith('password');

  });


});
