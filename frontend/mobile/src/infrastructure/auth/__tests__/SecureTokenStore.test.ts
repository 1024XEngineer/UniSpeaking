import { SecureTokenStore } from '../SecureTokenStore';

function createStorage(initialValue: string | null = null) {
  const values = new Map<string, string>();
  if (initialValue) values.set('unispeaking.accessToken', initialValue);
  return {
    getItemAsync: jest.fn(async (key: string) => values.get(key) ?? null),
    setItemAsync: jest.fn(async (key: string, nextValue: string) => {
      values.set(key, nextValue);
    }),
    deleteItemAsync: jest.fn(async (key: string) => {
      values.delete(key);
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

  it('saves and clears the persistent mobile session', async () => {
    const storage = createStorage();
    const tokenStore = new SecureTokenStore(storage);

    await tokenStore.setSession('new-access-token', 'new-refresh-token');
    await expect(tokenStore.get()).resolves.toBe('new-access-token');
    await expect(tokenStore.getRefreshToken()).resolves.toBe('new-refresh-token');
    await tokenStore.clear();
    await expect(tokenStore.get()).resolves.toBeNull();
    await expect(tokenStore.getRefreshToken()).resolves.toBeNull();

    expect(storage.setItemAsync).toHaveBeenCalledWith(
      'unispeaking.accessToken',
      'new-access-token',
    );
    expect(storage.setItemAsync).toHaveBeenCalledWith(
      'unispeaking.refreshToken',
      'new-refresh-token',
    );
    expect(storage.deleteItemAsync).toHaveBeenCalledWith(
      'unispeaking.accessToken',
    );
    expect(storage.deleteItemAsync).toHaveBeenCalledWith(
      'unispeaking.refreshToken',
    );
  });

  it('updates the access token without replacing the refresh token', async () => {
    const storage = createStorage();
    const tokenStore = new SecureTokenStore(storage);
    await tokenStore.setSession('old-access-token', 'refresh-token');

    await tokenStore.set('new-access-token');

    await expect(tokenStore.get()).resolves.toBe('new-access-token');
    await expect(tokenStore.getRefreshToken()).resolves.toBe('refresh-token');
  });
});
