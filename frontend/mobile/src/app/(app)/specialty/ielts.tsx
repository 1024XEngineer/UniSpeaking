import { useRouter } from 'expo-router';

import { IeltsFlow } from '@/screens/SpecialtyFlows';

export default function IeltsRoute() {
  const router = useRouter();
  return <IeltsFlow onExit={() => router.back()} />;
}
