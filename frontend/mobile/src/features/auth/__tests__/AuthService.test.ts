import { AuthService, type UserPreference } from '../AuthService';

function createClient() {
  return {
    request: jest.fn(async () => undefined),
  };
}

describe('AuthService', () => {
  it('logs in with the Java auth request shape', async () => {
    const client = createClient();
    const service = new AuthService(client);

    await service.login({ username: 'learner@example.com', password: 'password123' });

    expect(client.request).toHaveBeenCalledWith('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({
        username: 'learner@example.com',
        password: 'password123',
      }),
    });
  });

  it('registers with nickname using the Java auth request shape', async () => {
    const client = createClient();
    const service = new AuthService(client);

    await service.register({
      username: 'learner@example.com',
      password: 'password123',
      nickname: 'Yufan',
    });

    expect(client.request).toHaveBeenCalledWith('/api/auth/register', {
      method: 'POST',
      body: JSON.stringify({
        username: 'learner@example.com',
        password: 'password123',
        nickname: 'Yufan',
      }),
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
