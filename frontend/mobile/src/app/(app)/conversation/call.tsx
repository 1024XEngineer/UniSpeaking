import { useRouter } from 'expo-router';

import { CallScreen } from '@/screens/ConversationScreen';

export default function ConversationCallRoute() {
  const router = useRouter();
  return <CallScreen onEnd={() => router.back()} />;
}
