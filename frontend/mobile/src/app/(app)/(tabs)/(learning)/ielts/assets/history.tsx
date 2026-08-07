import { useRouter } from 'expo-router';

import { routes } from '@/navigation/routes';
import { SpecialtyAssetsScreen } from '@/screens/SpecialtyAssetsScreen';

export default function IeltsAssetsHistoryRoute() {
  const router = useRouter();
  return <SpecialtyAssetsScreen kind="ielts" tab="history" onScenes={() => router.replace(routes.tabs.learning)} onIelts={() => undefined} onInterview={() => router.replace(routes.learning.interview.overview)} onOpenRecord={(id) => router.push(routes.learning.ielts.record(id))} />;
}
