import * as SecureStore from 'expo-secure-store';
import { Platform } from 'react-native';

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

interface WebStorage {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
  removeItem(key: string): void;
}

export class WebTokenStorage implements SecureStoragePort {
  constructor(
    private readonly storageProvider: () => WebStorage | undefined = () =>
      typeof globalThis.localStorage === 'undefined'
        ? undefined
        : globalThis.localStorage,
  ) {}

  async getItemAsync(key: string) {
    return this.storageProvider()?.getItem(key) ?? null;
  }

  async setItemAsync(key: string, value: string) {
    this.storageProvider()?.setItem(key, value);
  }

  async deleteItemAsync(key: string) {
    this.storageProvider()?.removeItem(key);
  }
}

export function createPlatformTokenStorage(
  platform: string = Platform.OS,
): SecureStoragePort {
  return platform === 'web' ? new WebTokenStorage() : SecureStore;
}

export const accessTokenStorageKey = 'unispeaking.accessToken';
export const refreshTokenStorageKey = 'unispeaking.refreshToken';

export class SecureTokenStore implements TokenStore {
  constructor(
    private readonly storage: SecureStoragePort = createPlatformTokenStorage(),
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
