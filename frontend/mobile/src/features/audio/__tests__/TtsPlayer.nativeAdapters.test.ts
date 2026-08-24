const mockAssetFile = {
  uri: 'file:///cache/unispeaking-tts.wav',
  exists: true,
  create: jest.fn(),
  write: jest.fn(),
  delete: jest.fn(),
};
const mockFile = jest.fn(() => mockAssetFile);
const mockNativePlayer = { play: jest.fn(), pause: jest.fn(), remove: jest.fn(), volume: 0 };
const mockCreateAudioPlayer = jest.fn(() => mockNativePlayer);
const mockSetAudioModeAsync = jest.fn(async () => undefined);

jest.mock('expo-file-system', () => ({
  File: mockFile,
  Paths: { cache: 'file:///cache' },
}));
jest.mock('expo-audio', () => ({
  createAudioPlayer: mockCreateAudioPlayer,
  setAudioModeAsync: mockSetAudioModeAsync,
}));

import { SceneSpeechClient, TtsPlayer } from '../TtsPlayer';

describe('TTS native adapters', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockAssetFile.exists = true;
  });

  it('writes default synthesized WAV assets into the cache and deletes them', async () => {
    const client = new SceneSpeechClient({
      baseUrl: 'https://api.example.test',
      tokenStore: { get: async () => null },
      fetchImpl: jest.fn(async () => ({
        ok: true,
        arrayBuffer: async () => new Uint8Array([82, 73, 70, 70]).buffer,
      }) as Response),
    });

    const asset = await client.synthesize('scene', 'hello');

    expect(mockFile).toHaveBeenCalledWith('file:///cache', expect.stringMatching(/^unispeaking-tts-.*\.wav$/));
    expect(mockAssetFile.create).toHaveBeenCalledWith({ overwrite: true });
    expect(mockAssetFile.write).toHaveBeenCalledWith(expect.objectContaining({ byteLength: 4 }));
    asset.remove();
    expect(mockAssetFile.delete).toHaveBeenCalledTimes(1);
  });

  it('uses Expo audio defaults when no native player adapters are supplied', async () => {
    const asset = { uri: 'file:///speech.wav', remove: jest.fn() };
    const player = new TtsPlayer({ speechClient: { synthesize: jest.fn().mockResolvedValue(asset) } });

    await player.play('scene', 'hello');

    expect(mockSetAudioModeAsync).toHaveBeenCalledWith({
      allowsRecording: false,
      interruptionMode: 'doNotMix',
      playsInSilentMode: true,
      shouldRouteThroughEarpiece: false,
    });
    expect(mockCreateAudioPlayer).toHaveBeenCalledWith('file:///speech.wav', { downloadFirst: true });
    expect(mockNativePlayer.volume).toBe(1);
    expect(mockNativePlayer.play).toHaveBeenCalledTimes(1);
  });
});
