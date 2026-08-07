import { useRouter } from 'expo-router';

import { forgetSpecialty } from '@/navigation/specialtyMemory';
import { routes } from '@/navigation/routes';
import { IeltsFlow } from '@/screens/SpecialtyFlows';

export default function IeltsRoute() {
  const router = useRouter();
  return (
    <IeltsFlow
      onExit={() => void forgetSpecialty().then(() => router.replace('/(app)/(tabs)/(scenes)/scenes'))}
      onViewDetails={() => router.replace(routes.learning.ielts.history)}
    />
  );
}
