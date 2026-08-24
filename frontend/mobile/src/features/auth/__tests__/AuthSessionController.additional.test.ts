import { ApiError } from '@/infrastructure/http/ApiClient';

import { authErrorMessage, AuthSessionController } from '../AuthSessionController';

describe('AuthSessionController supplementary paths', () => {
  it('forwards account recovery operations and falls back to clearing a legacy token store', async () => {
    const authService = {
      issueEmailChallenge: jest.fn(async (input) => ({ challengeId: input.email, expiresInSeconds: 60, resendAfterSeconds: 5 })),
      issuePasswordResetChallenge: jest.fn(async (input) => ({ challengeId: `reset:${input.email}`, expiresInSeconds: 60, resendAfterSeconds: 5 })),
      resetPassword: jest.fn(async () => undefined),
      revoke: jest.fn(async () => undefined),
    };
    const tokenStore = { get: jest.fn(async () => null), set: jest.fn(async () => undefined), clear: jest.fn(async () => undefined) };
    const controller = new AuthSessionController({ authService, tokenStore } as any);

    await expect(controller.issueEmailChallenge({ email: 'a@example.test' })).resolves.toMatchObject({ challengeId: 'a@example.test' });
    await expect(controller.issuePasswordResetChallenge({ email: 'a@example.test' })).resolves.toMatchObject({ challengeId: 'reset:a@example.test' });
    await controller.resetPassword({ email: 'a@example.test', password: 'long-password', challengeId: 'challenge', code: '123456' });
    await controller.unauthorized();

    expect(authService.resetPassword).toHaveBeenCalledWith(expect.objectContaining({ challengeId: 'challenge' }));
    expect(tokenStore.clear).toHaveBeenCalledTimes(1);
  });

  it('maps known API failures to recovery-friendly Chinese messages', () => {
    expect(authErrorMessage(new ApiError('ignored', 400, 'CHALLENGE_INVALID'))).toBe('验证码无效或已过期，请重新获取');
    expect(authErrorMessage(new ApiError('ignored', 400, 'WEAK_PASSWORD'))).toBe('密码至少需要 12 位字符');
    expect(authErrorMessage(null)).toBe('请求失败，请稍后重试');
  });
});
