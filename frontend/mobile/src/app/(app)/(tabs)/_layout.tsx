import { Tabs } from 'expo-router';
import { BookOpenTextIcon } from 'phosphor-react-native/src/icons/BookOpenText';
import { SquaresFourIcon } from 'phosphor-react-native/src/icons/SquaresFour';
import { UserIcon } from 'phosphor-react-native/src/icons/User';
import { WaveformIcon } from 'phosphor-react-native/src/icons/Waveform';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { colors } from '@/theme/tokens';

export default function TabsLayout() {
  const insets = useSafeAreaInsets();

  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        tabBarActiveTintColor: colors.ink,
        tabBarInactiveTintColor: '#A1A19C',
        tabBarHideOnKeyboard: true,
        tabBarStyle: {
          height: 76 + insets.bottom,
          paddingTop: 8,
          paddingBottom: insets.bottom + 8,
          borderTopColor: '#E8E7E2',
          backgroundColor: 'rgba(255,255,255,0.98)',
        },
        tabBarLabelStyle: {
          fontSize: 13,
          fontWeight: '500',
        },
      }}
    >
      <Tabs.Screen
        name="conversation"
        options={{
          title: '对话',
          tabBarIcon: ({ color, focused }) => (
            <WaveformIcon color={color as string} size={30} weight={focused ? 'bold' : 'regular'} />
          ),
        }}
      />
      <Tabs.Screen
        name="scenes"
        options={{
          title: '场景',
          tabBarIcon: ({ color, focused }) => (
            <SquaresFourIcon color={color as string} size={30} weight={focused ? 'bold' : 'regular'} />
          ),
        }}
      />
      <Tabs.Screen
        name="learning"
        options={{
          title: '资产',
          tabBarIcon: ({ color, focused }) => (
            <BookOpenTextIcon color={color as string} size={30} weight={focused ? 'bold' : 'regular'} />
          ),
        }}
      />
      <Tabs.Screen
        name="profile"
        options={{
          title: '我的',
          tabBarIcon: ({ color, focused }) => (
            <UserIcon color={color as string} size={30} weight={focused ? 'bold' : 'regular'} />
          ),
        }}
      />
    </Tabs>
  );
}
