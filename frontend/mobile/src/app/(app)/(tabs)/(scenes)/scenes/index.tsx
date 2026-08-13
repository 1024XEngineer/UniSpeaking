import { useRouter } from 'expo-router';

import { routes } from '@/navigation/routes';
import { ScenesScreen } from '@/screens/ScenesScreen';

export default function ScenesHomeRoute() {
  const router = useRouter();
  return (
    <ScenesScreen
      onIeltsViewDetails={(recordId) => router.replace(routes.learning.ielts.record(recordId))}
      onSceneViewDetails={(sceneId) => router.replace(routes.learning.sceneDetail(sceneId))}
      onOpenIelts={() => router.navigate(routes.specialty.ielts)}
      onOpenInterview={() => router.navigate(routes.specialty.interview)}
    />
  );
}
