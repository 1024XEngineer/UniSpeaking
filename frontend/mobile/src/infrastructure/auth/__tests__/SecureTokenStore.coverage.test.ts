import { WebTokenStorage, SecureTokenStore, createPlatformTokenStorage } from '../SecureTokenStore';

describe('token storage adapters', () => {
  it('uses web storage and stores/clears access and refresh tokens', async () => {
    const values = new Map<string, string>();
    const storage = new WebTokenStorage(() => ({ getItem: (key) => values.get(key) ?? null, setItem: (key, value) => { values.set(key, value); }, removeItem: (key) => { values.delete(key); } }));
    await storage.setItemAsync('a', '1');
    await expect(storage.getItemAsync('a')).resolves.toBe('1');
    await storage.deleteItemAsync('a');
    await expect(storage.getItemAsync('a')).resolves.toBeNull();
    const store = new SecureTokenStore(storage, 'access', 'refresh');
    await store.setTokens({ accessToken: 'access-token', refreshToken: 'refresh-token' });
    await expect(store.getAccessToken()).resolves.toBe('access-token');
    await expect(store.getRefreshToken()).resolves.toBe('refresh-token');
    await store.setTokens({ accessToken: 'next', refreshToken: null });
    await store.clearTokens();
    await expect(store.get()).resolves.toBeNull();
  });

  it('selects the platform storage implementation', () => {
    expect(createPlatformTokenStorage('web')).toBeInstanceOf(WebTokenStorage);
    expect(createPlatformTokenStorage('ios')).toBeDefined();
  });
});
