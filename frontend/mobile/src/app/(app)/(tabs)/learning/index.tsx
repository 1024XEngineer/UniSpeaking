import { useRouter } from 'expo-router';

import { routes } from '@/navigation/routes';
import { AssetsScreen } from '@/screens/AssetsScreen';

export default function LearningHomeRoute() {
  const router = useRouter();
  return (
    <AssetsScreen
      onOpenRecord={(record) => router.push(routes.learning.sceneDetail(record.id))}
      onOpenIelts={() => router.push(routes.learning.ielts.overview)}
      onOpenInterview={() => router.push(routes.learning.interview.overview)}
    />
  );
}
