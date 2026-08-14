import { useRouter } from 'expo-router';

import { Overview } from '@/screens/ProfileScreen';

export default function OverviewRoute() {
  const router = useRouter();
  return <Overview onBack={() => router.back()} />;
}
