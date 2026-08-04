import { useRouter } from 'expo-router';

import { routes } from '@/navigation/routes';
import { AuthFormScreen } from '@/screens/AuthScreens';

export default function SignupRoute() {
  const router = useRouter();
  return (
    <AuthFormScreen
      mode="signup"
      onBack={() => router.back()}
      onSwitch={() => router.replace(routes.public.login)}
    />
  );
}
