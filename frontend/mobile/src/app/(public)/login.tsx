import { useRouter } from 'expo-router';

import { routes } from '@/navigation/routes';
import { AuthFormScreen } from '@/screens/AuthScreens';

export default function LoginRoute() {
  const router = useRouter();
  return (
    <AuthFormScreen
      mode="login"
      onBack={() => router.back()}
      onSwitch={() => router.replace(routes.public.signup)}
    />
  );
}
