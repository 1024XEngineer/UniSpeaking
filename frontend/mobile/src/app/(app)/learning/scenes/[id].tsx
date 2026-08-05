import { Redirect, useLocalSearchParams, useRouter } from 'expo-router';

import { useAppModel } from '@/model/AppModel';
import { routes } from '@/navigation/routes';
import { SceneAssetDetail } from '@/screens/AssetsScreen';

export default function SceneLearningDetailRoute() {
  const router = useRouter();
  const { id } = useLocalSearchParams<{ id: string }>();
  const { sceneRecords, removeSceneRecord } = useAppModel();
  const record = sceneRecords.find((item) => item.id === id);

  if (!record) return <Redirect href={routes.tabs.learning} />;

  return (
    <SceneAssetDetail
      record={record}
      onBack={() => router.back()}
      onPractice={() => router.push(routes.scenes.training(record.id, 'speak'))}
      onDelete={() => {
        removeSceneRecord(record.id);
        router.replace(routes.tabs.learning);
      }}
    />
  );
}
