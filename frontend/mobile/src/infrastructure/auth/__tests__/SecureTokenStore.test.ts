import { SecureTokenStore } from '../SecureTokenStore';

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
});
