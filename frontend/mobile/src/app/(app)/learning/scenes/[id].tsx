import { useLocalSearchParams, useRouter } from 'expo-router';

import { routes } from '@/navigation/routes';
import { SceneAssetDetailLoader } from '@/screens/AssetsScreen';

export default function SceneLearningDetailRoute() {
  const router = useRouter();
  const { id } = useLocalSearchParams<{ id: string }>();

  return (
    <SceneAssetDetailLoader
      sceneId={id}
      onBack={() => router.back()}
      onPractice={() => router.push(routes.scenes.training(id, 'speak'))}
      onDelete={() => router.replace(routes.tabs.learning)}
    />
  );
}
