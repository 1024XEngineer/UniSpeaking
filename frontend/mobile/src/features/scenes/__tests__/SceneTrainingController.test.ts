import {
  SceneTrainingController,
  type SceneTrainingServicePort,
} from '../SceneTrainingController';
import type {
  GeneratedScene,
  LearningContentItem,
  SceneFlowStage,
  SentenceEvaluation,
} from '../SceneService';

const word: LearningContentItem = {
  contentId: 'word-1',
  englishText: 'decaf',
  chineseText: '无咖啡因的',
  phonetic: '/ˈdiːkæf/',
};
const phrase: LearningContentItem = {
  contentId: 'phrase-1',
  englishText: 'Could I get',
  chineseText: '我可以要……吗',
  phonetic: null,
};
const sentence: LearningContentItem = {
  contentId: 'sentence-1',
  englishText: 'Could I get a decaf latte?',
  chineseText: '我可以要一杯无咖啡因拿铁吗？',
  phonetic: null,
};
const secondSentence: LearningContentItem = {
  contentId: 'sentence-2',
  englishText: 'Could you make it extra hot?',
  chineseText: '可以做得更热一些吗？',
  phonetic: null,
};
const scene: GeneratedScene = {
  sceneId: 'scene-1',
  title: '咖啡店点单',
  label: '餐饮',
  background: 'A coffee shop.',
  aiRole: 'Barista',
  userRole: 'Customer',
  learningGoal: 'Order a drink.',
  estimatedMinutes: 8,
  wordList: [word],
  phraseList: [phrase],
  sentenceList: [sentence],
  scenePrompt: 'Prompt',
};

function createService(): SceneTrainingServicePort & {
  createFlow: jest.Mock;
  getContent: jest.Mock;
  advanceStage: jest.Mock;
  evaluateSentence: jest.Mock;
} {
  return {
    createFlow: jest.fn(async () => ({
      sceneId: 'scene-1',
      stage: 'WORD_LEARNING' as const,
      completed: false,
    })),
    getContent: jest.fn(async (_sceneId, stage) => {
      if (stage === 'WORD_LEARNING') return [word];
      if (stage === 'PHRASE_LEARNING') return [phrase];
      return [sentence];
    }),
    advanceStage: jest.fn(async (_sceneId, stage) => ({
      sceneId: 'scene-1',
      stage: (
        stage === 'WORD_LEARNING'
          ? 'PHRASE_LEARNING'
          : stage === 'PHRASE_LEARNING'
            ? 'SENTENCE_LEARNING'
            : 'DIALOGUE') as SceneFlowStage,
      completed: false,
    })),
    evaluateSentence: jest.fn(async (): Promise<SentenceEvaluation> => ({
      overallScore: 86,
      passed: true,
      words: [],
    })),
  };
}

describe('SceneTrainingController', () => {
  it('starts the backend flow and exposes its real word content', async () => {
    const service = createService();
    const controller = new SceneTrainingController(service);

    await controller.start(scene);

    expect(service.createFlow).toHaveBeenCalledWith('scene-1');
    expect(service.getContent).toHaveBeenCalledWith(
      'scene-1',
      'WORD_LEARNING',
    );
    expect(controller.getSnapshot()).toEqual(
      expect.objectContaining({
        status: 'ready',
        stage: 'learn',
        learningGroup: 'words',
        currentItem: word,
        index: 0,
      }),
    );
  });

  it('reuses the normal dialogue stage for repractice without replaying learning transitions', async () => {
    const service = createService();
    const controller = new SceneTrainingController(service);

    await controller.start(scene, 'speak');

    expect(controller.getSnapshot()).toEqual(
      expect.objectContaining({
        status: 'ready',
        stage: 'speak',
        unlockedStage: 2,
      }),
    );
    expect(service.createFlow).not.toHaveBeenCalled();
    expect(service.advanceStage).not.toHaveBeenCalled();
    expect(service.getContent).not.toHaveBeenCalled();
  });

  it('advances words to phrases and phrases to the reading stage', async () => {
    const service = createService();
    const controller = new SceneTrainingController(service);
    await controller.start(scene);

    await controller.next();
    expect(controller.getSnapshot()).toEqual(
      expect.objectContaining({
        stage: 'learn',
        learningGroup: 'phrases',
        currentItem: phrase,
      }),
    );
    await controller.next();
    expect(controller.getSnapshot()).toEqual(
      expect.objectContaining({
        stage: 'read',
        currentItem: sentence,
        unlockedStage: 1,
      }),
    );
    expect(service.advanceStage.mock.calls).toEqual([
      ['scene-1', 'WORD_LEARNING'],
      ['scene-1', 'PHRASE_LEARNING'],
    ]);
  });

  it('treats words and phrases as one learn stage when the learn step is selected', async () => {
    const service = createService();
    const controller = new SceneTrainingController(service);
    await controller.start(scene);
    await controller.next();
    expect(controller.getSnapshot()).toEqual(
      expect.objectContaining({
        stage: 'learn',
        learningGroup: 'phrases',
        currentItem: phrase,
      }),
    );

    await expect(controller.selectStage('learn')).resolves.toBe(true);
    expect(controller.getSnapshot()).toEqual(
      expect.objectContaining({
        stage: 'learn',
        learningGroup: 'words',
        currentItem: word,
        index: 0,
      }),
    );
    expect(service.advanceStage).toHaveBeenCalledTimes(1);
  });

  it('moves backward from the first phrase to the last word within the learn stage', async () => {
    const service = createService();
    const controller = new SceneTrainingController(service);
    await controller.start(scene);
    await controller.next();

    expect(controller.previous()).toBe(true);
    expect(controller.getSnapshot()).toEqual(
      expect.objectContaining({
        stage: 'learn',
        learningGroup: 'words',
        currentItem: word,
        index: 0,
      }),
    );
    expect(service.advanceStage).toHaveBeenCalledTimes(1);
  });

  it('ignores a duplicate next action while a stage transition is pending', async () => {
    const service = createService();
    const controller = new SceneTrainingController(service);
    await controller.start(scene);

    let resolveAdvance!: (flow: {
      sceneId: string;
      stage: SceneFlowStage;
      completed: boolean;
    }) => void;
    service.advanceStage.mockImplementationOnce(
      () => new Promise((resolve) => {
        resolveAdvance = resolve;
      }),
    );

    const first = controller.next();
    await expect(controller.next()).resolves.toBe(false);
    expect(service.advanceStage).toHaveBeenCalledTimes(1);

    resolveAdvance({
      sceneId: 'scene-1',
      stage: 'PHRASE_LEARNING',
      completed: false,
    });
    await expect(first).resolves.toBe(true);
    expect(controller.getSnapshot()).toEqual(
      expect.objectContaining({
        status: 'ready',
        learningGroup: 'phrases',
        currentItem: phrase,
      }),
    );
  });

  it('scores the current WAV and unlocks dialogue only after a passing result', async () => {
    const service = createService();
    const controller = new SceneTrainingController(service);
    await controller.start(scene);
    await controller.next();
    await controller.next();

    await controller.scoreReading('file:///sentence.wav');
    expect(service.evaluateSentence).toHaveBeenCalledWith(
      'scene-1',
      'sentence-1',
      'file:///sentence.wav',
    );
    expect(controller.getSnapshot().readingResult).toEqual(
      expect.objectContaining({ overallScore: 86, passed: true }),
    );

    await expect(controller.next()).resolves.toBe(true);
    expect(controller.getSnapshot()).toEqual(
      expect.objectContaining({ stage: 'speak', unlockedStage: 2 }),
    );
    expect(service.advanceStage).toHaveBeenLastCalledWith(
      'scene-1',
      'SENTENCE_LEARNING',
    );
  });

  it('restores the passing result when returning to a previously scored sentence', async () => {
    const service = createService();
    service.getContent.mockImplementation(async (_sceneId, stage) => {
      if (stage === 'WORD_LEARNING') return [word];
      if (stage === 'PHRASE_LEARNING') return [phrase];
      return [sentence, secondSentence];
    });
    const controller = new SceneTrainingController(service);
    await controller.start(scene);
    await controller.next();
    await controller.next();

    await controller.scoreReading('file:///sentence-1.wav');
    await controller.next();
    expect(controller.getSnapshot()).toEqual(
      expect.objectContaining({ index: 1, readingResult: null }),
    );

    expect(controller.previous()).toBe(true);
    expect(controller.getSnapshot()).toEqual(
      expect.objectContaining({
        index: 0,
        currentItem: sentence,
        readingResult: expect.objectContaining({ overallScore: 86, passed: true }),
      }),
    );
  });

  it('can return to an unlocked stage without replaying backend transitions', async () => {
    const service = createService();
    const controller = new SceneTrainingController(service);
    await controller.start(scene);
    await controller.next();
    await controller.next();
    await controller.scoreReading('file:///sentence.wav');
    await controller.next();

    await expect(controller.selectStage('read')).resolves.toBe(true);
    expect(controller.getSnapshot()).toEqual(
      expect.objectContaining({ stage: 'read', currentItem: sentence, unlockedStage: 2 }),
    );
    await expect(controller.selectStage('learn')).resolves.toBe(true);
    expect(controller.getSnapshot()).toEqual(
      expect.objectContaining({ stage: 'learn', learningGroup: 'words', currentItem: word, unlockedStage: 2 }),
    );
    await expect(controller.next()).resolves.toBe(true);
    expect(controller.getSnapshot()).toEqual(
      expect.objectContaining({ stage: 'learn', learningGroup: 'phrases', currentItem: phrase, unlockedStage: 2 }),
    );
    expect(service.advanceStage).toHaveBeenCalledTimes(4);
  });

  it('returns from the first reading sentence to learning', async () => {
    const service = createService();
    const controller = new SceneTrainingController(service);
    await controller.start(scene);
    await controller.next();
    await controller.next();

    await expect(controller.selectStage('learn')).resolves.toBe(true);
    expect(controller.getSnapshot()).toEqual(
      expect.objectContaining({ stage: 'learn', currentItem: word }),
    );
  });

  it('publishes every update and supports listener removal', async () => {
    const service = createService();
    const controller = new SceneTrainingController(service);
    const listener = jest.fn();
    const unsubscribe = controller.subscribe(listener);
    expect(listener).toHaveBeenCalledWith(expect.objectContaining({ status: 'idle' }));
    await controller.start(scene);
    const count = listener.mock.calls.length;
    unsubscribe();
    await controller.next();
    expect(listener).toHaveBeenCalledTimes(count);
  });

  it('starts directly in reading and rejects an unexpected backend stage', async () => {
    const service = createService();
    const controller = new SceneTrainingController(service);
    await controller.start(scene, 'read');
    expect(controller.getSnapshot()).toEqual(expect.objectContaining({
      status: 'ready', stage: 'read', currentItem: sentence, unlockedStage: 1,
    }));

    const invalid = createService();
    invalid.advanceStage
      .mockResolvedValueOnce({ sceneId: 'scene-1', stage: 'PHRASE_LEARNING', completed: false })
      .mockResolvedValueOnce({ sceneId: 'scene-1', stage: 'PHRASE_LEARNING', completed: false });
    const failed = new SceneTrainingController(invalid);
    await expect(failed.start(scene, 'read')).rejects.toThrow('后端未进入句子学习阶段');
    expect(failed.getSnapshot()).toEqual(expect.objectContaining({
      status: 'error', error: '后端未进入句子学习阶段',
    }));
  });

  it('rejects empty content and normalizes non-Error failures', async () => {
    const empty = createService();
    empty.getContent.mockResolvedValueOnce([]);
    const first = new SceneTrainingController(empty);
    await expect(first.start(scene)).rejects.toThrow('当前训练阶段没有可用内容');

    const nonError = createService();
    nonError.createFlow.mockRejectedValueOnce('offline');
    const second = new SceneTrainingController(nonError);
    await expect(second.start(scene)).rejects.toBe('offline');
    expect(second.getSnapshot()).toEqual(expect.objectContaining({
      status: 'error', error: '场景训练请求失败',
    }));
  });

  it('guards navigation before start, in speak mode, and at locked boundaries', async () => {
    const controller = new SceneTrainingController(createService());
    await expect(controller.next()).rejects.toThrow('场景训练尚未开始');
    await expect(controller.selectStage('read')).rejects.toThrow('场景训练尚未开始');
    await expect(controller.scoreReading('file.wav')).rejects.toThrow('场景训练尚未开始');
    expect(controller.previous()).toBe(false);

    await controller.start(scene);
    expect(controller.previous()).toBe(false);
    await expect(controller.selectStage('learn')).resolves.toBe(true);
    await expect(controller.selectStage('speak')).resolves.toBe(false);

    await controller.start(scene, 'speak');
    await expect(controller.next()).resolves.toBe(false);
    await expect(controller.selectStage('speak')).resolves.toBe(true);
    await expect(controller.selectStage('learn')).resolves.toBe(true);
    await expect(controller.selectStage('speak')).resolves.toBe(true);
  });

  it('blocks actions while scoring and keeps dialogue locked after a failed reading', async () => {
    const service = createService();
    let resolveScore!: (result: SentenceEvaluation) => void;
    service.evaluateSentence.mockImplementationOnce(() => new Promise((resolve) => { resolveScore = resolve; }));
    const controller = new SceneTrainingController(service);
    await controller.start(scene, 'read');
    const scoring = controller.scoreReading('sentence.wav');
    await expect(controller.next()).resolves.toBe(false);
    await expect(controller.selectStage('learn')).resolves.toBe(false);
    resolveScore({ overallScore: 40, passed: false, words: [] });
    await scoring;
    await expect(controller.next()).resolves.toBe(false);
  });

  it('surfaces scoring and dialogue transition failures', async () => {
    const scoringService = createService();
    scoringService.evaluateSentence.mockRejectedValueOnce(new Error('score failed'));
    const scoring = new SceneTrainingController(scoringService);
    await scoring.start(scene, 'read');
    await expect(scoring.scoreReading('sentence.wav')).rejects.toThrow('score failed');
    expect(scoring.getSnapshot()).toEqual(expect.objectContaining({ status: 'error', error: 'score failed' }));

    const dialogueService = createService();
    dialogueService.advanceStage.mockImplementation(async (_sceneId, stage) => ({
      sceneId: 'scene-1',
      stage: stage === 'SENTENCE_LEARNING' ? 'SENTENCE_LEARNING' : stage === 'WORD_LEARNING' ? 'PHRASE_LEARNING' : 'SENTENCE_LEARNING',
      completed: false,
    } as any));
    const dialogue = new SceneTrainingController(dialogueService);
    await dialogue.start(scene, 'read');
    await dialogue.scoreReading('sentence.wav');
    await expect(dialogue.next()).rejects.toThrow('后端未进入场景对话阶段');
  });

  it('rejects unexpected learning transitions and empty selected-stage content', async () => {
    const transitionService = createService();
    transitionService.advanceStage.mockResolvedValueOnce({ sceneId: 'scene-1', stage: 'WORD_LEARNING', completed: false });
    const transition = new SceneTrainingController(transitionService);
    await transition.start(scene);
    await expect(transition.next()).rejects.toThrow('后端未进入预期训练阶段：PHRASE_LEARNING');

    const emptyService = createService();
    const selected = new SceneTrainingController(emptyService);
    await selected.start(scene, 'speak');
    emptyService.getContent.mockResolvedValueOnce([]);
    await expect(selected.selectStage('read')).rejects.toThrow('当前训练阶段没有可用内容');
  });
});
