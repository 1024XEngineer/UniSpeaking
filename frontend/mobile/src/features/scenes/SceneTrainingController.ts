import type {
  GeneratedScene,
  LearningContentItem,
  SceneFlow,
  SceneFlowStage,
  SentenceEvaluation,
} from './SceneService';

export type SceneTrainingServicePort = {
  createFlow(sceneId: string): Promise<SceneFlow>;
  getContent(
    sceneId: string,
    stage?: SceneFlowStage,
  ): Promise<LearningContentItem[]>;
  advanceStage(sceneId: string, stage: SceneFlowStage): Promise<SceneFlow>;
  evaluateSentence(
    sceneId: string,
    sentenceId: string,
    wavUri: string,
  ): Promise<SentenceEvaluation>;
};

export type SceneTrainingSnapshot = Readonly<{
  status: 'idle' | 'loading' | 'ready' | 'scoring' | 'error';
  scene: GeneratedScene | null;
  stage: 'learn' | 'read' | 'speak';
  learningGroup: 'words' | 'phrases';
  items: LearningContentItem[];
  currentItem: LearningContentItem | null;
  index: number;
  unlockedStage: 0 | 1 | 2;
  readingResult: SentenceEvaluation | null;
  error: string | null;
}>;

const initialSnapshot: SceneTrainingSnapshot = {
  status: 'idle',
  scene: null,
  stage: 'learn',
  learningGroup: 'words',
  items: [],
  currentItem: null,
  index: 0,
  unlockedStage: 0,
  readingResult: null,
  error: null,
};

type Listener = (snapshot: SceneTrainingSnapshot) => void;

export class SceneTrainingController {
  private snapshot = initialSnapshot;
  private readonly listeners = new Set<Listener>();

  constructor(private readonly service: SceneTrainingServicePort) {}

  getSnapshot() {
    return this.snapshot;
  }

  subscribe(listener: Listener) {
    this.listeners.add(listener);
    listener(this.snapshot);
    return () => {
      this.listeners.delete(listener);
    };
  }

  async start(scene: GeneratedScene, initialStage: 'learn' | 'read' | 'speak' = 'learn') {
    this.update({
      ...initialSnapshot,
      status: 'loading',
      scene,
    });
    try {
      if (initialStage === 'speak') {
        this.update({
          ...this.snapshot,
          status: 'ready',
          stage: 'speak',
          unlockedStage: 2,
        });
        return;
      }
      await this.service.createFlow(scene.sceneId);
      if (initialStage === 'read') {
        await this.service.advanceStage(scene.sceneId, 'WORD_LEARNING');
        const flow = await this.service.advanceStage(scene.sceneId, 'PHRASE_LEARNING');
        if (flow.stage !== 'SENTENCE_LEARNING') throw new Error('后端未进入句子学习阶段');
        const items = await this.requireContent(scene.sceneId, 'SENTENCE_LEARNING');
        this.update({
          ...this.snapshot,
          status: 'ready',
          stage: 'read',
          items,
          currentItem: items[0],
          unlockedStage: 1,
        });
        return;
      }
      const items = await this.requireContent(scene.sceneId, 'WORD_LEARNING');
      this.update({
        ...this.snapshot,
        status: 'ready',
        items,
        currentItem: items[0],
      });
    } catch (error) {
      this.fail(error);
      throw error;
    }
  }

  async next() {
    if (this.snapshot.status === 'loading' || this.snapshot.status === 'scoring') {
      return false;
    }
    const scene = this.requireScene();
    if (this.snapshot.stage === 'speak') return false;
    if (this.snapshot.index < this.snapshot.items.length - 1) {
      const index = this.snapshot.index + 1;
      this.update({
        ...this.snapshot,
        index,
        currentItem: this.snapshot.items[index],
        readingResult: null,
      });
      return true;
    }

    if (this.snapshot.stage === 'learn') {
      const currentStage: SceneFlowStage =
        this.snapshot.learningGroup === 'words'
          ? 'WORD_LEARNING'
          : 'PHRASE_LEARNING';
      const nextStage: SceneFlowStage =
        currentStage === 'WORD_LEARNING'
          ? 'PHRASE_LEARNING'
          : 'SENTENCE_LEARNING';
      await this.loadStage(scene.sceneId, currentStage, nextStage);
      return true;
    }

    if (!this.snapshot.readingResult?.passed) return false;
    this.update({ ...this.snapshot, status: 'loading', error: null });
    try {
      const flow = await this.service.advanceStage(
        scene.sceneId,
        'SENTENCE_LEARNING',
      );
      if (flow.stage !== 'DIALOGUE') {
        throw new Error('后端未进入场景对话阶段');
      }
      this.update({
        ...this.snapshot,
        status: 'ready',
        stage: 'speak',
        unlockedStage: 2,
        readingResult: null,
      });
      return true;
    } catch (error) {
      this.fail(error);
      throw error;
    }
  }

  previous() {
    if (this.snapshot.index <= 0) return false;
    const index = this.snapshot.index - 1;
    this.update({
      ...this.snapshot,
      index,
      currentItem: this.snapshot.items[index],
      readingResult: null,
    });
    return true;
  }

  async scoreReading(wavUri: string) {
    const scene = this.requireScene();
    const sentence = this.snapshot.currentItem;
    if (this.snapshot.stage !== 'read' || !sentence) {
      throw new Error('当前不在朗读阶段');
    }
    this.update({ ...this.snapshot, status: 'scoring', error: null });
    try {
      const readingResult = await this.service.evaluateSentence(
        scene.sceneId,
        sentence.contentId,
        wavUri,
      );
      this.update({ ...this.snapshot, status: 'ready', readingResult });
      return readingResult;
    } catch (error) {
      this.fail(error);
      throw error;
    }
  }

  private async loadStage(
    sceneId: string,
    currentStage: SceneFlowStage,
    nextStage: SceneFlowStage,
  ) {
    this.update({ ...this.snapshot, status: 'loading', error: null });
    try {
      const flow = await this.service.advanceStage(sceneId, currentStage);
      if (flow.stage !== nextStage) {
        throw new Error(`后端未进入预期训练阶段：${nextStage}`);
      }
      const items = await this.requireContent(sceneId, nextStage);
      this.update({
        ...this.snapshot,
        status: 'ready',
        stage: nextStage === 'SENTENCE_LEARNING' ? 'read' : 'learn',
        learningGroup:
          nextStage === 'PHRASE_LEARNING' ? 'phrases' : this.snapshot.learningGroup,
        items,
        currentItem: items[0],
        index: 0,
        unlockedStage: nextStage === 'SENTENCE_LEARNING' ? 1 : 0,
        readingResult: null,
      });
    } catch (error) {
      this.fail(error);
      throw error;
    }
  }

  private async requireContent(sceneId: string, stage: SceneFlowStage) {
    const items = await this.service.getContent(sceneId, stage);
    if (!items.length) throw new Error('当前训练阶段没有可用内容');
    return items;
  }

  private requireScene() {
    if (!this.snapshot.scene) throw new Error('场景训练尚未开始');
    return this.snapshot.scene;
  }

  private fail(error: unknown) {
    this.update({
      ...this.snapshot,
      status: 'error',
      error: error instanceof Error ? error.message : '场景训练请求失败',
    });
  }

  private update(snapshot: SceneTrainingSnapshot) {
    this.snapshot = snapshot;
    this.listeners.forEach((listener) => listener(snapshot));
  }
}
