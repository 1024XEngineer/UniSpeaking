export type ProfileAccount = {
  userId: string;
  email: string;
  nickname: string | null;
  displayName: string;
  avatarUrl: string | null;
  avatarUrlExpiresAt: string | null;
};

export type DailyPractice = {
  date: string;
  practiceSeconds: number;
};

export type ProfileOverview = {
  account: ProfileAccount;
  statistics: {
    weeklyPracticeSeconds: number;
    trainingRecordCount: number;
    consecutiveLearningDays: number;
    lastSevenDays: DailyPractice[];
  };
  calendar: {
    month: string;
    checkedDates: string[];
    checkedInToday: boolean;
  };
};

export type WeeklyGoals = {
  weekStartsAt: string;
  weekEndsAt: string;
  durationTargetMinutes: number;
  completedDurationSeconds: number;
  remainingDurationSeconds: number;
  durationProgress: number;
  durationAchieved: boolean;
  trainingCountTarget: number;
  completedTrainingCount: number;
  remainingTrainingCount: number;
  countProgress: number;
  countAchieved: boolean;
};

export type AbilityScores = {
  accuracy: number | null;
  fluency: number | null;
  grammar: number | null;
  vocabulary: number | null;
  naturalness: number | null;
};

export type ProfileInsights = {
  weeklyGoals: WeeklyGoals;
  trainingTypeDistribution: {
    type: string;
    durationSeconds: number;
    percentage: number;
  }[];
  abilityTrends: {
    sessionId: string;
    completedAt: string;
    trainingType: string;
    scores: AbilityScores;
  }[];
  weaknessAnalysis: {
    sampleCount: number;
    minimumSampleCount: number;
    reliable: boolean;
  };
  weaknesses: {
    dimension: string;
    rank: number;
    averageScore: number;
    recentChange: number;
    basis: string;
  }[];
  recommendations: {
    dimension: string;
    trainingType: string;
    reason: string;
  }[];
};

export type AchievementMilestone = {
  achievementId: string;
  level: number;
  title: string;
  description: string;
  threshold: number;
  unlocked: boolean;
  unlockedAt: string | null;
};

export type AchievementSeries = {
  seriesId: string;
  category: string;
  title: string;
  unit: string;
  currentValue: number;
  currentLevel: number;
  currentTitle: string | null;
  nextLevel: number | null;
  nextTitle: string | null;
  nextThreshold: number | null;
  completed: boolean;
  milestones: AchievementMilestone[];
};

export type AchievementOverview = { series: AchievementSeries[] };

export type HelpCategory = {
  id: string;
  title: string;
  description: string;
  articleCount: number;
};

export type HelpCenterContent = {
  categories: HelpCategory[];
};

export type HelpArticleSummary = {
  id: string;
  title: string;
  summary: string;
};

export type HelpCategoryDetail = {
  id: string;
  title: string;
  description: string;
  articles: HelpArticleSummary[];
};

export type HelpArticle = HelpArticleSummary & {
  categoryId: string;
  updatedAt: string;
};

export type ProfileAvatar = {
  uri: string;
  mimeType: string;
  fileName: string;
  fileSize?: number | null;
};

type ApiRequester = {
  request<T>(path: string, options?: RequestInit): Promise<T>;
};

export class ProfileApi {
  constructor(private readonly client: ApiRequester) {}

  getOverview(month?: string) {
    const query = month ? `?month=${encodeURIComponent(month)}` : '';
    return this.client.request<ProfileOverview>(`/api/profile/overview${query}`);
  }

  getInsights() {
    return this.client.request<ProfileInsights>('/api/profile/insights');
  }

  updateWeeklyGoals(input: { durationTargetMinutes: number; trainingCountTarget: number }) {
    return this.client.request<ProfileInsights>('/api/profile/insights/goals', {
      method: 'PUT',
      body: JSON.stringify(input),
    });
  }

  getAchievements() {
    return this.client.request<AchievementOverview>('/api/achievements');
  }

  updateNickname(nickname: string) {
    return this.client.request<{ nickname: string; displayName: string }>('/api/profile', {
      method: 'PATCH',
      body: JSON.stringify({ nickname }),
    });
  }

  uploadAvatar(avatar: ProfileAvatar) {
    const formData = new FormData();
    formData.append('avatar', {
      uri: avatar.uri,
      type: avatar.mimeType,
      name: avatar.fileName,
    } as unknown as Blob);
    return this.client.request<{
      avatarUrl: string;
      avatarUrlExpiresAt: string;
    }>('/api/profile/avatar', { method: 'POST', body: formData });
  }

  changePassword(input: { currentPassword: string; newPassword: string }) {
    return this.client.request<{ reauthenticationRequired: boolean }>('/api/auth/password', {
      method: 'PUT',
      body: JSON.stringify(input),
    });
  }

  getHelpCenter() {
    return this.client.request<HelpCenterContent>('/api/help-center');
  }

  getHelpCategory(categoryId: string) {
    return this.client.request<HelpCategoryDetail>(
      `/api/help-center/categories/${encodeURIComponent(categoryId)}`,
    );
  }

  getHelpArticle(articleId: string) {
    return this.client.request<HelpArticle>(
      `/api/help-center/articles/${encodeURIComponent(articleId)}`,
    );
  }
}
