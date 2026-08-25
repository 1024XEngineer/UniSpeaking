import { cleanup, fireEvent, render, waitFor } from '@testing-library/react-native';

const mockApi = {
  getHelpCenter: jest.fn(async () => { throw new Error('中心失败'); }),
  getHelpCategory: jest.fn(async () => { throw new Error('分类失败'); }),
  getHelpArticle: jest.fn(async () => { throw new Error('文章失败'); }),
};
jest.mock('@/features/profile/ProfileApi', () => ({ ProfileApi: jest.fn(() => mockApi) }));
jest.mock('@/model/AppModel', () => ({ useAppModel: () => ({ signOut: jest.fn() }) }));

import { HelpArticle, HelpCategory, HelpCenter } from '../ProfileScreen';

afterEach(() => { cleanup(); jest.clearAllMocks(); });

it.each(['center', 'category', 'article'] as const)('keeps %s retry errors visible', async (kind) => {
  const view = await render(
    kind === 'center'
      ? <HelpCenter onBack={jest.fn()} onOpenCategory={jest.fn()} />
      : kind === 'category'
        ? <HelpCategory categoryId="account" onBack={jest.fn()} onOpenArticle={jest.fn()} />
        : <HelpArticle articleId="password" onBack={jest.fn()} />,
  );
  await waitFor(() => expect(view.getByText(`${kind === 'center' ? '中心' : kind === 'category' ? '分类' : '文章'}失败`)).toBeTruthy());
  await fireEvent.press(view.getByText('重新加载'));
  const request = kind === 'center' ? mockApi.getHelpCenter : kind === 'category' ? mockApi.getHelpCategory : mockApi.getHelpArticle;
  await waitFor(() => expect(request).toHaveBeenCalledTimes(2));
});
