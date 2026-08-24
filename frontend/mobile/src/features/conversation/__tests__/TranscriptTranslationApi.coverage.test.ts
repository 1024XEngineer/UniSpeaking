import { TranscriptTranslationApi } from '../TranscriptTranslationApi';

describe('TranscriptTranslationApi', () => {
  it('posts free-chat and scene translation requests', async () => {
    const request = jest.fn().mockResolvedValue({ translatedText: '你好' });
    const api = new TranscriptTranslationApi({ request });
    await expect(api.translateFreeChat('session/1', 'hello')).resolves.toBe('你好');
    await expect(api.translateScene('scene/1', 'hello')).resolves.toBe('你好');
    expect(request).toHaveBeenCalledWith('/api/scene-sessions/session%2F1/translations', expect.objectContaining({ method: 'POST' }));
    expect(request).toHaveBeenCalledWith('/api/custom-scenes/scene%2F1/translations', expect.objectContaining({ method: 'POST' }));
  });
});
