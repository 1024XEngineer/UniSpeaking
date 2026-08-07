import { useRouter } from 'expo-router';

import { routes } from '@/navigation/routes';
import { ConversationScreen } from '@/screens/ConversationScreen';

export default function ConversationHomeRoute() {
  const router = useRouter();
  return <ConversationScreen onStartCall={() => router.push(routes.conversation.call)} />;
}
