import { useRouter } from 'expo-router';

import { forgetSpecialty } from '@/navigation/specialtyMemory';
import { routes } from '@/navigation/routes';
import { InterviewFlow } from '@/screens/SpecialtyFlows';

export default function InterviewRoute() {
  const router = useRouter();
  return (
    <InterviewFlow
      onExit={() => void forgetSpecialty().then(() => router.replace('/(app)/(tabs)/(scenes)/scenes'))}
      onViewDetails={() => router.replace(routes.learning.interview.history)}
    />
  );
}
