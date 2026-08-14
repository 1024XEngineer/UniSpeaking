import { ProfileApi } from '../ProfileApi';

describe('ProfileApi', () => {
  it('uses the same overview and insights contracts as the web client', async () => {
    const request = jest.fn().mockResolvedValue({});
    const api = new ProfileApi({ request });

    await api.getOverview('2026-08');
    await api.getInsights();
    await api.updateWeeklyGoals({
      durationTargetMinutes: 180,
      trainingCountTarget: 6,
    });

    expect(request).toHaveBeenNthCalledWith(1, '/api/profile/overview?month=2026-08');
    expect(request).toHaveBeenNthCalledWith(2, '/api/profile/insights');
    expect(request).toHaveBeenNthCalledWith(3, '/api/profile/insights/goals', {
      method: 'PUT',
      body: JSON.stringify({
        durationTargetMinutes: 180,
        trainingCountTarget: 6,
      }),
    });
  });

  it('updates account data and password through authenticated endpoints', async () => {
    const request = jest.fn().mockResolvedValue({});
    const api = new ProfileApi({ request });

    await api.updateNickname('方婧');
    await api.changePassword({
      currentPassword: 'old-password',
      newPassword: 'new-password',
    });
    await api.getAchievements();

    expect(request).toHaveBeenNthCalledWith(1, '/api/profile', {
      method: 'PATCH',
      body: JSON.stringify({ nickname: '方婧' }),
    });
    expect(request).toHaveBeenNthCalledWith(2, '/api/auth/password', {
      method: 'PUT',
      body: JSON.stringify({
        currentPassword: 'old-password',
        newPassword: 'new-password',
      }),
    });
    expect(request).toHaveBeenNthCalledWith(3, '/api/achievements');
  });

  it('uploads an avatar as multipart form data', async () => {
    const request = jest.fn().mockResolvedValue({});
    const api = new ProfileApi({ request });

    await api.uploadAvatar({
      uri: 'file:///avatar.jpg',
      mimeType: 'image/jpeg',
      fileName: 'avatar.jpg',
    });

    expect(request).toHaveBeenCalledWith('/api/profile/avatar', {
      method: 'POST',
      body: expect.any(FormData),
    });
  });

  it('loads help center content from the backend', async () => {
    const request = jest.fn().mockResolvedValue({ categories: [] });
    const api = new ProfileApi({ request });

    await api.getHelpCenter();

    expect(request).toHaveBeenCalledWith('/api/help-center');
  });
});
