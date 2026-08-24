import { fireEvent, render } from '@testing-library/react-native';

jest.mock('@/screens/ConversationScreen', () => ({ ConversationScreen: () => null }));
jest.mock('@/screens/ScenesScreen', () => ({ ScenesScreen: () => null }));
jest.mock('@/screens/AssetsScreen', () => ({ AssetsScreen: () => null }));
jest.mock('@/screens/ProfileScreen', () => ({ ProfileScreen: () => null }));
jest.mock('react-native-safe-area-context', () => ({ useSafeAreaInsets: () => ({ top: 0, right: 0, bottom: 8, left: 0 }) }));

import { MainApp } from '../MainApp';

describe('MainApp navigation shell', () => {
  it('switches between all tabs and hides the tab bar for immersive conversation', async () => {
    const view = await render(<MainApp />);
    expect(view.getByLabelText('对话')).toBeTruthy();
    await fireEvent.press(view.getByLabelText('场景'));
    await fireEvent.press(view.getByLabelText('资产'));
    await fireEvent.press(view.getByLabelText('我的'));
    expect(view.getAllByRole('tab')).toHaveLength(4);
    view.unmount();
  });
});
