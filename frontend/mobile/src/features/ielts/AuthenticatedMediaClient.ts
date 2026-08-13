import type { TokenStore } from '@/infrastructure/auth/SecureTokenStore';

type CacheFile = {
  uri: string;
  remove(): void;
};

function createCacheFile(bytes: Uint8Array, extension: string): CacheFile {
  // eslint-disable-next-line @typescript-eslint/no-require-imports
  const { File, Paths } = require('expo-file-system') as typeof import('expo-file-system');
  const file = new File(
    Paths.cache,
    `unispeaking-media-${Date.now()}-${Math.random().toString(36).slice(2, 8)}.${extension}`,
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

export class AuthenticatedMediaClient {
  constructor(
    private readonly baseUrl: string,
    private readonly tokenStore: Pick<TokenStore, 'get'>,
    private readonly fetchImpl: typeof fetch = fetch,
  ) {}

  async download(pathOrUrl: string): Promise<CacheFile> {
    const absolute = /^https?:\/\//i.test(pathOrUrl);
    const target = absolute ? pathOrUrl : `${this.baseUrl.replace(/\/+$/, '')}${pathOrUrl}`;
    const token = await this.tokenStore.get();
    const response = await this.fetchImpl(target, {
      headers: {
        ...(!absolute && token ? { Authorization: `Bearer ${token}` } : {}),
      },
    });
    if (!response.ok) {
      throw new Error(`录音加载失败（${response.status}）`);
    }
    const bytes = new Uint8Array(await response.arrayBuffer());
    const extension = pathOrUrl.toLowerCase().includes('.wav') ? 'wav' : 'audio';
    return createCacheFile(bytes, extension);
  }
}
