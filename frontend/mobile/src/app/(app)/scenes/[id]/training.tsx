import { useLocalSearchParams, useRouter } from 'expo-router';

import { routes } from '@/navigation/routes';
import { Training } from '@/screens/ScenesScreen';
import type { GeneratedScene } from '@/features/scenes/SceneService';

export default function ScenarioTrainingRoute() {
  const router = useRouter();
  const { id = 'coffee', stage, scene: encodedScene } = useLocalSearchParams<{ id: string; stage?: string; scene?: string }>();
  let scene: GeneratedScene | undefined;
  if (encodedScene) {
    try {
      scene = JSON.parse(encodedScene) as GeneratedScene;
    } catch {
      scene = undefined;
    }
  }
  return (
    <Training
      id={id}
      scene={scene}
      initialStage={stage === 'speak' ? 'speak' : undefined}
      onBack={() => router.replace(routes.tabs.scenes)}
      onFinish={() => router.replace(routes.tabs.scenes)}
      onViewDetails={(recordId) => router.replace(routes.learning.sceneDetail(recordId))}
    />
  );
}
