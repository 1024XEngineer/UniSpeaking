import { useRouter } from 'expo-router';

import { AboutProduct } from '@/screens/ProfileScreen';

export default function AboutRoute() {
  const router = useRouter();
  return <AboutProduct onBack={() => router.back()} />;
}
