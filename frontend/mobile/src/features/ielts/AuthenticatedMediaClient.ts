import type { TokenStore } from '@/infrastructure/auth/SecureTokenStore';
import { authTokenCoordinator, type AuthTokenCoordinator } from '@/infrastructure/auth/AuthTokenCoordinator';

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
    private readonly tokenCoordinator: AuthTokenCoordinator = authTokenCoordinator,
  ) {}

  async download(pathOrUrl: string): Promise<CacheFile> {
    const absolute = /^https?:\/\//i.test(pathOrUrl);
    const target = absolute ? pathOrUrl : `${this.baseUrl.replace(/\/+$/, '')}${pathOrUrl}`;
    let token = await this.tokenCoordinator.getAccessToken();
    let response = await this.fetchImpl(target, {
      headers: {
        ...(!absolute && token ? { Authorization: `Bearer ${token}` } : {}),
      },
    });
    if (response.status === 401 && !absolute) {
      const refreshedToken = await this.tokenCoordinator.refreshAccessToken();
      if (refreshedToken) {
        token = refreshedToken;
        response = await this.fetchImpl(target, {
          headers: { Authorization: `Bearer ${refreshedToken}` },
        });
      }
    }
    if (!response.ok) {
      throw new Error(`录音加载失败（${response.status}）`);
    }
    const bytes = new Uint8Array(await response.arrayBuffer());
    const extension = pathOrUrl.toLowerCase().includes('.wav') ? 'wav' : 'audio';
    return createCacheFile(bytes, extension);
  }
}
