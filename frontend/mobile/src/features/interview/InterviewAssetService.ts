import type { ApiRequestOptions } from '@/infrastructure/http/ApiClient';
import type { TokenStore } from '@/infrastructure/auth/SecureTokenStore';
import { File, Paths } from 'expo-file-system';

import type { InterviewAssetItem, InterviewReportResponse } from './InterviewSessionApi';

type ApiRequester = {
  request(path: string, options?: ApiRequestOptions): Promise<unknown>;
};

export type InterviewAssetRecord = InterviewAssetItem;

function isAsset(value: unknown): value is InterviewAssetItem {
  if (!value || typeof value !== 'object') return false;
  const item = value as Partial<InterviewAssetItem>;
  return typeof item.sceneId === 'string' && typeof item.jobTitle === 'string';
}

function isAssets(value: unknown): value is InterviewAssetItem[] {
  return Array.isArray(value) && value.every(isAsset);
}

export class InterviewAssetService {
  constructor(private readonly client: ApiRequester) {}

  async listAssets(): Promise<InterviewAssetRecord[]> {
    const value = await this.client.request('/api/interview-scenes/assets', { timeoutMs: 15_000 });
    if (!isAssets(value)) throw new Error('面试学习资产格式不正确');
    return value;
  }

  async getReport(sceneId: string, sessionId: string): Promise<InterviewReportResponse> {
    const value = await this.client.request(
      `/api/interview-scenes/${encodeURIComponent(sceneId)}/sessions/${encodeURIComponent(sessionId)}/report`,
      { timeoutMs: 15_000 },
    );
    if (!value || typeof value !== 'object' || !['PROCESSING', 'COMPLETED', 'FAILED'].includes((value as { status?: unknown }).status as string)) {
      throw new Error('面试报告格式不正确');
    }
    return value as InterviewReportResponse;
  }
}

export type InterviewRecordingAsset = { uri: string; remove(): void };

export class InterviewRecordingClient {
  constructor(
    private readonly baseUrl: string,
    private readonly tokenStore: Pick<TokenStore, 'get'>,
    private readonly fetchImpl: typeof fetch = fetch,
  ) {}

  async download(sceneId: string, sessionId: string): Promise<InterviewRecordingAsset> {
    const local = new File(Paths.document, `interview-full-${sessionId}.wav`);
    if (local.exists && local.size >= 44) {
      return { uri: local.uri, remove: () => undefined };
    }
    const token = await this.tokenStore.get();
    const response = await this.fetchImpl(`${this.baseUrl.replace(/\/+$/, '')}/api/interview-scenes/${encodeURIComponent(sceneId)}/sessions/${encodeURIComponent(sessionId)}/recording`, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    });
    if (!response.ok) throw new Error(response.status === 404 ? '上一次面试暂无可播放的完整录音' : `完整录音读取失败（${response.status}）`);
    const bytes = new Uint8Array(await response.arrayBuffer());
    if (bytes.length < 44) throw new Error('完整录音文件无效');
    const file = new File(Paths.cache, `interview-recording-${sessionId}-${Date.now()}.wav`);
    file.create({ overwrite: true });
    file.write(bytes);
    return { uri: file.uri, remove: () => { if (file.exists) file.delete(); } };
  }
}
