import { ApiError } from '@/infrastructure/http/ApiClient';

import {
  AuthSessionController,
  type AuthSessionDependencies,
} from '../AuthSessionController';
import type { AuthResponse, UserAccount, UserPreference } from '../AuthService';

const user: UserAccount = {
  id: 'user-1',
  username: 'learner@example.com',
  nickname: 'Yufan',
  role: 'USER',
  status: 'ACTIVE',
  lastLoginAt: null,
  createdAt: '2026-08-05T00:00:00Z',
};

const preference: UserPreference = {
  userId: 'user-1',
  preferredVoice: 'Harvey',
  preferredAiSpeechSpeed: 'NATURAL',
  cefrLevel: 'B',
  memoryText: null,
};

const authResponse: AuthResponse = {
  tokenType: 'Bearer',
  accessToken: 'jwt-token',
  expiresAt: '2026-08-06T00:00:00Z',
  user,
  refreshToken: 'refresh-token',
};

function createDependencies(
  token: string | null = null,
  refreshToken: string | null = null,
): AuthSessionDependencies & {
  tokenStore: AuthSessionDependencies['tokenStore'] & {
    set: jest.Mock;
    setSession: jest.Mock;
    clear: jest.Mock;
  };
  authService: AuthSessionDependencies['authService'] & {
    login: jest.Mock;
    issueEmailChallenge: jest.Mock;
    register: jest.Mock;
    logout: jest.Mock;
    currentUser: jest.Mock;
    getPreference: jest.Mock;
    updatePreference: jest.Mock;
  };
} {
  return {
    tokenStore: {
      get: jest.fn(async () => token),
      getRefreshToken: jest.fn(async () => refreshToken),
      set: jest.fn(async () => undefined),
      setSession: jest.fn(async () => undefined),
      clear: jest.fn(async () => undefined),
    },
    authService: {
      login: jest.fn(async () => authResponse),
      issueEmailChallenge: jest.fn(async () => ({
        challengeId: 'challenge-1',
        expiresInSeconds: 600,
        resendAfterSeconds: 60,
      })),
      register: jest.fn(async () => authResponse),
      logout: jest.fn(async () => undefined),
      currentUser: jest.fn(async () => user),
      getPreference: jest.fn(async () => preference),
      updatePreference: jest.fn(async (patch: Partial<UserPreference>) => ({
        ...preference,
        ...patch,
      })),
    },
  };
}

describe('AuthSessionController', () => {
  it('notifies subscribers when authentication state changes', async () => {
    const controller = new AuthSessionController(createDependencies());
    const listener = jest.fn();
    const unsubscribe = controller.subscribe(listener);

    await controller.bootstrap();

    expect(listener).toHaveBeenLastCalledWith(
      expect.objectContaining({ status: 'anonymous' }),
    );
    const notificationCount = listener.mock.calls.length;
    unsubscribe();
    await controller.login({
      username: 'learner@example.com',
      password: 'password123456',
    });
    expect(listener).toHaveBeenCalledTimes(notificationCount);
  });

  it('boots into anonymous state when no token is stored', async () => {
    const controller = new AuthSessionController(createDependencies());

    await controller.bootstrap();

    expect(controller.getSnapshot()).toEqual(
      expect.objectContaining({ status: 'anonymous', user: null, preference: null }),
    );
  });

  it('restores the user and preferences when the saved token is valid', async () => {
    const dependencies = createDependencies('saved-token');
    const controller = new AuthSessionController(dependencies);

    await controller.bootstrap();

    expect(controller.getSnapshot()).toEqual({
      status: 'authenticated',
      user,
      preference,
      error: null,
    });
  });

  it('attempts restoration when only a refresh token is stored', async () => {
    const dependencies = createDependencies(null, 'saved-refresh-token');
    const controller = new AuthSessionController(dependencies);

    await controller.bootstrap();

    expect(dependencies.authService.currentUser).toHaveBeenCalledTimes(1);
    expect(controller.getSnapshot().status).toBe('authenticated');
  });

  it('clears an invalid saved token after a 401 bootstrap response', async () => {
    const dependencies = createDependencies('expired-token');
    dependencies.authService.currentUser.mockRejectedValue(
      new ApiError('登录已过期', 401),
    );
    const controller = new AuthSessionController(dependencies);

    await controller.bootstrap();

    expect(dependencies.tokenStore.clear).toHaveBeenCalledTimes(1);
    expect(controller.getSnapshot()).toEqual(
      expect.objectContaining({ status: 'anonymous', error: '登录已过期' }),
    );
  });

  it('preserves a saved token after a recoverable network bootstrap failure', async () => {
    const dependencies = createDependencies('saved-token');
    dependencies.authService.currentUser.mockRejectedValue(new Error('Network request failed'));
    const controller = new AuthSessionController(dependencies);

    await controller.bootstrap();

    expect(dependencies.tokenStore.clear).not.toHaveBeenCalled();
    expect(controller.getSnapshot()).toEqual(
      expect.objectContaining({ status: 'anonymous', error: 'Network request failed' }),
    );
  });

  it('stores the token and loads preferences after login', async () => {
    const dependencies = createDependencies();
    const controller = new AuthSessionController(dependencies);

    await controller.login({
      username: 'learner@example.com',
      password: 'password123456',
    });

    expect(dependencies.tokenStore.setSession).toHaveBeenCalledWith(
      'jwt-token',
      'refresh-token',
    );
    expect(controller.getSnapshot()).toEqual({
      status: 'authenticated',
      user,
      preference,
      error: null,
    });
  });

  it('updates the authenticated preference snapshot', async () => {
    const dependencies = createDependencies('saved-token');
    const controller = new AuthSessionController(dependencies);
    await controller.bootstrap();

    await controller.updatePreference({ preferredAiSpeechSpeed: 'SLOWER' });

    expect(controller.getSnapshot().preference?.preferredAiSpeechSpeed).toBe('SLOWER');
  });

  it('clears local authentication on logout or unauthorized notification', async () => {
    const dependencies = createDependencies('saved-token', 'refresh-token');
    const controller = new AuthSessionController(dependencies);
    await controller.bootstrap();

    await controller.logout();

    expect(dependencies.authService.logout).toHaveBeenCalledWith('refresh-token');
    expect(dependencies.tokenStore.clear).toHaveBeenCalledTimes(1);
    expect(controller.getSnapshot().status).toBe('anonymous');
  });

  it('clears local authentication when server logout fails', async () => {
    const dependencies = createDependencies('saved-token', 'refresh-token');
    dependencies.authService.logout.mockRejectedValue(new Error('Network request failed'));
    const controller = new AuthSessionController(dependencies);
    await controller.bootstrap();

    await controller.logout();

    expect(dependencies.tokenStore.clear).toHaveBeenCalledTimes(1);
    expect(controller.getSnapshot().status).toBe('anonymous');
  });
});
