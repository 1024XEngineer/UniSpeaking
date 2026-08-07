import { useRouter } from 'expo-router';

import { Insights } from '@/screens/ProfileScreen';

export default function InsightsRoute() {
  const router = useRouter();
  return <Insights onBack={() => router.back()} />;
}
