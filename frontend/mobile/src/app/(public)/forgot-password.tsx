import { useRouter } from 'expo-router';

import { routes } from '@/navigation/routes';
import { PasswordResetScreen } from '@/screens/AuthScreens';

export default function ForgotPasswordRoute() {
  const router = useRouter();
  return (
    <PasswordResetScreen
      onBack={() => router.back()}
      onComplete={() => router.replace(routes.public.login)}
    />
  );
}
