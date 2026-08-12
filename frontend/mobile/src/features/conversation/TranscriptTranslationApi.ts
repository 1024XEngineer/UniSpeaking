import type { ApiRequestOptions } from '@/infrastructure/http/ApiClient';
import { SecureTokenStore } from '@/infrastructure/auth/SecureTokenStore';
import { getRuntimeConfig } from '@/infrastructure/config/runtimeConfig';
import { ApiClient } from '@/infrastructure/http/ApiClient';

type ApiRequester = {
  request(path: string, options?: ApiRequestOptions): Promise<unknown>;
};

type TranslationResponse = {
  translatedText: string;
};

export class TranscriptTranslationApi {
  constructor(private readonly client: ApiRequester) {}

  async translateFreeChat(sessionId: string, text: string) {
    const response = await this.translate(
      `/api/scene-sessions/${encodeURIComponent(sessionId)}/translations`,
      text,
    );
    return response.translatedText;
  }

  async translateScene(sceneId: string, text: string) {
    const response = await this.translate(
      `/api/custom-scenes/${encodeURIComponent(sceneId)}/translations`,
      text,
    );
    return response.translatedText;
  }

  private translate(path: string, text: string) {
    return this.client.request(path, {
      method: 'POST',
      body: JSON.stringify({ text }),
      timeoutMs: 30_000,
    }) as Promise<TranslationResponse>;
  }
}

export function createTranscriptTranslationApi() {
  return new TranscriptTranslationApi(new ApiClient({
    baseUrl: getRuntimeConfig().backendUrl,
    tokenStore: new SecureTokenStore(),
  }));
}
