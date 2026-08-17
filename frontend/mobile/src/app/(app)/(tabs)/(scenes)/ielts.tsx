import { useRouter } from 'expo-router';

import { forgetSpecialty } from '@/navigation/specialtyMemory';
import { useAnalytics } from '@/model/AnalyticsProvider';
import { routes } from '@/navigation/routes';
import { IeltsFlow } from '@/screens/SpecialtyFlows';

export default function IeltsRoute() {
  const router = useRouter();
  const analytics = useAnalytics();
  return (
    <IeltsFlow
      analytics={analytics}
      onExit={() => void forgetSpecialty().then(() => router.replace('/(app)/(tabs)/(scenes)/scenes'))}
      onViewDetails={(recordId) => router.replace(routes.learning.ielts.record(recordId))}
    />
  );
}
