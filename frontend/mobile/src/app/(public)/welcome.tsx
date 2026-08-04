import { useRouter } from 'expo-router';

import { routes } from '@/navigation/routes';
import { WelcomeScreen } from '@/screens/AuthScreens';

export default function WelcomeRoute() {
  const router = useRouter();
  return (
    <WelcomeScreen
      onLogin={() => router.push(routes.public.login)}
      onSignup={() => router.push(routes.public.signup)}
    />
  );
}
