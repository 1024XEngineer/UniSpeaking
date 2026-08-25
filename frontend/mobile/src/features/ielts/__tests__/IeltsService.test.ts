import type { ApiRequestOptions } from '@/infrastructure/http/ApiClient';

import { IeltsService } from '../IeltsService';

describe('IeltsService', () => {
	beforeEach(() => jest.useRealTimers());
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

	it('polls evaluation status and returns only the completed result', async () => {
		jest.useFakeTimers();
		const result = { assessmentType: 'DIAGNOSTIC', summary: 'Good work' };
		const client = {
			request: jest.fn()
				.mockResolvedValueOnce({ status: 'PROCESSING' })
				.mockResolvedValueOnce({ status: 'COMPLETED', result }),
		};
		const service = new IeltsService(client);
		const evaluation = service.generateEvaluation('ielts/1', 'session/1');

		await jest.advanceTimersByTimeAsync(1_000);

		await expect(evaluation).resolves.toEqual(result);
		expect(client.request).toHaveBeenLastCalledWith(
			'/api/ielts/ielts%2F1/sessions/session%2F1/evaluation',
		);
	});

	it('surfaces an asynchronous evaluation failure reason', async () => {
		const service = new IeltsService({
			request: jest.fn(async () => ({
				status: 'FAILED',
				failureReason: '评分材料不足',
			})),
		});

		await expect(service.generateEvaluation('i', 's')).rejects.toThrow('评分材料不足');
	});
});
