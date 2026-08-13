export type UserAccount = {
  id: string;
  username: string;
  nickname: string | null;
  role: string;
  status: string;
  lastLoginAt: string | null;
  createdAt: string;
};

export type AuthResponse = {
  tokenType: string;
  accessToken: string;
  expiresAt: string;
  user: UserAccount;
};

export type PreferredVoice =
  | 'Katerina'
  | 'Aiden'
  | 'Raymond'
  | 'Tina'
  | 'Harvey'
  | 'Dolce';

export type PreferredAiSpeechSpeed =
  | 'SLOWER'
  | 'MODERATE'
  | 'NATURAL'
  | 'FASTER';

export type CefrLevel = 'A' | 'B' | 'C' | 'D';

export type UserPreference = {
  userId: string;
  preferredVoice: PreferredVoice | null;
  preferredAiSpeechSpeed: PreferredAiSpeechSpeed | null;
  cefrLevel: CefrLevel | null;
  memoryText: string | null;
};

type ApiRequester = {
  request(path: string, options?: RequestInit): Promise<unknown>;
};

export type EmailChallenge = {
  challengeId: string;
  expiresInSeconds: number;
  resendAfterSeconds: number;
};

export class AuthService {
  constructor(private readonly client: ApiRequester) {}

  login(input: { username: string; password: string }) {
    return this.client.request('/api/auth/mobile/email/login', {
      method: 'POST',
      body: JSON.stringify({
        email: input.username,
        password: input.password,
      }),
    }) as Promise<AuthResponse>;
  }

  issueEmailChallenge(input: { email: string }) {
    return this.client.request('/api/auth/mobile/email/challenges', {
      method: 'POST',
      body: JSON.stringify(input),
    }) as Promise<EmailChallenge>;
  }

  register(input: {
    username: string;
    password: string;
    nickname: string | null;
    challengeId: string;
    code: string;
  }) {
    return this.client.request('/api/auth/mobile/email/register', {
      method: 'POST',
      body: JSON.stringify({
        email: input.username,
        password: input.password,
        nickname: input.nickname,
        challengeId: input.challengeId,
        code: input.code,
      }),
    }) as Promise<AuthResponse>;
  }

  currentUser() {
    return this.client.request('/api/auth/me') as Promise<UserAccount>;
  }

  getPreference() {
    return this.client.request('/api/user-preferences') as Promise<UserPreference>;
  }

  updatePreference(patch: Partial<UserPreference>) {
    return this.client.request('/api/user-preferences', {
      method: 'PUT',
      body: JSON.stringify(patch),
    }) as Promise<UserPreference>;
  }
}
