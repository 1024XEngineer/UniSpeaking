import { fireEvent, render, waitFor } from '@testing-library/react-native';
import { AccessibilityInfo } from 'react-native';

import type { AuthSessionState } from '@/features/auth/AuthSessionController';
import type { AppModelAuthController } from '@/model/AppModel';
import { AppModelProvider } from '@/model/AppModel';

jest.mock('@/components/TeacherSwipeStack', () => ({
  TeacherSwipeStack: () => null,
}));

import { AnimatedSloganLine, AuthFormScreen, PasswordResetScreen } from '../AuthScreens';

function createController(state: AuthSessionState): AppModelAuthController & {
  login: jest.Mock;
  register: jest.Mock;
  issueEmailChallenge: jest.Mock;
  issuePasswordResetChallenge: jest.Mock;
  resetPassword: jest.Mock;
} {
  let listener: ((nextState: AuthSessionState) => void) | null = null;
  return {
    getSnapshot: () => state,
    subscribe: jest.fn((nextListener) => {
      listener = nextListener;
      return () => {
        listener = null;
      };
    }),
    bootstrap: jest.fn(async () => {
      listener?.(state);
    }),
    login: jest.fn(async () => undefined),
    issueEmailChallenge: jest.fn(async () => ({
      challengeId: 'challenge-1',
      expiresInSeconds: 600,
      resendAfterSeconds: 60,
    })),
    issuePasswordResetChallenge: jest.fn(async () => ({
      challengeId: 'reset-challenge-1',
      expiresInSeconds: 600,
      resendAfterSeconds: 60,
    })),
    resetPassword: jest.fn(async () => undefined),
    register: jest.fn(async () => undefined),
    updatePreference: jest.fn(async () => ({
      userId: 'user-1',
      preferredVoice: null,
      preferredAiSpeechSpeed: null,
      cefrLevel: null,
      memoryText: null,
    })),
    logout: jest.fn(async () => undefined),
    unauthorized: jest.fn(async () => undefined),
  };
}

describe('AuthFormScreen backend binding', () => {
  it('renders reduced-motion slogan characters without starting animations', async () => {
    jest.spyOn(AccessibilityInfo, 'isReduceMotionEnabled').mockResolvedValue(true);
    const view = await render(<AnimatedSloganLine text="Speak" delay={0} />);
    await Promise.resolve();
    await Promise.resolve();
    expect(view.getByLabelText('Speak')).toBeTruthy();
    view.unmount();
  });

  it('submits the entered login credentials', async () => {
    const controller = createController({
      status: 'anonymous',
      user: null,
      preference: null,
      error: null,
    });
    const screen = await render(
      <AppModelProvider authController={controller}>
        <AuthFormScreen mode="login" onBack={jest.fn()} onSwitch={jest.fn()} />
      </AppModelProvider>,
    );

    await fireEvent.changeText(
      screen.getByPlaceholderText('name@example.com'),
      'learner@example.com',
    );
    await fireEvent.changeText(screen.getByPlaceholderText('请输入密码'), 'password123456');
    await fireEvent.press(screen.getByRole('button', { name: '登录' }));

    await waitFor(() =>
      expect(controller.login).toHaveBeenCalledWith({
        username: 'learner@example.com',
        password: 'password123456',
      }),
    );
  });

  it('lets the learner show and hide the login password', async () => {
    const controller = createController({
      status: 'anonymous', user: null, preference: null, error: null,
    });
    const screen = await render(
      <AppModelProvider authController={controller}>
        <AuthFormScreen mode="login" onBack={jest.fn()} onSwitch={jest.fn()} />
      </AppModelProvider>,
    );

    const passwordInput = screen.getByPlaceholderText('请输入密码');
    expect(passwordInput.props.secureTextEntry).toBe(true);

    await fireEvent.press(screen.getByRole('button', { name: '显示密码' }));
    expect(passwordInput.props.secureTextEntry).toBe(false);

    await fireEvent.press(screen.getByRole('button', { name: '隐藏密码' }));
    expect(passwordInput.props.secureTextEntry).toBe(true);
  });

  it('exposes an actionable forgot-password entry on login', async () => {
    const controller = createController({
      status: 'anonymous', user: null, preference: null, error: null,
    });
    const onForgotPassword = jest.fn();
    const screen = await render(
      <AppModelProvider authController={controller}>
        <AuthFormScreen
          mode="login"
          onBack={jest.fn()}
          onSwitch={jest.fn()}
          onForgotPassword={onForgotPassword}
        />
      </AppModelProvider>,
    );

    await fireEvent.press(screen.getByRole('button', { name: '忘记密码？' }));
    expect(onForgotPassword).toHaveBeenCalledTimes(1);
  });

  it('completes the email password-reset flow', async () => {
    const controller = createController({
      status: 'anonymous', user: null, preference: null, error: null,
    });
    const onComplete = jest.fn();
    const screen = await render(
      <AppModelProvider authController={controller}>
        <PasswordResetScreen onBack={jest.fn()} onComplete={onComplete} />
      </AppModelProvider>,
    );

    await fireEvent.changeText(screen.getByPlaceholderText('name@example.com'), 'learner@example.com');
    await fireEvent.press(screen.getByRole('button', { name: '发送重置验证码' }));
    await waitFor(() => expect(screen.getByText('设置新密码')).toBeTruthy());

    await fireEvent.changeText(screen.getByPlaceholderText('000000'), '123456');
    await fireEvent.changeText(screen.getByPlaceholderText('至少 12 位字符'), 'new-password123');
    await fireEvent.changeText(screen.getByPlaceholderText('再次输入新密码'), 'new-password123');
    await fireEvent.press(screen.getByRole('button', { name: '确认重置' }));

    await waitFor(() => expect(controller.resetPassword).toHaveBeenCalledWith({
      email: 'learner@example.com',
      password: 'new-password123',
      challengeId: 'reset-challenge-1',
      code: '123456',
    }));
    await waitFor(() => expect(screen.getByText('密码已重置')).toBeTruthy());
    await fireEvent.press(screen.getByRole('button', { name: '返回登录' }));
    expect(onComplete).toHaveBeenCalledTimes(1);
  });

  it('sends an email code and registers with the verified challenge', async () => {
    const controller = createController({
      status: 'anonymous', user: null, preference: null, error: null,
    });
    const screen = await render(
      <AppModelProvider authController={controller}>
        <AuthFormScreen mode="signup" onBack={jest.fn()} onSwitch={jest.fn()} />
      </AppModelProvider>,
    );

    await fireEvent.changeText(screen.getByPlaceholderText('怎么称呼你'), 'Sunny');
    await fireEvent.changeText(screen.getByPlaceholderText('name@example.com'), 'learner@example.com');
    await fireEvent.changeText(screen.getByPlaceholderText('至少 12 位字符'), 'password123456');
    await fireEvent.changeText(screen.getByPlaceholderText('再次输入密码'), 'password123456');
    await fireEvent.press(screen.getByRole('button', { name: '发送邮箱验证码' }));

    await waitFor(() => expect(screen.getByText('查看你的邮箱')).toBeTruthy());
    expect(controller.issueEmailChallenge).toHaveBeenCalledWith({
      email: 'learner@example.com',
    });

    await fireEvent.changeText(screen.getByPlaceholderText('000000'), '123456');
    await fireEvent.press(screen.getByRole('button', { name: '完成注册' }));
    await waitFor(() => expect(controller.register).toHaveBeenCalledWith({
      username: 'learner@example.com',
      password: 'password123456',
      nickname: 'Sunny',
      challengeId: 'challenge-1',
      code: '123456',
    }));
    await fireEvent.press(screen.getByRole('button', { name: '返回' }));
    expect(screen.getByPlaceholderText('怎么称呼你')).toBeTruthy();
  });

  it('does not send an email code when the passwords do not match', async () => {
    const controller = createController({
      status: 'anonymous', user: null, preference: null, error: null,
    });
    const screen = await render(
      <AppModelProvider authController={controller}>
        <AuthFormScreen mode="signup" onBack={jest.fn()} onSwitch={jest.fn()} />
      </AppModelProvider>,
    );

    await fireEvent.changeText(screen.getByPlaceholderText('怎么称呼你'), 'Sunny');
    await fireEvent.changeText(screen.getByPlaceholderText('name@example.com'), 'learner@example.com');
    await fireEvent.changeText(screen.getByPlaceholderText('至少 12 位字符'), 'password123456');
    await fireEvent.changeText(screen.getByPlaceholderText('再次输入密码'), 'different123456');
    await fireEvent.press(screen.getByRole('button', { name: '发送邮箱验证码' }));

    expect(screen.getByText('两次输入的密码不一致')).toBeTruthy();
    expect(controller.issueEmailChallenge).not.toHaveBeenCalled();
  });

  it('toggles visibility independently for both signup password fields', async () => {
    const controller = createController({
      status: 'anonymous', user: null, preference: null, error: null,
    });
    const screen = await render(
      <AppModelProvider authController={controller}>
        <AuthFormScreen mode="signup" onBack={jest.fn()} onSwitch={jest.fn()} />
      </AppModelProvider>,
    );

    const passwordInput = screen.getByPlaceholderText('至少 12 位字符');
    const confirmPasswordInput = screen.getByPlaceholderText('再次输入密码');
    expect(passwordInput.props.secureTextEntry).toBe(true);
    expect(confirmPasswordInput.props.secureTextEntry).toBe(true);

    await fireEvent.press(screen.getByRole('button', { name: '显示密码' }));
    expect(passwordInput.props.secureTextEntry).toBe(false);
    expect(confirmPasswordInput.props.secureTextEntry).toBe(true);

    await fireEvent.press(screen.getByRole('button', { name: '显示确认密码' }));
    expect(confirmPasswordInput.props.secureTextEntry).toBe(false);
  });

  it('shows the backend authentication error without changing the layout flow', async () => {
    const controller = createController({
      status: 'anonymous',
      user: null,
      preference: null,
      error: '邮箱或密码错误',
    });
    const screen = await render(
      <AppModelProvider authController={controller}>
        <AuthFormScreen mode="login" onBack={jest.fn()} onSwitch={jest.fn()} />
      </AppModelProvider>,
    );

    expect(screen.getByText('邮箱或密码错误')).toBeTruthy();
  });

  it('shows a login failure returned by the backend', async () => {
    const login = createController({ status: 'anonymous', user: null, preference: null, error: null });
    login.login.mockRejectedValueOnce(new Error('登录暂不可用'));
    const loginView = await render(<AppModelProvider authController={login}><AuthFormScreen mode="login" onBack={jest.fn()} onSwitch={jest.fn()} /></AppModelProvider>);
    await fireEvent.changeText(loginView.getByPlaceholderText('name@example.com'), 'learner@example.com');
    await fireEvent.changeText(loginView.getByPlaceholderText('请输入密码'), 'password123456');
    await fireEvent.press(loginView.getByRole('button', { name: '登录' }));
    await waitFor(() => expect(loginView.getByText('登录暂不可用')).toBeTruthy());
  });

  it('validates every password-reset field and reports backend failures', async () => {
    const controller = createController({ status: 'anonymous', user: null, preference: null, error: null });
    const view = await render(<AppModelProvider authController={controller}><PasswordResetScreen onBack={jest.fn()} onComplete={jest.fn()} /></AppModelProvider>);
    await fireEvent(view.getByPlaceholderText('name@example.com'), 'submitEditing');
    expect(view.getByText('请输入有效的邮箱地址')).toBeTruthy();
    controller.issuePasswordResetChallenge.mockRejectedValueOnce(new Error('重置服务失败'));
    await fireEvent.changeText(view.getByPlaceholderText('name@example.com'), 'learner@example.com');
    await fireEvent.press(view.getByRole('button', { name: '发送重置验证码' }));
    await waitFor(() => expect(view.getByRole('alert')).toBeTruthy());
    controller.issuePasswordResetChallenge.mockResolvedValueOnce({ challengeId: 'reset', expiresInSeconds: 600, resendAfterSeconds: 0 });
    await fireEvent.press(view.getByRole('button', { name: '发送重置验证码' }));
    await waitFor(() => expect(view.getByText('设置新密码')).toBeTruthy());
    await fireEvent.press(view.getByRole('button', { name: '确认重置' }));
    expect(view.getByText('请输入 6 位邮箱验证码')).toBeTruthy();
    await fireEvent.changeText(view.getByPlaceholderText('000000'), '123456');
    await fireEvent.changeText(view.getByPlaceholderText('至少 12 位字符'), 'short');
    await fireEvent.press(view.getByRole('button', { name: '确认重置' }));
    expect(view.getByText('密码至少需要 12 位字符')).toBeTruthy();
    await fireEvent.changeText(view.getByPlaceholderText('至少 12 位字符'), 'new-password123');
    await fireEvent.changeText(view.getByPlaceholderText('再次输入新密码'), 'different-pass');
    await fireEvent.press(view.getByRole('button', { name: '确认重置' }));
    expect(view.getByText('两次输入的密码不一致')).toBeTruthy();
    await fireEvent.press(view.getByRole('button', { name: '显示新密码' }));
    await fireEvent.press(view.getByRole('button', { name: '显示确认新密码' }));
    controller.resetPassword.mockRejectedValueOnce(new Error('更新失败'));
    await fireEvent.changeText(view.getByPlaceholderText('再次输入新密码'), 'new-password123');
    await fireEvent.press(view.getByRole('button', { name: '确认重置' }));
    await waitFor(() => expect(view.getByRole('alert')).toBeTruthy());
  });

  it('handles signup challenge and registration failures through keyboard and resend actions', async () => {
    jest.spyOn(AccessibilityInfo, 'isReduceMotionEnabled').mockResolvedValueOnce(true);
    const controller = createController({ status: 'anonymous', user: null, preference: null, error: null });
    controller.issueEmailChallenge
      .mockRejectedValueOnce('challenge unavailable')
      .mockResolvedValue({ challengeId: 'retry-challenge', expiresInSeconds: 600, resendAfterSeconds: 0 });
    controller.register.mockRejectedValueOnce('registration unavailable');
    const view = await render(<AppModelProvider authController={controller}><AuthFormScreen mode="signup" onBack={jest.fn()} onSwitch={jest.fn()} /></AppModelProvider>);
    await fireEvent.changeText(view.getByPlaceholderText('怎么称呼你'), 'Sunny');
    await fireEvent.changeText(view.getByPlaceholderText('name@example.com'), 'learner@example.com');
    await fireEvent.changeText(view.getByPlaceholderText('至少 12 位字符'), 'password123456');
    await fireEvent.changeText(view.getByPlaceholderText('再次输入密码'), 'password123456');
    await fireEvent(view.getByPlaceholderText('至少 12 位字符'), 'submitEditing');
    await waitFor(() => expect(view.getByRole('alert')).toBeTruthy());
    await fireEvent.press(view.getByRole('button', { name: '发送邮箱验证码' }));
    await waitFor(() => expect(view.getByText('查看你的邮箱')).toBeTruthy());
    await fireEvent(view.getByPlaceholderText('000000'), 'submitEditing');
    expect(view.getByText('请输入 6 位邮箱验证码')).toBeTruthy();
    await fireEvent.press(view.getByText('重新发送验证码'));
    await fireEvent.changeText(view.getByPlaceholderText('000000'), '123456');
    await fireEvent(view.getByPlaceholderText('000000'), 'submitEditing');
    await waitFor(() => expect(view.getByText('请求失败，请稍后重试')).toBeTruthy());
    view.unmount();
  });

  it('returns from password entry to the reset email step', async () => {
    const controller = createController({ status: 'anonymous', user: null, preference: null, error: null });
    controller.issuePasswordResetChallenge.mockResolvedValueOnce({ challengeId: 'reset', expiresInSeconds: 600, resendAfterSeconds: 0 });
    const onBack = jest.fn();
    const view = await render(<AppModelProvider authController={controller}><PasswordResetScreen onBack={onBack} onComplete={jest.fn()} /></AppModelProvider>);
    await fireEvent.changeText(view.getByPlaceholderText('name@example.com'), 'learner@example.com');
    await fireEvent.press(view.getByText('发送重置验证码'));
    await waitFor(() => expect(view.getByText('设置新密码')).toBeTruthy());
    await fireEvent.press(view.getByRole('button', { name: '返回' }));
    expect(view.getByText('找回账号')).toBeTruthy();
    expect(onBack).not.toHaveBeenCalled();
    view.unmount();
  });
});
