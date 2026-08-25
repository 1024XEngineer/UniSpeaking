import { cleanup, fireEvent, render, waitFor } from '@testing-library/react-native';

const mockApi = {
  getHelpCenter: jest.fn(),
  getHelpCategory: jest.fn(),
  getHelpArticle: jest.fn(),
  getOverview: jest.fn(),
  getAchievements: jest.fn(),
  getInsights: jest.fn(),
  updateWeeklyGoals: jest.fn(),
  updateNickname: jest.fn(),
  uploadAvatar: jest.fn(),
  changePassword: jest.fn(),
};
const mockAppModel = {
  nickname: 'Ada', email: 'ada@example.com', teacher: { id: 'clara', image: 1 },
  speed: '自然', level: 'starter', setNickname: jest.fn(), saveSpeed: jest.fn(),
  saveLevel: jest.fn(), saveTeacher: jest.fn(), signOut: jest.fn(),
};

jest.mock('@/features/profile/ProfileApi', () => ({ ProfileApi: jest.fn(() => mockApi) }));
jest.mock('@/model/AppModel', () => ({ useAppModel: () => mockAppModel }));

import { HelpArticle, HelpCategory, HelpCenter, ProfileHome } from '../ProfileScreen';

afterEach(() => cleanup());

describe('profile help and account failure coverage', () => {
  it('retries help center loading after a non-Error failure', async () => {
    mockApi.getHelpCenter.mockRejectedValue('offline');
    const view = await render(<HelpCenter onBack={jest.fn()} onOpenCategory={jest.fn()} />);
    await waitFor(() => expect(view.getByText('帮助内容加载失败')).toBeTruthy());
    mockApi.getHelpCenter.mockResolvedValue({ categories: [{ id: 'account', title: '账号问题', description: '登录与安全', articleCount: 1 }] });
    fireEvent.press(view.getByText('重新加载'));
    await waitFor(() => expect(view.getByText('账号问题')).toBeTruthy());
  });

  it('retries help category loading after an Error failure', async () => {
    mockApi.getHelpCategory.mockRejectedValue(new Error('分类暂不可用'));
    const view = await render(<HelpCategory categoryId="account" onBack={jest.fn()} onOpenArticle={jest.fn()} />);
    await waitFor(() => expect(view.getByText('分类暂不可用')).toBeTruthy());
    mockApi.getHelpCategory.mockResolvedValue({ id: 'account', title: '账号问题', description: '登录与安全', articles: [] });
    fireEvent.press(view.getByText('重新加载'));
    await waitFor(() => expect(view.getAllByText('账号问题').length).toBeGreaterThan(0));
  });

  it('retries help article loading after a non-Error failure', async () => {
    mockApi.getHelpArticle.mockRejectedValue('offline');
    const view = await render(<HelpArticle articleId="password" onBack={jest.fn()} />);
    await waitFor(() => expect(view.getByText('帮助文章加载失败')).toBeTruthy());
    mockApi.getHelpArticle.mockResolvedValue({ id: 'password', categoryId: 'account', title: '如何修改密码', summary: '说明正文', updatedAt: '2026-08-10' });
    fireEvent.press(view.getByText('重新加载'));
    await waitFor(() => expect(view.getByText('说明正文')).toBeTruthy());
  });

  it('routes every profile menu item while showing a failed overview request', async () => {
    mockApi.getOverview.mockRejectedValue('offline');
    const onOpen = jest.fn();
    const view = await render(<ProfileHome activeRoute="membership" onOpen={onOpen} />);
    await waitFor(() => expect(view.getByText('个人资料加载失败')).toBeTruthy());
    for (const [label, route] of [
      ['个人概览', 'overview'], ['学习目标与洞察', 'insights'], ['会员权益', 'membership'],
      ['助手设置', 'assistant'], ['账号与安全', 'account'], ['帮助中心', 'help'], ['关于产品', 'about'],
    ] as const) {
      fireEvent.press(view.getByText(label));
      expect(onOpen).toHaveBeenCalledWith(route);
    }
  });

});
