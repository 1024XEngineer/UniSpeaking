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
  pause(): void;
  remove(): void;
  volume?: number;
};

type TtsPlayerOptions = {
  speechClient: Pick<SceneSpeechClient, 'synthesize'>;
  createPlayer?: (uri: string) => NativeAudioPlayer;
  preparePlayback?: () => Promise<void>;
};

function createNativePlayer(uri: string): NativeAudioPlayer {
  // eslint-disable-next-line @typescript-eslint/no-require-imports
  const { createAudioPlayer } = require('expo-audio') as typeof import('expo-audio');
  return createAudioPlayer(uri, { downloadFirst: true });
}

async function prepareNativePlayback() {
  // eslint-disable-next-line @typescript-eslint/no-require-imports
  const { setAudioModeAsync } = require('expo-audio') as typeof import('expo-audio');
  await setAudioModeAsync({
    allowsRecording: false,
    interruptionMode: 'doNotMix',
    playsInSilentMode: true,
    shouldRouteThroughEarpiece: false,
  });
}

export class TtsPlayer {
  private readonly createPlayer: (uri: string) => NativeAudioPlayer;
  private readonly preparePlayback: () => Promise<void>;
  private player: NativeAudioPlayer | null = null;
  private asset: SpeechAsset | null = null;
  private requestVersion = 0;

  constructor(private readonly options: TtsPlayerOptions) {
    this.createPlayer = options.createPlayer ?? createNativePlayer;
    this.preparePlayback = options.preparePlayback ?? prepareNativePlayback;
  }

  async play(sceneId: string, text: string) {
    this.stop();
    const requestVersion = this.requestVersion;
    const asset = await this.options.speechClient.synthesize(sceneId, text);
    if (requestVersion !== this.requestVersion) {
      asset.remove();
      return;
    }
    try {
      await this.preparePlayback();
    } catch (error) {
      asset.remove();
      throw error;
    }
    if (requestVersion !== this.requestVersion) {
      asset.remove();
      return;
    }
    const player = this.createPlayer(asset.uri);
    // Learning-expression playback must be audible through the device speaker.
    if ('volume' in player) player.volume = 1;
    this.asset = asset;
    this.player = player;
    player.play();
  }

  stop() {
    this.requestVersion += 1;
    const player = this.player;
    const asset = this.asset;
    this.player = null;
    this.asset = null;
    try {
      player?.pause();
    } finally {
      player?.remove();
      asset?.remove();
    }
  }
}
