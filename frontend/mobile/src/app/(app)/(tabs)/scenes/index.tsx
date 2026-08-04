import { useRouter } from 'expo-router';

import { routes } from '@/navigation/routes';
import { ScenesHome, type SceneRoute } from '@/screens/ScenesScreen';

export default function ScenesHomeRoute() {
  const router = useRouter();

  const open = (route: SceneRoute) => {
    if (route.name === 'training') router.push(routes.scenes.training(route.id));
    else if (route.name === 'ielts') router.push(routes.specialty.ielts);
    else if (route.name === 'interview') router.push(routes.specialty.interview);
  };

  return <ScenesHome onOpen={open} />;
}
