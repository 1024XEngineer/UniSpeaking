import { fireEvent, render, waitFor } from '@testing-library/react-native';

import type { AuthSessionState } from '@/features/auth/AuthSessionController';
import type { AppModelAuthController } from '@/model/AppModel';
import { AppModelProvider } from '@/model/AppModel';

jest.mock('@/components/TeacherSwipeStack', () => ({
  TeacherSwipeStack: () => null,
}));

import { AuthFormScreen, PasswordResetScreen } from '../AuthScreens';

function createController(state: AuthSessionState): AppModelAuthController & {
  login: jest.Mock;
  register: jest.Mock;
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
});
