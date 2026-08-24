import { AuthTokenCoordinator } from '../AuthTokenCoordinator';

describe('AuthTokenCoordinator', () => {
  it('reads, saves and clears legacy token stores', async () => {
    const store = { get: jest.fn().mockReturnValue('old'), set: jest.fn(), clear: jest.fn() };
    const coordinator = new AuthTokenCoordinator(store as any);
    await expect(coordinator.getAccessToken()).resolves.toBe('old');
    await expect(coordinator.saveTokens({ accessToken: 'new' })).resolves.toBe('new');
    expect(store.set).toHaveBeenCalledWith('new');
    await coordinator.clear();
    expect(store.clear).toHaveBeenCalled();
  });

  it('refreshes once for concurrent callers and handles missing/failed refresh', async () => {
    const store = { get: jest.fn(), getRefreshToken: jest.fn().mockResolvedValue('refresh'), setTokens: jest.fn() };
    const refresh = jest.fn().mockResolvedValue({ accessToken: 'fresh' });
    const coordinator = new AuthTokenCoordinator(store as any, { refresh });
    await expect(Promise.all([coordinator.refreshAccessToken(), coordinator.refreshAccessToken()])).resolves.toEqual(['fresh', 'fresh']);
    expect(refresh).toHaveBeenCalledTimes(1);
    const missing = new AuthTokenCoordinator({ getRefreshToken: jest.fn().mockResolvedValue(null) } as any, { refresh });
    await expect(missing.refreshAccessToken()).resolves.toBeNull();
    const onFailure = jest.fn();
    const failed = new AuthTokenCoordinator({ getRefreshToken: jest.fn().mockResolvedValue('r') } as any, { refresh: jest.fn().mockRejectedValue(new Error('bad')), onRefreshFailure: onFailure });
    await expect(failed.refreshAccessToken()).resolves.toBeNull();
    expect(onFailure).toHaveBeenCalled();
  });
});
