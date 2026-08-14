import { useRouter } from 'expo-router';

import { Membership } from '@/screens/ProfileScreen';

export default function MembershipRoute() {
  const router = useRouter();
  return <Membership onBack={() => router.back()} />;
}
