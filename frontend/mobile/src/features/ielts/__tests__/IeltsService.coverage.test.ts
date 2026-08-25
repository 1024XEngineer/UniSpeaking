import { IeltsService } from '../IeltsService';

jest.mock('@/features/scenes/SceneService', () => ({ createWavUploadFile: (uri: string) => ({ uri }) }));

describe('IeltsService request contracts', () => {
  it('builds settings, topic, session and evaluation requests', async () => {
    const request = jest.fn(async (path: string, options?: { method?: string }) =>
      path.endsWith('/evaluation') && options?.method === 'POST'
        ? { status: 'COMPLETED', result: { assessmentType: 'DIAGNOSTIC' } }
        : {});
    const service = new IeltsService({ request });
    await service.getSettings();
    await service.updateSettings({ targetScore: 7, examinerId: 'james' });
    await service.searchTopics({ part: 'PART_1', category: 'food', keyword: '  tea ', page: 2, pageSize: 5 });
    await service.getTraining('PART_2', 'topic/1');
    await service.generateScene({ mode: 'PART_PRACTICE', part: 'PART_1', topicId: 'topic-1' });
    await service.createFlow('scene/1');
    await service.generateEvaluation('ielts/1', 'session/1');
    await service.getEvaluationHistory();
    await service.getDialogueState('ielts/1', 'session/1');
    await service.advanceDialogueState('ielts/1', 'session/1', 2, true);
    await service.getPart2State('ielts/1', 'session/1');
    await service.advancePart2State('ielts/1', 'session/1', 'START_PREPARATION' as any);
    await service.evaluateTurn('ielts/1', 'session/1', 1, 'hello', 'file.wav');
    expect(request).toHaveBeenCalledWith('/api/ielts/settings');
    expect(request).toHaveBeenCalledWith('/api/ielts/topics?part=PART_1&page=2&pageSize=5&category=food&keyword=tea');
    expect(request).toHaveBeenCalledWith(expect.stringContaining('timedOut=true'), { method: 'POST' });
    expect(request).toHaveBeenCalledTimes(13);
  });
});
