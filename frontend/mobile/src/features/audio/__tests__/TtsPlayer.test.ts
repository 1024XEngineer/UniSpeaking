import { SceneSpeechClient, TtsPlayer } from '../TtsPlayer';

function wavResponse(bytes: number[], status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: { get: () => 'audio/wav' },
    arrayBuffer: jest.fn(async () => new Uint8Array(bytes).buffer),
    json: jest.fn(async () => ({ message: 'TTS 生成失败' })),
  } as unknown as Response;
}

describe('SceneSpeechClient', () => {
  it('downloads authenticated backend WAV into a removable local asset', async () => {
    const remove = jest.fn();
    const writeWav = jest.fn(async () => ({
      uri: 'file:///tts.wav',
      remove,
    }));
    const fetchImpl = jest.fn(async () => wavResponse([82, 73, 70, 70]));
    const client = new SceneSpeechClient({
      baseUrl: 'http://127.0.0.1:8080/',
      tokenStore: { get: async () => 'jwt-token' },
      fetchImpl,
      writeWav,
    });

    await expect(client.synthesize('scene/1', 'Hello.')).resolves.toEqual({
      uri: 'file:///tts.wav',
      remove,
    });
    expect(fetchImpl).toHaveBeenCalledWith(
      'http://127.0.0.1:8080/api/custom-scenes/scene%2F1/speech',
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: 'Bearer jwt-token',
        },
        body: JSON.stringify({ text: 'Hello.', model: null }),
      },
    );
    expect(writeWav).toHaveBeenCalledWith(
      expect.objectContaining({ byteLength: 4 }),
    );
  });
});

describe('TtsPlayer', () => {
  it('replaces and releases the previous player and cached WAV', async () => {
    const firstAsset = { uri: 'file:///first.wav', remove: jest.fn() };
    const secondAsset = { uri: 'file:///second.wav', remove: jest.fn() };
    const speechClient = {
      synthesize: jest
        .fn()
        .mockResolvedValueOnce(firstAsset)
        .mockResolvedValueOnce(secondAsset),
    };
    const firstPlayer = { play: jest.fn(), remove: jest.fn() };
    const secondPlayer = { play: jest.fn(), remove: jest.fn() };
    const createPlayer = jest
      .fn()
      .mockReturnValueOnce(firstPlayer)
      .mockReturnValueOnce(secondPlayer);
    const player = new TtsPlayer({ speechClient, createPlayer });

    await player.play('scene-1', 'First');
    await player.play('scene-1', 'Second');

    expect(firstPlayer.remove).toHaveBeenCalledTimes(1);
    expect(firstAsset.remove).toHaveBeenCalledTimes(1);
    expect(secondPlayer.play).toHaveBeenCalledTimes(1);
    player.stop();
    player.stop();
    expect(secondPlayer.remove).toHaveBeenCalledTimes(1);
    expect(secondAsset.remove).toHaveBeenCalledTimes(1);
  });
});
