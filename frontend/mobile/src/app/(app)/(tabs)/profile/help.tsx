import { useRouter } from 'expo-router';

import { HelpCenter } from '@/screens/ProfileScreen';

export default function HelpRoute() {
  const router = useRouter();
  return <HelpCenter onBack={() => router.back()} />;
}
