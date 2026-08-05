import { useRouter } from 'expo-router';

import { AccountSettings } from '@/screens/ProfileScreen';

export default function AccountRoute() {
  const router = useRouter();
  return <AccountSettings onBack={() => router.back()} />;
}
