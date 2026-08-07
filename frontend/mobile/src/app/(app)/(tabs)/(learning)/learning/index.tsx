import { useFocusEffect, useRouter } from 'expo-router';
import { useCallback } from 'react';

import { routes } from '@/navigation/routes';
import { forgetSpecialty } from '@/navigation/specialtyMemory';
import { AssetsScreen } from '@/screens/AssetsScreen';

export default function LearningHomeRoute() {
  const router = useRouter();
  useFocusEffect(useCallback(() => {
    void forgetSpecialty();
  }, []));
  return (
    <AssetsScreen
      onOpenRecord={(record) => router.push(routes.learning.sceneDetail(record.id))}
      onOpenIelts={() => router.push(routes.learning.ielts.overview)}
      onOpenInterview={() => router.push(routes.learning.interview.overview)}
    />
  );
}
