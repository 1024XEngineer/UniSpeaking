import { act, renderHook, waitFor } from '@testing-library/react-native';

import { createIeltsService } from '../createIeltsService';
import { useIeltsFlowController } from '../useIeltsFlowController';

jest.mock('../createIeltsService', () => ({
  createIeltsService: jest.fn(),
}));

const mockedCreateIeltsService = jest.mocked(createIeltsService);

function createService() {
  return {
    getSettings: jest.fn(async () => ({ targetScore: 6.5, examinerId: 'daniel' })),
    updateSettings: jest.fn(async (input) => ({ targetScore: 6.5, examinerId: input.examinerId ?? 'daniel' })),
    searchTopics: jest.fn(async () => ({
      categories: [{ code: 'FOOD', label: '食物' }],
      topics: [{ id: 'topic-1', title: 'Food' }],
      total: 1,
      totalPages: 1,
    })),
    generateScene: jest.fn(async () => ({ ieltsId: 'ielts-1', selectedTopicId: 'topic-1' })),
    createFlow: jest.fn(async () => ({ sceneId: 'ielts-1' })),
    getTraining: jest.fn(async () => ({ ieltsId: 'ielts-1', part: 'PART_2' })),
    generateEvaluation: jest.fn(async () => ({ overallBand: 7 })),
    getEvaluationHistory: jest.fn(async () => []),
  };
}

describe('useIeltsFlowController', () => {
  beforeEach(() => {
    jest.useFakeTimers();
    mockedCreateIeltsService.mockReturnValue(createService() as never);
  });

  afterEach(() => {
    jest.useRealTimers();
    jest.clearAllMocks();
  });

  it('loads settings after mount and persists the selected target', async () => {
    const service = createService();
    mockedCreateIeltsService.mockReturnValue(service as never);
    const { result } = await renderHook(() => useIeltsFlowController());

    await act(async () => {
      await jest.runOnlyPendingTimersAsync();
    });
    await waitFor(() => expect(result.current.settings).toEqual({ targetScore: 6.5, examinerId: 'daniel' }));

    await act(async () => {
      await result.current.saveTargetScore('7.5+');
    });

    expect(service.updateSettings).toHaveBeenCalledWith({ targetScore: 7.5 });
    expect(result.current.settings).toEqual({ targetScore: 6.5, examinerId: 'daniel' });
  });

  it('maps topic filters and clears stale data when search fails', async () => {
    const service = createService();
    mockedCreateIeltsService.mockReturnValue(service as never);
    const { result } = await renderHook(() => useIeltsFlowController());

    await act(async () => {
      await result.current.loadTopics('p1', 'ALL', 'food', 2);
    });

    expect(service.searchTopics).toHaveBeenCalledWith({
      part: 'PART_1', category: null, keyword: 'food', page: 2, pageSize: 5,
    });
    expect(result.current.topics).toEqual([{ id: 'topic-1', title: 'Food' }]);

    service.searchTopics.mockRejectedValueOnce(new Error('题库不可用'));
    await act(async () => {
      await result.current.loadTopics('p3', 'FOOD', '', 1);
    });
    expect(result.current.topics).toEqual([]);
    expect(result.current.topicTotal).toBe(0);
    expect(result.current.topicsError).toBe('题库不可用');
  });

  it('prepares Part 2 in order, exposes training, and retains the backend failure', async () => {
    const service = createService();
    mockedCreateIeltsService.mockReturnValue(service as never);
    const { result } = await renderHook(() => useIeltsFlowController());

    await act(async () => {
      await result.current.prepareSession({
        part: 'p2', topicId: 'topic-1', random: false, examiner: { id: 'marcus' } as never,
      });
    });

    expect(service.updateSettings).toHaveBeenCalledWith({ examinerId: 'marcus' });
    expect(service.generateScene).toHaveBeenCalledWith({ mode: 'PART_PRACTICE', part: 'PART_2', topicId: 'topic-1' });
    expect(service.createFlow).toHaveBeenCalledWith('ielts-1');
    expect(service.getTraining).toHaveBeenCalledWith('PART_2', 'topic-1');
    expect(result.current.sessionBusy).toBe(false);
    expect(result.current.training).toEqual({ ieltsId: 'ielts-1', part: 'PART_2' });

    service.generateScene.mockRejectedValueOnce(new Error('生成失败'));
    let caught: unknown;
    await act(async () => {
      try {
        await result.current.prepareSession({
          part: 'p1', random: true, examiner: { id: 'daniel' } as never,
        });
      } catch (error) {
        caught = error;
      }
    });
    expect(caught).toEqual(new Error('生成失败'));
    expect(result.current.sessionError).toBe('生成失败');
    expect(result.current.sessionBusy).toBe(false);
  });

  it('stores evaluation and degrades history refresh to an empty collection', async () => {
    const service = createService();
    mockedCreateIeltsService.mockReturnValue(service as never);
    const { result } = await renderHook(() => useIeltsFlowController());

    await act(async () => {
      await result.current.finalizeEvaluation('ielts-1', 'session-1');
    });
    expect(result.current.latestEvaluation).toEqual({ overallBand: 7 });

    service.getEvaluationHistory.mockRejectedValueOnce(new Error('offline'));
    await act(async () => {
      await result.current.refreshHistory();
    });
    expect(result.current.historyRecords).toEqual([]);
  });
});
