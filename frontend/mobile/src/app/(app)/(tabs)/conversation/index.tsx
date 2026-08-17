import { useRouter } from 'expo-router';

import { useAnalytics } from '@/model/AnalyticsProvider';
import { routes } from '@/navigation/routes';
import { ConversationScreen } from '@/screens/ConversationScreen';

export default function ConversationHomeRoute() {
  const router = useRouter();
  const analytics = useAnalytics();
  return (
    <ConversationScreen
      onStartCall={() => {
        analytics.trackModeSelection({ mode: 'FREE_CHAT', pageCode: 'conversation' }, 'conversation-home');
        router.push(routes.conversation.call);
      }}
    />
  );
}
