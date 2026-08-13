import { RealtimeSessionApi } from '../RealtimeSessionApi';

describe('RealtimeSessionApi', () => {
  it('starts free chat with the existing Java endpoint and body', async () => {
    const client = { request: jest.fn(async () => ({ sessionId: 'session-1' })) };
    const api = new RealtimeSessionApi(client);

    await api.start({
      sceneId: null,
      offerSdp: 'offer-sdp',
      provider: 'QWEN',
      model: 'qwen3.5-omni-flash-realtime',
      voice: 'Harvey',
      translationEnabled: true,
    });

    expect(client.request).toHaveBeenCalledWith('/api/scene-sessions', {
      method: 'POST',
      body: expect.stringContaining('"voice":"Harvey"'),
      timeoutMs: 20_000,
    });
  });

  it('starts scene conversation with the encoded scene endpoint', async () => {
    const client = { request: jest.fn(async () => ({ sessionId: 'session-1' })) };
    const api = new RealtimeSessionApi(client);

    await api.start({
      sceneId: 'scene/with space',
      offerSdp: 'offer-sdp',
      provider: 'QWEN',
      model: 'qwen3.5-omni-flash-realtime',
      voice: 'Harvey',
      translationEnabled: true,
    });

    expect(client.request).toHaveBeenCalledWith(
      '/api/custom-scenes/scene%2Fwith%20space/sessions',
      expect.objectContaining({ method: 'POST' }),
    );
  });

  it('starts ielts session with voiceId body field', async () => {
    const client = { request: jest.fn(async () => ({ sessionId: 'session-1' })) };
    const api = new RealtimeSessionApi(client);

    await api.start({
      sceneId: null,
      ieltsId: 'ielts-20',
      offerSdp: 'offer-sdp',
      provider: 'QWEN',
      model: 'qwen3.5-omni-flash-realtime',
      voice: 'Harvey',
      translationEnabled: true,
    });

    expect(client.request).toHaveBeenCalledWith('/api/ielts/ielts-20/sessions', {
      method: 'POST',
      body: expect.stringContaining('"voiceId":"Harvey"'),
      timeoutMs: 20_000,
    });
  });
});
