import type { TokenStore } from '@/infrastructure/auth/SecureTokenStore';

export type SpeechAsset = {
  uri: string;
  remove(): void;
};

type SpeechClientOptions = {
  baseUrl: string;
  tokenStore: Pick<TokenStore, 'get'>;
  fetchImpl?: typeof fetch;
  writeWav?: (bytes: Uint8Array) => Promise<SpeechAsset>;
};

function createWavAsset(bytes: Uint8Array): SpeechAsset {
  // eslint-disable-next-line @typescript-eslint/no-require-imports
  const { File, Paths } = require('expo-file-system') as typeof import('expo-file-system');
  const file = new File(
    Paths.cache,
    `unispeaking-tts-${Date.now()}-${Math.random().toString(36).slice(2, 8)}.wav`,
  );
  file.create({ overwrite: true });
  file.write(bytes);
  return {
    uri: file.uri,
    remove: () => {
      if (file.exists) file.delete();
    },
  };
}

export class SceneSpeechClient {
  private readonly baseUrl: string;
  private readonly fetchImpl: typeof fetch;
  private readonly writeWav: (bytes: Uint8Array) => Promise<SpeechAsset>;

  constructor(private readonly options: SpeechClientOptions) {
    this.baseUrl = options.baseUrl.replace(/\/+$/, '');
    this.fetchImpl = options.fetchImpl ?? fetch;
    this.writeWav = options.writeWav ?? (async (bytes) => createWavAsset(bytes));
  }

  async synthesize(sceneId: string, text: string, model: string | null = null) {
    const token = await this.options.tokenStore.get();
    const response = await this.fetchImpl(
      `${this.baseUrl}/api/custom-scenes/${encodeURIComponent(sceneId)}/speech`,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body: JSON.stringify({ text: text.trim(), model }),
      },
    );
    if (!response.ok) {
      let message = `语音生成失败（${response.status}）`;
      if ((response.headers.get('content-type') ?? '').includes('application/json')) {
        const body = (await response.json()) as { message?: string; code?: string };
        message = body.message || body.code || message;
      }
      throw new Error(message);
    }
    return this.writeWav(new Uint8Array(await response.arrayBuffer()));
  }
}

type NativeAudioPlayer = {
  play(): void;
  remove(): void;
};

type TtsPlayerOptions = {
  speechClient: Pick<SceneSpeechClient, 'synthesize'>;
  createPlayer?: (uri: string) => NativeAudioPlayer;
};

function createNativePlayer(uri: string): NativeAudioPlayer {
  // eslint-disable-next-line @typescript-eslint/no-require-imports
  const { createAudioPlayer } = require('expo-audio') as typeof import('expo-audio');
  return createAudioPlayer(uri);
}

export class TtsPlayer {
  private readonly createPlayer: (uri: string) => NativeAudioPlayer;
  private player: NativeAudioPlayer | null = null;
  private asset: SpeechAsset | null = null;

  constructor(private readonly options: TtsPlayerOptions) {
    this.createPlayer = options.createPlayer ?? createNativePlayer;
  }

  async play(sceneId: string, text: string) {
    this.stop();
    const asset = await this.options.speechClient.synthesize(sceneId, text);
    const player = this.createPlayer(asset.uri);
    this.asset = asset;
    this.player = player;
    player.play();
  }

  stop() {
    const player = this.player;
    const asset = this.asset;
    this.player = null;
    this.asset = null;
    player?.remove();
    asset?.remove();
  }
}
