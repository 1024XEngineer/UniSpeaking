import { AuthenticatedMediaClient } from '../AuthenticatedMediaClient';

const mockFile = { uri: 'cache://audio.wav', exists: true, create: jest.fn(), write: jest.fn(), delete: jest.fn() };
jest.mock('expo-file-system', () => ({ File: jest.fn(() => mockFile), Paths: { cache: 'cache' } }));

describe('AuthenticatedMediaClient', () => {
  it('downloads relative media with a bearer token and caches it', async () => {
    const fetchImpl = jest.fn().mockResolvedValue({ ok: true, status: 200, arrayBuffer: async () => new Uint8Array([1, 2]).buffer });
    const coordinator = { getAccessToken: jest.fn().mockResolvedValue('token-1'), refreshAccessToken: jest.fn() } as any;
    const client = new AuthenticatedMediaClient('https://api.example.com/', { get: jest.fn() }, fetchImpl, coordinator);
    const file = await client.download('/media/audio.wav');
    expect(fetchImpl).toHaveBeenCalledWith('https://api.example.com/media/audio.wav', { headers: { Authorization: 'Bearer token-1' } });
    expect(file.uri).toBe('cache://audio.wav');
    file.remove();
    expect(mockFile.delete).toHaveBeenCalled();
  });

  it('refreshes after an unauthorized relative request and omits auth for absolute URLs', async () => {
    const fetchImpl = jest.fn()
      .mockResolvedValueOnce({ ok: false, status: 401, arrayBuffer: async () => new ArrayBuffer(0) })
      .mockResolvedValueOnce({ ok: true, status: 200, arrayBuffer: async () => new ArrayBuffer(0) });
    const coordinator = { getAccessToken: jest.fn().mockResolvedValue('old'), refreshAccessToken: jest.fn().mockResolvedValue('new') } as any;
    const client = new AuthenticatedMediaClient('https://api.example.com', { get: jest.fn() }, fetchImpl, coordinator);
    await client.download('/media/audio');
    expect(fetchImpl.mock.calls[1][1]).toEqual({ headers: { Authorization: 'Bearer new' } });

    const absoluteFetch = jest.fn().mockResolvedValue({ ok: true, status: 200, arrayBuffer: async () => new ArrayBuffer(0) });
    await new AuthenticatedMediaClient('https://api.example.com', { get: jest.fn() }, absoluteFetch, coordinator).download('https://cdn.example.com/a.mp3');
    expect(absoluteFetch.mock.calls[0][1]).toEqual({ headers: {} });
  });

  it('rejects unsuccessful downloads', async () => {
    const client = new AuthenticatedMediaClient('https://api.example.com', { get: jest.fn() }, jest.fn().mockResolvedValue({ ok: false, status: 500 }), { getAccessToken: jest.fn().mockResolvedValue(null), refreshAccessToken: jest.fn() } as any);
    await expect(client.download('/missing')).rejects.toThrow('录音加载失败（500）');
  });
});
