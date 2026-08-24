const files: any[] = [];
jest.mock('expo-file-system', () => ({
  File: jest.fn((_parent: string, name: string) => {
    const file = { uri: `file://${name}`, exists: false, size: 0, create: jest.fn(), write: jest.fn(), delete: jest.fn() };
    files.push(file);
    return file;
  }),
  Paths: { document: 'document', cache: 'cache' },
}));

import { InterviewAssetService, InterviewRecordingClient } from '../InterviewAssetService';

describe('Interview asset boundaries', () => {
  it('validates asset and report response shapes', async () => {
    const request = jest.fn()
      .mockResolvedValueOnce([{ sceneId: 'scene-1', jobTitle: 'PM' }])
      .mockResolvedValueOnce({ status: 'FAILED', failureReason: 'timeout' })
      .mockResolvedValueOnce({ invalid: true });
    const service = new InterviewAssetService({ request });
    await expect(service.listAssets()).resolves.toHaveLength(1);
    await expect(service.getReport('scene-1', 'session-1')).resolves.toMatchObject({ status: 'FAILED' });
    await expect(service.listAssets()).rejects.toThrow('资产格式不正确');
  });

  it('downloads valid recordings, reuses local files and reports bad responses', async () => {
    const tokenStore = { get: jest.fn().mockResolvedValue('token') };
    const fetchImpl = jest.fn().mockResolvedValue({ ok: true, status: 200, arrayBuffer: async () => new Uint8Array(44).buffer });
    const downloaded = await new InterviewRecordingClient('https://api.example.com/', tokenStore, fetchImpl).download('scene', 'session-2');
    expect(fetchImpl).toHaveBeenCalledWith(expect.stringContaining('/api/interview-scenes/scene/sessions/session-2/recording'), expect.anything());
    downloaded.remove();
    await expect(new InterviewRecordingClient('https://api.example.com', tokenStore, jest.fn().mockResolvedValue({ ok: false, status: 404 })).download('scene', 'x')).rejects.toThrow('暂无可播放');
  });
});
