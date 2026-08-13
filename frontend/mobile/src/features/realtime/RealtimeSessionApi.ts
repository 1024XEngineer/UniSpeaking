import type { ApiRequestOptions } from '@/infrastructure/http/ApiClient';

import type {
  RealtimeSessionStartRequest,
  RealtimeSessionStartResponse,
} from './RealtimeSessionController';

type ApiRequester = {
  request(path: string, options?: ApiRequestOptions): Promise<unknown>;
};

export class RealtimeSessionApi {
  constructor(private readonly client: ApiRequester) {}

  start(request: RealtimeSessionStartRequest) {
    const { sceneId, ieltsId, voice, ...rest } = request;
    const path = ieltsId
      ? `/api/ielts/${encodeURIComponent(ieltsId)}/sessions`
      : sceneId
        ? `/api/custom-scenes/${encodeURIComponent(sceneId)}/sessions`
        : '/api/scene-sessions';
    const body = ieltsId
      ? { ...rest, voiceId: voice, translationEnabled: request.translationEnabled }
      : { ...rest, voice, translationEnabled: request.translationEnabled };
    return this.client.request(path, {
      method: 'POST',
      body: JSON.stringify(body),
      timeoutMs: 20_000,
    }) as Promise<RealtimeSessionStartResponse>;
  }
}
