import { useRouter } from 'expo-router';

import { useAppModel } from '@/model/AppModel';
import { AccountSettings } from '@/screens/ProfileScreen';

export default function AccountRoute() {
  const router = useRouter();
  const { signOut } = useAppModel();
  return <AccountSettings onBack={() => router.back()} onLogout={signOut} />;
}
