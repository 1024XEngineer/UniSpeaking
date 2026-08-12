import { InterviewAssetService } from '../InterviewAssetService';

describe('InterviewAssetService', () => {
  it('loads real interview assets', async () => {
    const client = { request: jest.fn().mockResolvedValue([{ sceneId: 'scene-1', jobTitle: 'PM', difficulty: 'STANDARD', latestSessionId: null, latestReportStatus: null, latestOverallScore: null, latestPracticedAt: null, practiceCount: 0, createdAt: '2026-01-01' }]) };
    await expect(new InterviewAssetService(client).listAssets()).resolves.toHaveLength(1);
    expect(client.request).toHaveBeenCalledWith('/api/interview-scenes/assets', expect.anything());
  });

  it('loads and validates a report', async () => {
    const report = { status: 'COMPLETED', report: { overallScore: 81 } };
    const client = { request: jest.fn().mockResolvedValue(report) };
    await expect(new InterviewAssetService(client).getReport('scene/1', 'session/1')).resolves.toEqual(report);
    expect(client.request).toHaveBeenCalledWith('/api/interview-scenes/scene%2F1/sessions/session%2F1/report', expect.anything());
  });

  it('rejects malformed assets and reports', async () => {
    const client = { request: jest.fn().mockResolvedValue([{ sceneId: 1 }]) };
    await expect(new InterviewAssetService(client).listAssets()).rejects.toThrow('格式');
    client.request.mockResolvedValue({ status: 'UNKNOWN' });
    await expect(new InterviewAssetService(client).getReport('scene', 'session')).rejects.toThrow('格式');
  });
});
