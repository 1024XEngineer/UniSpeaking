import type { TokenStore } from '@/infrastructure/auth/SecureTokenStore';
import { ApiError } from '@/infrastructure/http/ApiClient';

import type {
  AuthResponse,
  EmailChallenge,
  UserAccount,
  UserPreference,
} from './AuthService';

export type AuthSessionStatus =
  | 'booting'
  | 'anonymous'
  | 'authenticating'
  | 'authenticated';

export type AuthSessionState = Readonly<{
  status: AuthSessionStatus;
  user: UserAccount | null;
  preference: UserPreference | null;
  error: string | null;
}>;

type AuthServicePort = {
  login(input: { username: string; password: string }): Promise<AuthResponse>;
  issueEmailChallenge(input: { email: string }): Promise<EmailChallenge>;
  register(input: {
    username: string;
    password: string;
    nickname: string | null;
    challengeId: string;
    code: string;
  }): Promise<AuthResponse>;
  logout(refreshToken: string): Promise<void>;
  currentUser(): Promise<UserAccount>;
  getPreference(): Promise<UserPreference>;
  updatePreference(patch: Partial<UserPreference>): Promise<UserPreference>;
};

export type AuthSessionDependencies = {
  tokenStore: TokenStore;
  authService: AuthServicePort;
};

type AuthStateListener = (state: AuthSessionState) => void;

const initialState: AuthSessionState = {
  status: 'booting',
  user: null,
  preference: null,
  error: null,
};

export function authErrorMessage(error: unknown) {
  if (error instanceof ApiError) {
    const messages: Record<string, string> = {
      INVALID_CREDENTIALS: '邮箱或密码错误',
      CHALLENGE_INVALID: '验证码无效或已过期，请重新获取',
      IDENTITY_ALREADY_BOUND: '该邮箱已注册，请直接登录',
      WEAK_PASSWORD: '密码至少需要 12 位字符',
    };
    if (error.code && messages[error.code]) return messages[error.code];
  }
  return error instanceof Error ? error.message : '请求失败，请稍后重试';
}

export class AuthSessionController {
  private state = initialState;
  private readonly listeners = new Set<AuthStateListener>();

  constructor(private readonly dependencies: AuthSessionDependencies) {}

  getSnapshot() {
    return this.state;
  }

  subscribe(listener: AuthStateListener) {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  async bootstrap() {
    this.setState({ ...initialState, status: 'booting' });
    const [accessToken, refreshToken] = await Promise.all([
      this.dependencies.tokenStore.get(),
      this.dependencies.tokenStore.getRefreshToken(),
    ]);
    if (!accessToken && !refreshToken) {
      this.setAnonymous();
      return;
    }

    try {
      const [user, preference] = await Promise.all([
        this.dependencies.authService.currentUser(),
        this.dependencies.authService.getPreference(),
      ]);
      this.setAuthenticated(user, preference);
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        await this.dependencies.tokenStore.clear();
      }
      this.setAnonymous(authErrorMessage(error));
    }
  }

  issueEmailChallenge(input: { email: string }) {
    return this.dependencies.authService.issueEmailChallenge(input);
  }

  async login(input: { username: string; password: string }) {
    this.setState({ ...initialState, status: 'authenticating' });
    try {
      const auth = await this.dependencies.authService.login(input);
      await this.finishAuthentication(auth);
    } catch (error) {
      this.setAnonymous(authErrorMessage(error));
      throw error;
    }
  }

  async register(input: {
    username: string;
    password: string;
    nickname: string | null;
    challengeId: string;
    code: string;
  }) {
    this.setState({ ...initialState, status: 'authenticating' });
    try {
      const auth = await this.dependencies.authService.register(input);
      await this.finishAuthentication(auth);
    } catch (error) {
      this.setAnonymous(authErrorMessage(error));
      throw error;
    }
  }

  async updatePreference(patch: Partial<UserPreference>) {
    if (this.state.status !== 'authenticated') {
      throw new Error('需要登录后才能更新偏好');
    }
    const preference = await this.dependencies.authService.updatePreference(patch);
    this.setState({ ...this.state, preference, error: null });
    return preference;
  }

  async logout() {
    const refreshToken = await this.dependencies.tokenStore.getRefreshToken();
    try {
      if (refreshToken) await this.dependencies.authService.logout(refreshToken);
    } catch {
      // Local logout must succeed even when the server cannot be reached.
    } finally {
      await this.unauthorized();
    }
  }

  async unauthorized() {
    await this.dependencies.tokenStore.clear();
    this.setAnonymous();
  }

  private async finishAuthentication(auth: AuthResponse) {
    await this.dependencies.tokenStore.setSession(auth.accessToken, auth.refreshToken);
    const preference = await this.dependencies.authService.getPreference();
    this.setAuthenticated(auth.user, preference);
  }

  private setAuthenticated(user: UserAccount, preference: UserPreference) {
    this.setState({
      status: 'authenticated',
      user,
      preference,
      error: null,
    });
  }

  private setAnonymous(error: string | null = null) {
    this.setState({
      status: 'anonymous',
      user: null,
      preference: null,
      error,
    });
  }

  private setState(state: AuthSessionState) {
    this.state = state;
    this.listeners.forEach((listener) => listener(state));
  }
}
