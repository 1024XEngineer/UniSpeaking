import { useRouter } from 'expo-router';

import { routes } from '@/navigation/routes';
import { LevelOnboardingScreen } from '@/screens/AuthScreens';

export default function LevelRoute() {
  const router = useRouter();
  return <LevelOnboardingScreen onNext={() => router.push(routes.onboarding.teacher)} />;
}
