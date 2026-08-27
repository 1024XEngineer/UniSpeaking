import type { ApiRequestOptions } from '@/infrastructure/http/ApiClient';
import { waitForAsyncTask } from '@/infrastructure/http/waitForAsyncTask';
import { File } from 'expo-file-system';

import type { SceneLabel } from '@/data/sceneCategories';

export type SceneFlowStage =
  | 'WORD_LEARNING'
  | 'PHRASE_LEARNING'
  | 'SENTENCE_LEARNING'
  | 'DIALOGUE'
  | 'COMPLETED';

export type LearningContentItem = {
  contentId: string;
  englishText: string;
  chineseText: string;
  phonetic: string | null;
};

export type GeneratedScene = {
  sceneId: string;
  title: string;
  label: SceneLabel;
  background: string;
  aiRole: string;
  userRole: string;
  learningGoal: string;
  estimatedMinutes: number;
  wordList: LearningContentItem[];
  phraseList: LearningContentItem[];
  sentenceList: LearningContentItem[];
  scenePrompt: string;
};

export type SceneFlow = {
  sceneId: string;
  stage: SceneFlowStage;
  completed: boolean;
};

export type PhonemeScore = {
  expectedPhoneme: string;
  actualPhoneme: string;
  score: number;
};

export type WordPronunciationScore = {
  word: string;
  wordScore: number;
  phonemes: PhonemeScore[];
};

export type SentenceEvaluation = {
  overallScore: number;
  passed: boolean;
  words: WordPronunciationScore[];
};

type ApiRequester = {
  request(path: string, options?: ApiRequestOptions): Promise<unknown>;
};

function isGeneratedScene(value: unknown): value is GeneratedScene {
  if (!value || typeof value !== 'object') return false;
  const scene = value as Partial<GeneratedScene>;
  return Boolean(
    scene.sceneId?.trim() &&
      scene.label?.trim() &&
      Array.isArray(scene.wordList) &&
      scene.wordList.length &&
      Array.isArray(scene.phraseList) &&
      scene.phraseList.length &&
      Array.isArray(scene.sentenceList) &&
      scene.sentenceList.length,
  );
}

export function createWavUploadFile(wavUri: string) {
  return new File(wavUri);
}

export class SceneService {
  constructor(private readonly client: ApiRequester) {}

  async generate(sceneInput: string, userPreference: string | null = null) {
    const task = await this.client.request('/api/custom-scenes/generate', {
      method: 'POST',
      body: JSON.stringify({ sceneInput: sceneInput.trim(), userPreference }),
      timeoutMs: 60_000,
    });
    if (!task || typeof task !== 'object' || !('taskId' in task) ||
        typeof task.taskId !== 'string' || !task.taskId.trim()) {
      throw new Error('场景生成响应缺少 taskId');
    }
    const taskId = task.taskId;
    const scene = await waitForAsyncTask<GeneratedScene>(
      task,
      () => this.client.request(
        `/api/custom-scenes/generation-tasks/${encodeURIComponent(taskId)}`,
      ),
      '场景生成',
    );
    if (!isGeneratedScene(scene)) {
      throw new Error('场景生成内容不完整，请重新生成');
    }
    return scene;
  }

  createFlow(sceneId: string) {
    return this.client.request('/api/custom-scenes/flows', {
      method: 'POST',
      body: JSON.stringify({ sceneId }),
    }) as Promise<SceneFlow>;
  }

  getContent(sceneId: string, stage?: SceneFlowStage) {
    const query = stage ? `?stage=${encodeURIComponent(stage)}` : '';
    return this.client.request(
      `/api/custom-scenes/flows/${encodeURIComponent(sceneId)}/content${query}`,
    ) as Promise<LearningContentItem[]>;
  }

  advanceStage(sceneId: string, stage: SceneFlowStage) {
    return this.client.request('/api/custom-scenes/flows/advance', {
      method: 'POST',
      body: JSON.stringify({ sceneId, stage }),
    }) as Promise<SceneFlow>;
  }

  evaluateSentence(sceneId: string, sentenceId: string, wavUri: string) {
    const body = new FormData();
    // Expo SDK 57's fetch implementation accepts Blob-compatible files with a
    // `bytes()` method. React Native's legacy `{ uri, name, type }` descriptor
    // is rejected before the request reaches the backend.
    body.append('audio', createWavUploadFile(wavUri));
    return this.client.request(
      `/api/custom-scenes/${encodeURIComponent(sceneId)}/sentences/${encodeURIComponent(sentenceId)}/evaluation`,
      { method: 'POST', body, timeoutMs: 60_000 },
    ) as Promise<SentenceEvaluation>;
  }
}
