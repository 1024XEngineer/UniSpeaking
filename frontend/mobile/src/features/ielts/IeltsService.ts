import type { ApiRequestOptions } from '@/infrastructure/http/ApiClient';
import { waitForAsyncTask } from '@/infrastructure/http/waitForAsyncTask';
import { createWavUploadFile } from '@/features/scenes/SceneService';

import type {
  IeltsEvaluationHistoryItem,
  IeltsEvaluationResult,
  IeltsGeneration,
  IeltsMode,
  IeltsPart,
  IeltsPart2Event,
  IeltsPart2State,
  IeltsSceneFlow,
  IeltsSettings,
  IeltsTopicSearchResult,
  IeltsTraining,
  IeltsDialogueState,
} from './types';

type ApiRequester = {
  request(path: string, options?: ApiRequestOptions): Promise<unknown>;
};

export type IeltsTopicQuery = {
  part: IeltsPart;
  category?: string | null;
  keyword?: string | null;
  page?: number;
  pageSize?: number;
};

export class IeltsService {
  constructor(private readonly client: ApiRequester) {}

  getSettings() {
    return this.client.request('/api/ielts/settings') as Promise<IeltsSettings>;
  }

  updateSettings(input: { targetScore?: number | null; examinerId?: string | null }) {
    return this.client.request('/api/ielts/settings', {
      method: 'PUT',
      body: JSON.stringify(input),
    }) as Promise<IeltsSettings>;
  }

  searchTopics(query: IeltsTopicQuery) {
    const params = new URLSearchParams({
      part: query.part,
      page: String(query.page ?? 1),
      pageSize: String(query.pageSize ?? 10),
    });
    if (query.category) params.set('category', query.category);
    if (query.keyword?.trim()) params.set('keyword', query.keyword.trim());
    return this.client.request(`/api/ielts/topics?${params.toString()}`) as Promise<IeltsTopicSearchResult>;
  }

  getTraining(part: IeltsPart, topicId?: string | null) {
    const params = new URLSearchParams({ part });
    if (topicId) params.set('topicId', topicId);
    return this.client.request(`/api/ielts/training?${params.toString()}`) as Promise<IeltsTraining>;
  }

  generateScene(input: {
    mode: IeltsMode;
    part?: IeltsPart | null;
    topicId?: string | null;
  }) {
    return this.client.request('/api/ielts/generate', {
      method: 'POST',
      body: JSON.stringify(input),
      timeoutMs: 60_000,
    }) as Promise<IeltsGeneration>;
  }

  createFlow(sceneId: string) {
    return this.client.request('/api/ielts/flows', {
      method: 'POST',
      body: JSON.stringify({ sceneId }),
    }) as Promise<IeltsSceneFlow>;
  }

  async generateEvaluation(ieltsId: string, sessionId: string) {
    const path = `/api/ielts/${encodeURIComponent(ieltsId)}/sessions/${encodeURIComponent(sessionId)}/evaluation`;
    const task = await this.client.request(path, {
      method: 'POST',
      timeoutMs: 90_000,
    });
    return waitForAsyncTask<IeltsEvaluationResult>(
      task,
      () => this.client.request(path),
      'IELTS 评分',
    );
  }

  getEvaluationHistory() {
    return this.client.request('/api/ielts/evaluations') as Promise<IeltsEvaluationHistoryItem[]>;
  }

  getDialogueState(ieltsId: string, sessionId: string) {
    return this.client.request(
      `/api/ielts/${encodeURIComponent(ieltsId)}/sessions/${encodeURIComponent(sessionId)}/state`,
    ) as Promise<IeltsDialogueState>;
  }

  advanceDialogueState(
    ieltsId: string,
    sessionId: string,
    turnNo: number,
    timedOut = false,
  ) {
    const suffix = timedOut ? '?timedOut=true' : '';
    return this.client.request(
      `/api/ielts/${encodeURIComponent(ieltsId)}/sessions/${encodeURIComponent(sessionId)}/turns/${turnNo}/state${suffix}`,
      { method: 'POST' },
    ) as Promise<IeltsDialogueState>;
  }

  getPart2State(ieltsId: string, sessionId: string) {
    return this.client.request(
      `/api/ielts/${encodeURIComponent(ieltsId)}/sessions/${encodeURIComponent(sessionId)}/part2/state`,
    ) as Promise<IeltsPart2State>;
  }

  advancePart2State(ieltsId: string, sessionId: string, event: IeltsPart2Event) {
    return this.client.request(
      `/api/ielts/${encodeURIComponent(ieltsId)}/sessions/${encodeURIComponent(sessionId)}/part2/state`,
      {
        method: 'POST',
        body: JSON.stringify({ event }),
      },
    ) as Promise<IeltsPart2State>;
  }

  evaluateTurn(
    ieltsId: string,
    sessionId: string,
    turnNo: number,
    transcript: string,
    wavUri?: string | null,
  ) {
    const body = new FormData();
    body.append('transcript', transcript);
    if (wavUri) {
      body.append('audio', createWavUploadFile(wavUri));
    }
    return this.client.request(
      `/api/ielts/${encodeURIComponent(ieltsId)}/sessions/${encodeURIComponent(sessionId)}/turns/${turnNo}/evaluation`,
      { method: 'POST', body },
    );
  }
}
