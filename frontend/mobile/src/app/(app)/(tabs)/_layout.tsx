import { Tabs, usePathname, useRouter } from 'expo-router';
import { BookOpenTextIcon } from 'phosphor-react-native/src/icons/BookOpenText';
import { SquaresFourIcon } from 'phosphor-react-native/src/icons/SquaresFour';
import { UserIcon } from 'phosphor-react-native/src/icons/User';
import { WaveformIcon } from 'phosphor-react-native/src/icons/Waveform';
import { useState } from 'react';

import { LiquidGlassTabBar } from '@/components/LiquidGlassTabBar';
import { colors } from '@/theme/tokens';
import { LearningStageProvider } from '@/navigation/learningStage';
import { routes } from '@/navigation/routes';
import { forgetSpecialty, readRememberedSpecialty, rememberSpecialty } from '@/navigation/specialtyMemory';

export default function TabsLayout() {
  const pathname = usePathname();
  const router = useRouter();
  const [immersiveLearning, setImmersiveLearning] = useState(false);
  const hideTabBar = immersiveLearning && (pathname === '/ielts' || pathname === '/interview');

  return (
    <LearningStageProvider immersiveLearning={immersiveLearning} setImmersiveLearning={setImmersiveLearning}>
      <Tabs
      screenOptions={{
        headerShown: false,
        tabBarActiveTintColor: colors.ink,
        tabBarInactiveTintColor: '#A1A19C',
        tabBarShowLabel: false,
        tabBarHideOnKeyboard: true,
        tabBarStyle: {
          borderTopWidth: 0,
          backgroundColor: 'transparent',
        },
      }}
      tabBar={(props) => hideTabBar ? null : <LiquidGlassTabBar {...props} />}
    >
      <Tabs.Screen
        name="conversation"
        options={{
          title: '对话',
          tabBarIcon: ({ color, focused }) => (
            <WaveformIcon color={color as string} size={30} weight={focused ? 'bold' : 'regular'} />
          ),
        }}
        listeners={{
          tabPress: (event) => {
            if (pathname === '/conversation') return;
            event.preventDefault();
            void (async () => {
              if (pathname === '/ielts' || pathname === '/interview') await rememberSpecialty(pathname === '/ielts' ? 'ielts' : 'interview');
              router.navigate('/(app)/(tabs)/conversation');
            })();
          },
        }}
      />
      <Tabs.Screen
        name="(scenes)"
        options={{
          title: '场景',
          href: routes.tabs.scenes,
          tabBarIcon: ({ color, focused }) => (
            <SquaresFourIcon color={color as string} size={30} weight={focused ? 'bold' : 'regular'} />
          ),
        }}
        listeners={{
          tabPress: (event) => {
            if (pathname === '/ielts' || pathname === '/interview') {
              event.preventDefault();
              void forgetSpecialty().then(() => router.replace('/(app)/(tabs)/(scenes)/scenes'));
            } else if (pathname !== '/scenes') {
              event.preventDefault();
              void readRememberedSpecialty().then((saved) => {
                router.navigate(
                  saved === 'ielts'
                    ? '/(app)/(tabs)/(scenes)/ielts'
                    : saved === 'interview'
                      ? '/(app)/(tabs)/(scenes)/interview'
                      : '/(app)/(tabs)/(scenes)/scenes',
                );
              });
            }
          },
        }}
      />
      <Tabs.Screen
        name="(learning)"
        options={{
          title: '资产',
          href: routes.tabs.learning,
          tabBarIcon: ({ color, focused }) => (
            <BookOpenTextIcon color={color as string} size={30} weight={focused ? 'bold' : 'regular'} />
          ),
        }}
        listeners={{
          tabPress: (event) => {
            const specialtyScene = pathname === '/ielts' || pathname === '/interview';
            const specialtyAssets = pathname.startsWith('/ielts/assets') || pathname.startsWith('/interview/assets');
            if (!specialtyScene && !specialtyAssets && pathname === '/learning') return;
            event.preventDefault();
            void (async () => {
              if (specialtyScene) {
                const specialty = pathname === '/ielts' ? 'ielts' : 'interview';
                await rememberSpecialty(specialty);
                router.navigate(
                  specialty === 'ielts'
                    ? '/(app)/(tabs)/(learning)/ielts/assets'
                    : '/(app)/(tabs)/(learning)/interview/assets',
                );
                return;
              }
              await forgetSpecialty();
              router.navigate('/(app)/(tabs)/(learning)/learning');
            })();
          },
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
        listeners={{
          tabPress: (event) => {
            if (pathname === '/profile') return;
            event.preventDefault();
            void (async () => {
              if (pathname === '/ielts' || pathname === '/interview') await rememberSpecialty(pathname === '/ielts' ? 'ielts' : 'interview');
              router.navigate('/(app)/(tabs)/profile');
            })();
          },
        }}
      />
      </Tabs>
    </LearningStageProvider>
  );
}
