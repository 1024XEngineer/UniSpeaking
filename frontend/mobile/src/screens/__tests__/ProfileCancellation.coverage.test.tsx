import { act, render } from '@testing-library/react-native';

const mockApi = {
  getOverview: jest.fn(), getAchievements: jest.fn(), getInsights: jest.fn(),
  getHelpCenter: jest.fn(), getHelpCategory: jest.fn(), getHelpArticle: jest.fn(),
};

jest.mock('@/features/profile/ProfileApi', () => ({ ProfileApi: jest.fn(() => mockApi) }));
jest.mock('@/model/AppModel', () => ({
  useAppModel: () => ({
    nickname: 'Ada', email: 'ada@example.com', setNickname: jest.fn(), signOut: jest.fn(),
    teacher: { id: 'clara', name: 'Clara', image: 1 },
  }),
}));

import { HelpArticle, HelpCategory, HelpCenter, Insights, Overview, ProfileHome } from '../ProfileScreen';

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (cause: unknown) => void;
  const promise = new Promise<T>((res, rej) => { resolve = res; reject = rej; });
  void promise.catch(() => undefined);
  return { promise, resolve, reject };
}

async function settle(action: () => void) {
  await act(async () => {
    action();
    await Promise.resolve();
    await Promise.resolve();
    await Promise.resolve();
  });
}

describe('Profile async cancellation guards', () => {
  beforeEach(() => jest.clearAllMocks());

  it('ignores overview, achievement, and insight success after unmount', async () => {
    const overview = deferred<any>();
    const achievements = deferred<any>();
    mockApi.getOverview.mockReturnValueOnce(overview.promise);
    mockApi.getAchievements.mockReturnValueOnce(achievements.promise);
    const overviewView = await render(<Overview onBack={jest.fn()} />);
    overviewView.unmount();
    await settle(() => {
      overview.resolve({});
      achievements.resolve({ series: [] });
    });

    const insights = deferred<any>();
    mockApi.getInsights.mockReturnValueOnce(insights.promise);
    const insightsView = await render(<Insights onBack={jest.fn()} />);
    insightsView.unmount();
    await settle(() => insights.resolve({}));
  });

  it('ignores overview, achievement, and insight failures after unmount', async () => {
    const overview = deferred<any>();
    const achievements = deferred<any>();
    mockApi.getOverview.mockReturnValueOnce(overview.promise);
    mockApi.getAchievements.mockReturnValueOnce(achievements.promise);
    const overviewView = await render(<Overview onBack={jest.fn()} />);
    overviewView.unmount();
    await settle(() => {
      overview.reject(new Error('late overview'));
      achievements.reject(new Error('late achievements'));
    });

    const insights = deferred<any>();
    mockApi.getInsights.mockReturnValueOnce(insights.promise);
    const insightsView = await render(<Insights onBack={jest.fn()} />);
    insightsView.unmount();
    await settle(() => insights.reject(new Error('late insights')));
  });

  it.each([
    ['center', () => <HelpCenter onBack={jest.fn()} onOpenCategory={jest.fn()} />, 'getHelpCenter'],
    ['category', () => <HelpCategory categoryId="account" onBack={jest.fn()} onOpenArticle={jest.fn()} />, 'getHelpCategory'],
    ['article', () => <HelpArticle articleId="password" onBack={jest.fn()} />, 'getHelpArticle'],
  ] as const)('ignores late help %s success and failure', async (_name, create, method) => {
    const success = deferred<any>();
    mockApi[method].mockReturnValueOnce(success.promise);
    const successView = await render(create());
    successView.unmount();
    await settle(() => success.resolve({ categories: [], articles: [] }));

    const failure = deferred<any>();
    mockApi[method].mockReturnValueOnce(failure.promise);
    const failureView = await render(create());
    failureView.unmount();
    await settle(() => failure.reject(new Error('late help failure')));
  });

  it('ignores a late profile home success and failure', async () => {
    const success = deferred<any>();
    mockApi.getOverview.mockReturnValueOnce(success.promise);
    const successView = await render(<ProfileHome onOpen={jest.fn()} />);
    successView.unmount();
    await settle(() => success.resolve({ account: { nickname: 'Late', displayName: 'Late' } }));

    const failure = deferred<any>();
    mockApi.getOverview.mockReturnValueOnce(failure.promise);
    const failureView = await render(<ProfileHome onOpen={jest.fn()} />);
    failureView.unmount();
    await settle(() => failure.reject(new Error('late profile')));
  });
});
