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
});
