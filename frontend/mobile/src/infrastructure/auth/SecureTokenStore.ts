import * as SecureStore from 'expo-secure-store';

export interface TokenStore {
  get(): Promise<string | null>;
  getRefreshToken(): Promise<string | null>;
  set(token: string): Promise<void>;
  setSession(accessToken: string, refreshToken: string): Promise<void>;
  clear(): Promise<void>;
}

export interface SecureStoragePort {
  getItemAsync(key: string): Promise<string | null>;
  setItemAsync(key: string, value: string): Promise<void>;
  deleteItemAsync(key: string): Promise<void>;
}

export const accessTokenStorageKey = 'unispeaking.accessToken';
export const refreshTokenStorageKey = 'unispeaking.refreshToken';

export class SecureTokenStore implements TokenStore {
  constructor(
    private readonly storage: SecureStoragePort = SecureStore,
    private readonly storageKey: string = accessTokenStorageKey,
    private readonly refreshStorageKey: string = refreshTokenStorageKey,
  ) {}

  get() {
    return this.storage.getItemAsync(this.storageKey);
  }

  set(token: string) {
    return this.storage.setItemAsync(this.storageKey, token);
  }

  getRefreshToken() {
    return this.storage.getItemAsync(this.refreshStorageKey);
  }

  async setSession(accessToken: string, refreshToken: string) {
    await this.storage.setItemAsync(this.refreshStorageKey, refreshToken);
    await this.storage.setItemAsync(this.storageKey, accessToken);
  }

  async clear() {
    await Promise.all([
      this.storage.deleteItemAsync(this.storageKey),
      this.storage.deleteItemAsync(this.refreshStorageKey),
    ]);
  }
}
