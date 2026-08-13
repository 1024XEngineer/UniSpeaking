import type { ApiRequestOptions } from '@/infrastructure/http/ApiClient';
import { createWavUploadFile } from './SceneService';

export type ScenarioDialogueState = {
  sceneId: string;
  sessionId: string;
  stage: string;
  effectiveUserTurns: number;
  maximumUserTurns: number;
  outcomes: unknown[];
  completed: boolean;
  completionReason: string | null;
  controlInstruction: string;
  warning: string | null;
};

export type DialogueReport = {
  accuracyScore: number;
  fluencyScore: number;
  grammarScore: number;
  vocabularyScore: number;
  naturalnessScore: number;
  finalScore: number;
  summary: string;
  strengths: string[];
  improvements: string[];
};

export type DialogueCompletion = {
  sceneId: string;
  sessionId: string;
  stopTime: string;
  evaluation?: DialogueReport;
  state?: ScenarioDialogueState;
};

type ApiRequester = {
  request(path: string, options?: ApiRequestOptions): Promise<unknown>;
};

export class SceneDialogueApi {
  constructor(
    private readonly client: ApiRequester,
    private readonly sceneId: string,
  ) {}

  advanceState(sessionId: string, turnNo: number, transcript: string) {
    return this.client.request(
      `${this.turnPath(sessionId, turnNo)}/state`,
      {
        method: 'POST',
        body: JSON.stringify({ transcript }),
      },
    ) as Promise<ScenarioDialogueState>;
  }

  evaluateTurn(
    sessionId: string,
    turnNo: number,
    transcript: string,
    wavUri?: string | null,
  ) {
    const body = new FormData();
    body.append('transcript', transcript);
    if (wavUri) body.append('audio', createWavUploadFile(wavUri));
    return this.client.request(
      `${this.turnPath(sessionId, turnNo)}/evaluation`,
      { method: 'POST', body },
    );
  }

  complete(sessionId: string, stopTime: string) {
    return this.client.request(
      `${this.sessionPath(sessionId)}/complete`,
      {
        method: 'POST',
        body: JSON.stringify({ stopTime }),
        timeoutMs: 90_000,
      },
    ) as Promise<DialogueCompletion>;
  }

  getReport(sessionId: string) {
    return this.client.request(
      `${this.sessionPath(sessionId)}/evaluation`,
    ) as Promise<DialogueReport>;
  }

  private sessionPath(sessionId: string) {
    return `/api/custom-scenes/${encodeURIComponent(this.sceneId)}/sessions/${encodeURIComponent(sessionId)}`;
  }

  private turnPath(sessionId: string, turnNo: number) {
    return `${this.sessionPath(sessionId)}/turns/${turnNo}`;
  }
}
