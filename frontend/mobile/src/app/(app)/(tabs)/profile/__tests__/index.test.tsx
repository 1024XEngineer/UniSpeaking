import { fireEvent, render } from '@testing-library/react-native';
import { useRouter } from 'expo-router';

import ProfileHomeRoute from '../index';

jest.mock('expo-router', () => ({
  useRouter: jest.fn(),
}));

jest.mock('@/model/AppModel', () => ({
  useAppModel: () => ({ signOut: jest.fn() }),
}));

jest.mock('@/screens/ProfileScreen', () => {
  const React = jest.requireActual<typeof import('react')>('react');
  const { Pressable, Text } = jest.requireActual<typeof import('react-native')>('react-native');
  return {
    ProfileHome: ({ activeRoute, onOpen }: { activeRoute: string; onOpen: (route: string) => void }) =>
      React.createElement(
        React.Fragment,
        null,
        React.createElement(Text, { testID: 'active-route' }, activeRoute),
        React.createElement(
          Pressable,
          { accessibilityRole: 'button', onPress: () => onOpen('insights') },
          React.createElement(Text, null, '学习目标与洞察'),
        ),
      ),
  };
});

const mockUseRouter = jest.mocked(useRouter);
const push = jest.fn();

describe('ProfileHomeRoute', () => {
  beforeEach(() => {
    push.mockClear();
    mockUseRouter.mockReturnValue({ push } as unknown as ReturnType<typeof useRouter>);
  });

  it('keeps the last opened profile section selected when the child route returns', async () => {
    const view = await render(<ProfileHomeRoute />);

    await fireEvent.press(view.getByRole('button', { name: '学习目标与洞察' }));

    expect(view.getByTestId('active-route')).toHaveTextContent('insights');
    expect(push).toHaveBeenCalledWith('/profile/insights');
  });
});
