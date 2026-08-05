import MaterialIcons from '@expo/vector-icons/MaterialIcons';
import { useFonts } from 'expo-font';
import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { GestureHandlerRootView } from 'react-native-gesture-handler';

import { DevicePreviewFrame } from '@/components/DevicePreviewFrame';
import { AppModelProvider, useAppModel } from '@/model/AppModel';

function RootNavigator() {
  const { isModelReady, isAuthenticated, hasCompletedOnboarding } = useAppModel();

  if (!isModelReady) return null;

  return (
    <Stack screenOptions={{ headerShown: false, animation: 'slide_from_right' }}>
      <Stack.Screen name="index" />
      <Stack.Protected guard={!isAuthenticated}>
        <Stack.Screen name="(public)" />
      </Stack.Protected>
      <Stack.Protected guard={isAuthenticated && !hasCompletedOnboarding}>
        <Stack.Screen name="(onboarding)" />
      </Stack.Protected>
      <Stack.Protected guard={isAuthenticated && hasCompletedOnboarding}>
        <Stack.Screen name="(app)" />
      </Stack.Protected>
    </Stack>
  );
}

export default function RootLayout() {
  const [fontsLoaded] = useFonts(MaterialIcons.font);

  if (!fontsLoaded) return null;

  return (
    <AppModelProvider>
      <StatusBar style="dark" />
      <GestureHandlerRootView style={{ flex: 1 }}>
        <DevicePreviewFrame>
          <RootNavigator />
        </DevicePreviewFrame>
      </GestureHandlerRootView>
    </AppModelProvider>
  );
}
