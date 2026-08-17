import { useRouter } from 'expo-router';

import type { TrainingMode } from '@/infrastructure/analytics/pageCatalog';
import { useAnalytics } from '@/model/AnalyticsProvider';
import { routes } from '@/navigation/routes';
import { ScenesScreen } from '@/screens/ScenesScreen';

export default function ScenesHomeRoute() {
  const router = useRouter();
  const analytics = useAnalytics();
  const trackMode = (mode: TrainingMode, pageCode: string) => {
    analytics.trackModeSelection({ mode, pageCode }, 'scene-plaza');
  };
  return (
    <ScenesScreen
      onIeltsViewDetails={(recordId) => {
        analytics.trackLearningAsset({ mode: 'IELTS', pageCode: 'ielts-assets' }, 'REPORT');
        router.replace(routes.learning.ielts.record(recordId));
      }}
      onSceneViewDetails={(sceneId) => {
        analytics.trackLearningAsset({ mode: 'SCENE', pageCode: 'scene-assets' }, 'REPORT');
        router.replace(routes.learning.sceneDetail(sceneId));
      }}
      onOpenIelts={() => {
        trackMode('IELTS', 'ielts-training');
        router.navigate(routes.specialty.ielts);
      }}
      onOpenInterview={() => {
        trackMode('INTERVIEW', 'interview-training');
        router.navigate(routes.specialty.interview);
      }}
      onStartScene={() => trackMode('SCENE', 'scene-training')}
    />
  );
}
