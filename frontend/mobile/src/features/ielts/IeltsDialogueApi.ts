import type { ApiRequestOptions } from '@/infrastructure/http/ApiClient';
import { createWavUploadFile } from '@/features/scenes/SceneService';

import type {
  IeltsDialogueState,
  IeltsPart2Event,
  IeltsPart2State,
} from './types';

type ApiRequester = {
  request(path: string, options?: ApiRequestOptions): Promise<unknown>;
};

export class IeltsDialogueApi {
  constructor(
    private readonly client: ApiRequester,
    private readonly ieltsId: string,
  ) {}

  advanceState(sessionId: string, turnNo: number, timedOut = false) {
    const suffix = timedOut ? '?timedOut=true' : '';
    return this.client.request(
      `${this.sessionPath(sessionId)}/turns/${turnNo}/state${suffix}`,
      { method: 'POST' },
    ) as Promise<IeltsDialogueState>;
  }

  getDialogueState(sessionId: string) {
    return this.client.request(
      `${this.sessionPath(sessionId)}/state`,
    ) as Promise<IeltsDialogueState>;
  }

  getPart2State(sessionId: string) {
    return this.client.request(
      `${this.sessionPath(sessionId)}/part2/state`,
    ) as Promise<IeltsPart2State>;
  }

  evaluateTurn(
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
      `${this.sessionPath(sessionId)}/turns/${turnNo}/evaluation`,
      { method: 'POST', body },
    );
  }

  advancePart2State(sessionId: string, event: IeltsPart2Event) {
    return this.client.request(`${this.sessionPath(sessionId)}/part2/state`, {
      method: 'POST',
      body: JSON.stringify({ event }),
    }) as Promise<IeltsPart2State>;
  }

  private sessionPath(sessionId: string) {
    return `/api/ielts/${encodeURIComponent(this.ieltsId)}/sessions/${encodeURIComponent(sessionId)}`;
  }
}
