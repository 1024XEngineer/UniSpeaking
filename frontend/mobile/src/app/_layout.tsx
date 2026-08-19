import MaterialIcons from '@expo/vector-icons/MaterialIcons';
import { useFonts } from 'expo-font';
import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { GestureHandlerRootView } from 'react-native-gesture-handler';

import { DevicePreviewFrame } from '@/components/DevicePreviewFrame';
import { AnalyticsProvider } from '@/model/AnalyticsProvider';
import { AppModelProvider, useAppModel } from '@/model/AppModel';
import { TelemetryProvider } from '@/model/TelemetryProvider';
import { initializeMobileTelemetry, wrapTelemetryRoot } from '@/infrastructure/telemetry/MobileTelemetry';

initializeMobileTelemetry();

function RootNavigator() {
  const { isModelReady, isAuthenticated, hasCompletedOnboarding } = useAppModel();

  if (!isModelReady) return null;

  return (
    <Stack
      // The protected app is a single root screen. Let nested stacks own back
      // gestures so an edge swipe cannot pop the whole app and exit.
      screenOptions={{ headerShown: false, animation: 'slide_from_right', gestureEnabled: false }}
    >
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

function RootLayout() {
  const [fontsLoaded] = useFonts(MaterialIcons.font);

  if (!fontsLoaded) return null;

  return (
    <AppModelProvider>
	  <TelemetryProvider>
		<AnalyticsProvider>
		  <StatusBar style="dark" />
		  <GestureHandlerRootView style={{ flex: 1 }}>
			<DevicePreviewFrame>
			  <RootNavigator />
			</DevicePreviewFrame>
		  </GestureHandlerRootView>
		</AnalyticsProvider>
	  </TelemetryProvider>
    </AppModelProvider>
  );
}

export default wrapTelemetryRoot(RootLayout);
