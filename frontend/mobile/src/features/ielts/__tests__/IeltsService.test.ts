import type { ApiRequestOptions } from '@/infrastructure/http/ApiClient';

import { IeltsService } from '../IeltsService';

describe('IeltsService', () => {
  it('loads topics from the backend ielts endpoint', async () => {
    const client = {
      request: jest.fn(async (_path: string, _options?: ApiRequestOptions) => ({
        categories: [{ code: 'ALL', label: '全部' }],
        topics: [],
        page: 1,
        pageSize: 5,
        total: 0,
        totalPages: 0,
      })),
    };
    const service = new IeltsService(client);

    await service.searchTopics({
      part: 'PART_1',
      category: 'EVENT',
      keyword: 'food',
      page: 2,
      pageSize: 5,
    });

    expect(client.request).toHaveBeenCalledWith(
      '/api/ielts/topics?part=PART_1&page=2&pageSize=5&category=EVENT&keyword=food',
    );
  });

  it('generates ielts scene and creates flow', async () => {
    const client = {
      request: jest.fn(async (path: string, options?: ApiRequestOptions) => {
        if (path === '/api/ielts/generate') {
          return { ieltsId: 'ielts-20', mode: 'PART_PRACTICE', title: 'Food' };
        }
        return { sceneId: 'ielts-20', stage: 'IELTS_PART_1', completed: false };
      }),
    };
    const service = new IeltsService(client);

    const scene = await service.generateScene({
      mode: 'PART_PRACTICE',
      part: 'PART_1',
      topicId: 'topic-1',
    });
    await service.createFlow(scene.ieltsId);

    expect(client.request).toHaveBeenCalledWith('/api/ielts/generate', expect.objectContaining({ method: 'POST' }));
    expect(client.request).toHaveBeenCalledWith('/api/ielts/flows', expect.objectContaining({ method: 'POST' }));
  });
});
