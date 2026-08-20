import { SecureTokenStore, type TokenStore } from './SecureTokenStore';

export type TokenRefreshResult = { accessToken: string; refreshToken?: string | null };
export type AuthTokenCoordinatorOptions = {
  tokenStore?: TokenStore;
  refresh?: (refreshToken: string) => Promise<TokenRefreshResult>;
  onRefreshFailure?: (error: unknown) => void | Promise<void>;
};

export class AuthTokenCoordinator {
  private refreshPromise: Promise<string | null> | null = null;
  private refreshHandler?: AuthTokenCoordinatorOptions['refresh'];
  private onRefreshFailure?: AuthTokenCoordinatorOptions['onRefreshFailure'];

  constructor(
    readonly tokenStore: TokenStore = new SecureTokenStore(),
    options: Omit<AuthTokenCoordinatorOptions, 'tokenStore'> = {},
  ) {
    this.refreshHandler = options.refresh;
    this.onRefreshFailure = options.onRefreshFailure;
  }

  configure(options: Omit<AuthTokenCoordinatorOptions, 'tokenStore'>) {
    this.refreshHandler = options.refresh;
    this.onRefreshFailure = options.onRefreshFailure;
  }

  async getAccessToken() {
    return (await this.tokenStore.getAccessToken?.()) ?? this.tokenStore.get();
  }

  async saveTokens(tokens: TokenRefreshResult) {
    if (this.tokenStore.setTokens) await this.tokenStore.setTokens(tokens);
    else await this.tokenStore.set(tokens.accessToken);
    return tokens.accessToken;
  }

  async refreshAccessToken(): Promise<string | null> {
    if (this.refreshPromise) return this.refreshPromise;
    this.refreshPromise = this.performRefresh().finally(() => { this.refreshPromise = null; });
    return this.refreshPromise;
  }

  async clear() {
    if (this.tokenStore.clearTokens) await this.tokenStore.clearTokens();
    else await this.tokenStore.clear();
  }

  private async performRefresh() {
    const refreshToken = await this.tokenStore.getRefreshToken?.();
    if (!refreshToken || !this.refreshHandler) return null;
    try {
      const tokens = await this.refreshHandler(refreshToken);
      return this.saveTokens(tokens);
    } catch (error) {
      await this.onRefreshFailure?.(error);
      return null;
    }
  }
}

export const authTokenCoordinator = new AuthTokenCoordinator();
