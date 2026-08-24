import { DailyPicksApi } from '../DailyPicksApi';

const validResponse = {
  date: '2026-08-24',
  timezone: 'Asia/Shanghai',
  nextRefreshAt: '2026-08-24T16:00:00Z',
  picks: [
    {
      id: 'coffee-order',
      position: 1,
      title: '咖啡店点单',
      category: 'food',
      duration: '8–10 分钟',
      level: '初级',
      goal: '流利点单，清晰表达需求',
      sceneInput: '在咖啡店点单并确认细节',
    },
    {
      id: 'hotel-check-in',
      position: 2,
      title: '酒店入住',
      category: 'accommodation',
      duration: '10–12 分钟',
      level: '中级',
      goal: '确认入住细节',
      sceneInput: '在酒店前台办理入住',
    },
    {
      id: 'pharmacy-advice',
      position: 3,
      title: '药店咨询',
      category: 'health',
      duration: '8–10 分钟',
      level: '中级',
      goal: '描述症状并确认用法',
      sceneInput: '在药店描述症状并咨询用药',
    },
  ],
} as const;

describe('DailyPicksApi', () => {
  it('loads the shared daily recommendations from the backend', async () => {
    const request = jest.fn(async () => validResponse);
    const api = new DailyPicksApi({ request });

    await expect(api.getDailyPicks()).resolves.toEqual(validResponse);
    expect(request).toHaveBeenCalledWith('/api/daily-picks');
  });

  it('rejects an incomplete recommendation batch', async () => {
    const api = new DailyPicksApi({
      request: jest.fn(async () => ({ ...validResponse, picks: validResponse.picks.slice(0, 2) })),
    });

    await expect(api.getDailyPicks()).rejects.toThrow('每日推荐数据不完整');
  });
});
