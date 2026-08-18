import { Tabs, usePathname, useRouter } from 'expo-router';
import { BookOpenTextIcon } from 'phosphor-react-native/src/icons/BookOpenText';
import { SquaresFourIcon } from 'phosphor-react-native/src/icons/SquaresFour';
import { UserIcon } from 'phosphor-react-native/src/icons/User';
import { WaveformIcon } from 'phosphor-react-native/src/icons/Waveform';
import { BackHandler, Platform } from 'react-native';
import { useEffect, useState } from 'react';

import { LiquidGlassTabBar } from '@/components/LiquidGlassTabBar';
import { useAnalytics } from '@/model/AnalyticsProvider';
import { colors } from '@/theme/tokens';
import { LearningStageProvider } from '@/navigation/learningStage';
import { routes } from '@/navigation/routes';
import { forgetSpecialty, readRememberedSpecialty, rememberSpecialty } from '@/navigation/specialtyMemory';

export default function TabsLayout() {
  const pathname = usePathname();
  const router = useRouter();
  const analytics = useAnalytics();
  const [immersiveLearning, setImmersiveLearning] = useState(false);
  const hideTabBar = immersiveLearning;

  useEffect(() => {
    if (Platform.OS !== 'android') return;

    const rootTab = pathname === '/conversation'
      || pathname === '/learning'
      || pathname === '/profile';
    if (!rootTab) return;

    const subscription = BackHandler.addEventListener('hardwareBackPress', () => {
      // A root tab has no parent screen to pop. Consuming the event prevents
      // Android from closing the Activity; nested screens handle their own back.
      return true;
    });
    return () => subscription.remove();
  }, [pathname]);

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
            analytics.trackModeSelection({ mode: 'FREE_CHAT', pageCode: 'conversation' }, 'tab-navigation');
            void (async () => {
              if (pathname === '/ielts' || pathname === '/interview') await rememberSpecialty(pathname === '/ielts' ? 'ielts' : 'interview');
              router.replace('/(app)/(tabs)/conversation');
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
              analytics.trackModeSelection({ mode: 'SCENE', pageCode: 'scene-training' }, 'tab-navigation');
              void forgetSpecialty().then(() => router.replace('/(app)/(tabs)/(scenes)/scenes'));
            } else if (pathname !== '/scenes') {
              event.preventDefault();
              void readRememberedSpecialty().then((saved) => {
                analytics.trackModeSelection(
                  saved === 'ielts'
                    ? { mode: 'IELTS', pageCode: 'ielts-training' }
                    : saved === 'interview'
                      ? { mode: 'INTERVIEW', pageCode: 'interview-training' }
                      : { mode: 'SCENE', pageCode: 'scene-training' },
                  'tab-navigation',
                );
                router.replace(
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
            const currentSpecialty = pathname === '/ielts'
              ? 'ielts'
              : pathname === '/interview'
                ? 'interview'
                : null;
            const specialtyScene = currentSpecialty !== null;
            const specialtyAssets = pathname.startsWith('/ielts/assets') || pathname.startsWith('/interview/assets');
            if (!specialtyScene && !specialtyAssets && pathname === '/learning') return;
            event.preventDefault();
            void (async () => {
              if (currentSpecialty) {
                analytics.trackLearningAsset(
                  currentSpecialty === 'ielts'
                    ? { mode: 'IELTS', pageCode: 'ielts-assets' }
                    : { mode: 'INTERVIEW', pageCode: 'interview-assets' },
                  'REPORT',
                );
                await rememberSpecialty(currentSpecialty);
                router.replace(
                  currentSpecialty === 'ielts'
                    ? '/(app)/(tabs)/(learning)/ielts/assets'
                    : '/(app)/(tabs)/(learning)/interview/assets',
                );
                return;
              }
              const remembered = await readRememberedSpecialty();
              if (remembered) {
                const specialty = remembered;
                analytics.trackLearningAsset(
                  specialty === 'ielts'
                    ? { mode: 'IELTS', pageCode: 'ielts-assets' }
                    : { mode: 'INTERVIEW', pageCode: 'interview-assets' },
                  'REPORT',
                );
                await rememberSpecialty(specialty);
                router.replace(
                  specialty === 'ielts'
                    ? '/(app)/(tabs)/(learning)/ielts/assets'
                    : '/(app)/(tabs)/(learning)/interview/assets',
                );
                return;
              }
              analytics.trackLearningAsset({ mode: 'SCENE', pageCode: 'scene-assets' }, 'REPORT');
              router.replace('/(app)/(tabs)/(learning)/learning');
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
            if (pathname.startsWith('/profile/')) {
              event.preventDefault();
              return;
            }
            event.preventDefault();
            void (async () => {
              if (pathname === '/ielts' || pathname === '/interview') await rememberSpecialty(pathname === '/ielts' ? 'ielts' : 'interview');
              router.replace('/(app)/(tabs)/profile');
            })();
          },
        }}
      />
      </Tabs>
    </LearningStageProvider>
  );
}
