import { useRouter } from 'expo-router';

import { routes } from '@/navigation/routes';
import { SpecialtyAssetsScreen } from '@/screens/SpecialtyAssetsScreen';

export default function InterviewAssetsTrendsRoute() {
  const router = useRouter();
  return <SpecialtyAssetsScreen kind="interview" tab="trends" onTabChange={(tab) => router.replace(routes.learning.interview[tab])} onScenes={() => router.replace(routes.tabs.learning)} onIelts={() => router.replace(routes.learning.ielts.overview)} onInterview={() => undefined} />;
}
