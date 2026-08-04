import { useLocalSearchParams, useRouter } from 'expo-router';

import { routes } from '@/navigation/routes';
import { Training } from '@/screens/ScenesScreen';

export default function ScenarioTrainingRoute() {
  const router = useRouter();
  const { id = 'coffee', stage } = useLocalSearchParams<{ id: string; stage?: string }>();
  return (
    <Training
      id={id}
      initialStage={stage === 'speak' ? 'speak' : undefined}
      onBack={() => router.replace(routes.tabs.scenes)}
      onFinish={() => router.replace(routes.tabs.scenes)}
    />
  );
}
