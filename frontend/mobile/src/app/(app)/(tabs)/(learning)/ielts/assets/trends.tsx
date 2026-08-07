import { useRouter } from 'expo-router';

import { routes } from '@/navigation/routes';
import { SpecialtyAssetsScreen } from '@/screens/SpecialtyAssetsScreen';

export default function IeltsAssetsTrendsRoute() {
  const router = useRouter();
  return <SpecialtyAssetsScreen kind="ielts" tab="trends" onScenes={() => router.replace(routes.tabs.learning)} onIelts={() => undefined} onInterview={() => router.replace(routes.learning.interview.overview)} />;
}
