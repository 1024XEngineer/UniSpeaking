import type { TokenStore } from '@/infrastructure/auth/SecureTokenStore';
import { ApiError } from '@/infrastructure/http/ApiClient';

import type {
  AuthResponse,
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
  register(input: {
    username: string;
    password: string;
    nickname: string | null;
  }): Promise<AuthResponse>;
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

function errorMessage(error: unknown) {
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
    const token = await this.dependencies.tokenStore.get();
    if (!token) {
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
      this.setAnonymous(errorMessage(error));
    }
  }

  async login(input: { username: string; password: string }) {
    this.setState({ ...initialState, status: 'authenticating' });
    try {
      const auth = await this.dependencies.authService.login(input);
      await this.finishAuthentication(auth);
    } catch (error) {
      this.setAnonymous(errorMessage(error));
      throw error;
    }
  }

  async register(input: {
    username: string;
    password: string;
    nickname: string | null;
  }) {
    this.setState({ ...initialState, status: 'authenticating' });
    try {
      const auth = await this.dependencies.authService.register(input);
      await this.finishAuthentication(auth);
    } catch (error) {
      this.setAnonymous(errorMessage(error));
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
    await this.unauthorized();
  }

  async unauthorized() {
    await this.dependencies.tokenStore.clear();
    this.setAnonymous();
  }

  private async finishAuthentication(auth: AuthResponse) {
    await this.dependencies.tokenStore.set(auth.accessToken);
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
