import * as SecureStore from 'expo-secure-store';

export interface TokenStore {
  get(): Promise<string | null>;
  set(token: string): Promise<void>;
  clear(): Promise<void>;
}

export interface SecureStoragePort {
  getItemAsync(key: string): Promise<string | null>;
  setItemAsync(key: string, value: string): Promise<void>;
  deleteItemAsync(key: string): Promise<void>;
}

export const accessTokenStorageKey = 'unispeaking.accessToken';

export class SecureTokenStore implements TokenStore {
  constructor(
    private readonly storage: SecureStoragePort = SecureStore,
    private readonly storageKey: string = accessTokenStorageKey,
  ) {}

  get() {
    return this.storage.getItemAsync(this.storageKey);
  }

  set(token: string) {
    return this.storage.setItemAsync(this.storageKey, token);
  }

  clear() {
    return this.storage.deleteItemAsync(this.storageKey);
  }
}
