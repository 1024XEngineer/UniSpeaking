import { cleanup, fireEvent, render } from '@testing-library/react-native';
import { Platform, Text, View } from 'react-native';
import type { BottomTabBarProps } from 'expo-router/build/react-navigation/bottom-tabs';

import { LiquidGlassTabBar } from '../LiquidGlassTabBar';

const mockIsGlassEffectAPIAvailable = jest.fn();
const mockGlassView = jest.fn();

jest.mock('expo-glass-effect', () => {
  const React = require('react');
  const { View: NativeView } = require('react-native');

  return {
    GlassView: (props: object) => {
      mockGlassView(props);
      return React.createElement(NativeView, { testID: 'glass-layer', ...props });
    },
    isGlassEffectAPIAvailable: () => mockIsGlassEffectAPIAvailable(),
  };
});

type TabDefinition = {
  key: string;
  name: string;
  params?: object;
  options: Record<string, unknown>;
};

function propsFor(tabs: TabDefinition[], selectedIndex = 0, defaultPrevented = false): BottomTabBarProps {
  const emit = jest.fn((event) => ({ ...event, defaultPrevented }));

  return {
    state: {
      index: selectedIndex,
      key: 'tabs',
      routeNames: tabs.map((tab) => tab.name),
      routes: tabs.map(({ key, name, params }) => ({ key, name, params })),
      type: 'tab',
      stale: false,
      history: [],
    },
    descriptors: Object.fromEntries(tabs.map((tab) => [tab.key, { options: tab.options }])) as BottomTabBarProps['descriptors'],
    navigation: { emit, navigate: jest.fn() } as unknown as BottomTabBarProps['navigation'],
    insets: { top: 0, right: 0, bottom: 12, left: 0 },
  } as unknown as BottomTabBarProps;
}

describe('LiquidGlassTabBar', () => {
  const originalOS = Platform.OS;

  beforeEach(() => {
    mockGlassView.mockClear();
    mockIsGlassEffectAPIAvailable.mockReturnValue(true);
    Object.defineProperty(Platform, 'OS', { configurable: true, value: 'ios' });
  });

  afterEach(async () => {
    await cleanup();
  });

  afterAll(() => {
    Object.defineProperty(Platform, 'OS', { configurable: true, value: originalOS });
  });

  it('renders accessible tabs with focused state, icons, and native glass', async () => {
    const homeIcon = jest.fn(({ color, focused }: { color: string; focused: boolean }) => (
      <Text>{`home:${color}:${focused}`}</Text>
    ));
    const settingsIcon = jest.fn(({ color, focused }: { color: string; focused: boolean }) => (
      <Text>{`settings:${color}:${focused}`}</Text>
    ));
    const props = propsFor([
      {
        key: 'home-key',
        name: 'Home',
        options: {
          tabBarAccessibilityLabel: 'Home tab',
          tabBarButtonTestID: 'home-tab',
          tabBarActiveTintColor: '#123456',
          tabBarIcon: homeIcon,
        },
      },
      {
        key: 'settings-key',
        name: 'Settings',
        options: { title: 'Settings title', tabBarInactiveTintColor: '#abcdef', tabBarIcon: settingsIcon },
      },
    ]);

    const view = await render(<LiquidGlassTabBar {...props} />);

    const homeTab = view.getByLabelText('Home tab');
    expect(homeTab.props.accessibilityState).toEqual({ selected: true });
    expect(view.getByLabelText('Settings title').props.accessibilityState).toEqual({ selected: false });
    expect(view.getByTestId('home-tab')).toBeTruthy();
    expect(view.getByText('home:#123456:true')).toBeTruthy();
    expect(view.getByText('settings:#abcdef:false')).toBeTruthy();
    expect(homeIcon).toHaveBeenCalledWith({ color: '#123456', focused: true, size: 25 });
    expect(settingsIcon).toHaveBeenCalledWith({ color: '#abcdef', focused: false, size: 25 });
    expect(mockGlassView).toHaveBeenLastCalledWith(expect.objectContaining({ glassEffectStyle: 'clear', isInteractive: true }));
  });

  it('emits tab events, navigates only for an unprevented inactive tab, and handles long presses', async () => {
    mockIsGlassEffectAPIAvailable.mockReturnValue(false);
    const props = propsFor([
      { key: 'active', name: 'Active', options: {} },
      { key: 'other', name: 'Other', params: { source: 'dock' }, options: {} },
    ]);
    const { emit, navigate } = props.navigation as unknown as { emit: jest.Mock; navigate: jest.Mock };
    const view = await render(<LiquidGlassTabBar {...props} />);

    await fireEvent.press(view.getByLabelText('Active'));
    await fireEvent.press(view.getByLabelText('Other'));
    await fireEvent(view.getByLabelText('Other'), 'longPress');

    expect(emit).toHaveBeenNthCalledWith(1, { type: 'tabPress', target: 'active', canPreventDefault: true });
    expect(emit).toHaveBeenNthCalledWith(2, { type: 'tabPress', target: 'other', canPreventDefault: true });
    expect(emit).toHaveBeenNthCalledWith(3, { type: 'tabLongPress', target: 'other' });
    expect(navigate).toHaveBeenCalledWith('Other', { source: 'dock' });
    expect(mockGlassView).toHaveBeenLastCalledWith(expect.objectContaining({ glassEffectStyle: 'none' }));
  });

  it('does not navigate when a tab press is prevented and renders safely with no available tabs', async () => {
    const preventedProps = propsFor([
      { key: 'one', name: 'One', options: { tabBarAccessibilityLabel: 'One tab' } },
      { key: 'two', name: 'Two', options: { tabBarAccessibilityLabel: 'Two tab' } },
    ], 0, true);
    const { navigate } = preventedProps.navigation as unknown as { navigate: jest.Mock };
    const prevented = await render(<LiquidGlassTabBar {...preventedProps} />);

    await fireEvent.press(prevented.getByLabelText('Two tab'));
    expect(navigate).not.toHaveBeenCalled();

    Object.defineProperty(Platform, 'OS', { configurable: true, value: 'web' });
    const empty = await render(<LiquidGlassTabBar {...propsFor([])} />);
    expect(empty.queryAllByRole('tab')).toHaveLength(0);
    expect(empty.getByTestId('glass-layer')).toBeTruthy();
  });
});
