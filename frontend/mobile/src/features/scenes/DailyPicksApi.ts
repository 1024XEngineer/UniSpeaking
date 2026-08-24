import { sceneCategories, type SceneCategory } from '@/data/sceneCategories';
import type { DailyRecommendation } from '@/data/content';

type ApiRequester = {
  request(path: string, options?: RequestInit): Promise<unknown>;
};

export type DailyPicks = {
  date: string;
  timezone: string;
  nextRefreshAt: string;
  picks: (DailyRecommendation & { position: number })[];
};

function isSceneCategory(value: unknown): value is SceneCategory {
  return typeof value === 'string' && value in sceneCategories;
}

function isDailyRecommendation(value: unknown): value is DailyRecommendation & { position: number } {
  if (!value || typeof value !== 'object') return false;
  const item = value as Partial<DailyRecommendation & { position: number }>;
  return Boolean(
    item.id?.trim() &&
      item.title?.trim() &&
      isSceneCategory(item.category) &&
      item.duration?.trim() &&
      item.level?.trim() &&
      item.goal?.trim() &&
      item.sceneInput?.trim() &&
      Number.isInteger(item.position),
  );
}

export class DailyPicksApi {
  constructor(private readonly client: ApiRequester) {}

  async getDailyPicks() {
    const response = await this.client.request('/api/daily-picks') as DailyPicks;
    if (!response || !Array.isArray(response.picks) || response.picks.length !== 3 || !response.picks.every(isDailyRecommendation)) {
      throw new Error('每日推荐数据不完整');
    }
    return response;
  }
}
