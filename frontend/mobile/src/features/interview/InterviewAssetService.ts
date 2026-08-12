import type { ApiRequestOptions } from '@/infrastructure/http/ApiClient';

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
