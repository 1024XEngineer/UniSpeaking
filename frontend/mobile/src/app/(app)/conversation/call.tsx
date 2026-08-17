import { useRouter } from 'expo-router';

import { useAnalytics } from '@/model/AnalyticsProvider';
import { CallScreen } from '@/screens/ConversationScreen';

export default function ConversationCallRoute() {
  const router = useRouter();
  const analytics = useAnalytics();
  return <CallScreen analytics={analytics} onEnd={() => router.back()} />;
}
