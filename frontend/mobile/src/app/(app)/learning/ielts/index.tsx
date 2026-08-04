import { useRouter } from 'expo-router';

import { routes } from '@/navigation/routes';
import { SpecialtyAssetsScreen } from '@/screens/SpecialtyAssetsScreen';

export default function IeltsAssetsOverviewRoute() {
  const router = useRouter();
  return <SpecialtyAssetsScreen kind="ielts" tab="overview" onTabChange={(tab) => router.replace(routes.learning.ielts[tab])} onScenes={() => router.replace(routes.tabs.learning)} onIelts={() => undefined} onInterview={() => router.replace(routes.learning.interview.overview)} />;
}
