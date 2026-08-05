import { useRouter } from 'expo-router';

import { routes } from '@/navigation/routes';
import { TeacherOnboardingScreen } from '@/screens/AuthScreens';

export default function TeacherRoute() {
  const router = useRouter();
  return <TeacherOnboardingScreen onComplete={() => router.replace(routes.tabs.conversation)} />;
}
