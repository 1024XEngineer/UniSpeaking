import {
  createPlatformTokenStorage,
  SecureTokenStore,
  WebTokenStorage,
} from '../SecureTokenStore';

function createStorage(initialValue: string | null = null) {
  let value = initialValue;
  return {
    getItemAsync: jest.fn(async () => value),
    setItemAsync: jest.fn(async (_key: string, nextValue: string) => {
      value = nextValue;
    }),
    deleteItemAsync: jest.fn(async () => {
      value = null;
    }),
  };
}

describe('SecureTokenStore', () => {
  it('reads the saved access token using the stable storage key', async () => {
    const storage = createStorage('saved-token');
    const tokenStore = new SecureTokenStore(storage);

    await expect(tokenStore.get()).resolves.toBe('saved-token');
    expect(storage.getItemAsync).toHaveBeenCalledWith('unispeaking.accessToken');
  });

  it('saves and clears the access token', async () => {
    const storage = createStorage();
    const tokenStore = new SecureTokenStore(storage);

    await tokenStore.set('new-token');
    await expect(tokenStore.get()).resolves.toBe('new-token');
    await tokenStore.clear();
    await expect(tokenStore.get()).resolves.toBeNull();

    expect(storage.setItemAsync).toHaveBeenCalledWith(
      'unispeaking.accessToken',
      'new-token',
    );
    expect(storage.deleteItemAsync).toHaveBeenCalledWith(
      'unispeaking.accessToken',
    );
  });

  it('uses browser storage for the web platform', async () => {
    const values = new Map<string, string>();
    const browserStorage = {
      getItem: jest.fn((key: string) => values.get(key) ?? null),
      setItem: jest.fn((key: string, value: string) => values.set(key, value)),
      removeItem: jest.fn((key: string) => values.delete(key)),
    };
    const storage = new WebTokenStorage(() => browserStorage);
    const tokenStore = new SecureTokenStore(storage);

    await tokenStore.set('web-token');
    await expect(tokenStore.get()).resolves.toBe('web-token');
    await tokenStore.clear();
    await expect(tokenStore.get()).resolves.toBeNull();
  });

  it('selects web storage only for the web platform', () => {
    expect(createPlatformTokenStorage('web')).toBeInstanceOf(WebTokenStorage);
    expect(createPlatformTokenStorage('ios')).not.toBeInstanceOf(WebTokenStorage);
  });
});
