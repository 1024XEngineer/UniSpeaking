import { useLocalSearchParams, useRouter } from 'expo-router';

import { routes } from '@/navigation/routes';
import { SceneAssetDetailLoader } from '@/screens/AssetsScreen';
import { LearningAssetService } from '@/features/scenes/LearningAssetService';
import { SecureTokenStore } from '@/infrastructure/auth/SecureTokenStore';
import { getRuntimeConfig } from '@/infrastructure/config/runtimeConfig';
import { ApiClient } from '@/infrastructure/http/ApiClient';

export default function SceneLearningDetailRoute() {
  const router = useRouter();
  const { id } = useLocalSearchParams<{ id: string }>();

  const practice = async () => {
    const tokenStore = new SecureTokenStore();
    const service = new LearningAssetService(new ApiClient({
      baseUrl: getRuntimeConfig().backendUrl,
      tokenStore,
    }));
    const scene = await service.getScene(id);
    router.push({
      pathname: '/scenes/[id]/training',
      params: { id, scene: JSON.stringify(scene), stage: 'speak' },
    });
  };

  return (
    <SceneAssetDetailLoader
      sceneId={id}
      onBack={() => router.replace(routes.tabs.learning)}
      onPractice={() => void practice()}
      onDelete={() => router.replace(routes.tabs.learning)}
    />
  );
}
