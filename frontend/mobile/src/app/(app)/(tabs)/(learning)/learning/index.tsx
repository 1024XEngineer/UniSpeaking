import { useRouter } from 'expo-router';

import { useAnalytics } from '@/model/AnalyticsProvider';
import { routes } from '@/navigation/routes';
import { AssetsScreen } from '@/screens/AssetsScreen';

export default function LearningHomeRoute() {
  const router = useRouter();
  const analytics = useAnalytics();
  return (
    <AssetsScreen
      onOpenRecord={(record) => {
        analytics.trackLearningAsset({ mode: 'SCENE', pageCode: 'scene-assets' }, 'REPORT');
        router.push(routes.learning.sceneDetail(record.id));
      }}
      onOpenIelts={() => {
        analytics.trackLearningAsset({ mode: 'IELTS', pageCode: 'ielts-assets' }, 'REPORT');
        router.push(routes.learning.ielts.overview);
      }}
      onOpenInterview={() => {
        analytics.trackLearningAsset({ mode: 'INTERVIEW', pageCode: 'interview-assets' }, 'REPORT');
        router.push(routes.learning.interview.overview);
      }}
    />
  );
}
