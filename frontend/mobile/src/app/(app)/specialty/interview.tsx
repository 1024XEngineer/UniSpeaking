import { useRouter } from 'expo-router';

import { InterviewFlow } from '@/screens/SpecialtyFlows';

export default function InterviewRoute() {
  const router = useRouter();
  return <InterviewFlow onExit={() => router.back()} />;
}
