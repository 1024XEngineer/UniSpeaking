import { useRouter } from 'expo-router';

import { routes } from '@/navigation/routes';
import { HelpCenter } from '@/screens/ProfileScreen';

export default function HelpRoute() {
  const router = useRouter();
  return (
    <HelpCenter
      onBack={() => router.back()}
      onOpenCategory={(categoryId) => router.push(routes.profile.helpCategory(categoryId))}
    />
  );
}
