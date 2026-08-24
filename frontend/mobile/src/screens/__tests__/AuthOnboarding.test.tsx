import { fireEvent, render } from '@testing-library/react-native';

const mockSetLevel = jest.fn();
const mockComplete = jest.fn(async () => undefined);
jest.mock('@/model/AppModel', () => ({ useAppModel: () => ({ level: 'starter', setLevel: mockSetLevel, teacher: { id: 'clara', name: 'Clara', voiceId: 'Mione', accent: '英式', image: 1 }, setTeacher: jest.fn(), completeOnboarding: mockComplete }) }));
jest.mock('@/components/TeacherSwipeStack', () => ({ TeacherSwipeStack: () => null }));

import { LevelOnboardingScreen, TeacherOnboardingScreen, WelcomeScreen } from '../AuthScreens';

describe('auth onboarding screens', () => {
  it('routes from welcome to login and signup', async () => {
    const onLogin = jest.fn();
    const onSignup = jest.fn();
    const view = await render(<WelcomeScreen onLogin={onLogin} onSignup={onSignup} />);
    await fireEvent.press(view.getByRole('button', { name: '登录' }));
    await fireEvent.press(view.getByRole('button', { name: '注册' }));
    expect(onLogin).toHaveBeenCalledTimes(1);
    expect(onSignup).toHaveBeenCalledTimes(1);
    view.unmount();
  });

  it('selects a level and advances onboarding', async () => {
    const onNext = jest.fn();
    const view = await render(<LevelOnboardingScreen onNext={onNext} />);
    await fireEvent.press(view.getByText('可以简单交流'));
    expect(mockSetLevel).toHaveBeenCalledWith('basic');
    await fireEvent.press(view.getByRole('button', { name: '下一步' }));
    expect(onNext).toHaveBeenCalledTimes(1);
    view.unmount();
  });

  it('completes teacher onboarding after selecting the current teacher', async () => {
    const onComplete = jest.fn();
    const view = await render(<TeacherOnboardingScreen onComplete={onComplete} />);
    expect(view.getByText('选择一位 AI 老师')).toBeTruthy();
    await fireEvent.press(view.getByRole('button', { name: '选择这位老师' }));
    expect(mockComplete).toHaveBeenCalled();
    view.unmount();
  });
});
