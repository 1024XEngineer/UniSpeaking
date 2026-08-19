import { AuthService, type UserPreference } from '../AuthService';

function createClient() {
  return {
    request: jest.fn(async () => undefined),
  };
}

describe('AuthService', () => {
  it('logs in through mobile email auth without human verification', async () => {
    const client = createClient();
    const service = new AuthService(client);

    await service.login({
      username: 'learner@example.com',
      password: 'password123456',
    });

    expect(client.request).toHaveBeenCalledWith('/api/auth/mobile/email/login', {
      method: 'POST',
      body: JSON.stringify({
        email: 'learner@example.com',
        password: 'password123456',
      }),
    });
  });

  it('issues a mobile email challenge without human verification', async () => {
    const client = createClient();
    const service = new AuthService(client);

    await service.issueEmailChallenge({
      email: 'learner@example.com',
    });

    expect(client.request).toHaveBeenCalledWith('/api/auth/mobile/email/challenges', {
      method: 'POST',
      body: JSON.stringify({
        email: 'learner@example.com',
      }),
    });
  });

  it('registers with the email challenge and nickname', async () => {
    const client = createClient();
    const service = new AuthService(client);

    await service.register({
      username: 'learner@example.com',
      password: 'password123456',
      nickname: 'Yufan',
      challengeId: 'challenge-1',
      code: '123456',
    });

    expect(client.request).toHaveBeenCalledWith('/api/auth/mobile/email/register', {
      method: 'POST',
      body: JSON.stringify({
        email: 'learner@example.com',
        password: 'password123456',
        nickname: 'Yufan',
        challengeId: 'challenge-1',
        code: '123456',
      }),
    });
  });

  it('revokes the persistent session on logout', async () => {
    const client = createClient();
    const service = new AuthService(client);

    await service.logout('refresh-token');

    expect(client.request).toHaveBeenCalledWith('/api/auth/mobile/email/logout', {
      method: 'POST',
      body: JSON.stringify({ refreshToken: 'refresh-token' }),
    });
  });

  it('loads the current account and preference from protected endpoints', async () => {
    const client = createClient();
    const service = new AuthService(client);

    await service.currentUser();
    await service.getPreference();

    expect(client.request).toHaveBeenNthCalledWith(1, '/api/auth/me');
    expect(client.request).toHaveBeenNthCalledWith(2, '/api/user-preferences');
  });

  it('updates only the supplied preference fields', async () => {
    const client = createClient();
    const service = new AuthService(client);
    const patch: Partial<UserPreference> = {
      cefrLevel: 'B',
      preferredVoice: 'Harvey',
    };

    await service.updatePreference(patch);

    expect(client.request).toHaveBeenCalledWith('/api/user-preferences', {
      method: 'PUT',
      body: JSON.stringify(patch),
    });
  });
});
