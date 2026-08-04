import { useRouter } from 'expo-router';

import { AssistantSettings } from '@/screens/ProfileScreen';

export default function AssistantRoute() {
  const router = useRouter();
  return <AssistantSettings onBack={() => router.back()} />;
}
