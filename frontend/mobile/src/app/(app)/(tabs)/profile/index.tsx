import { useRouter } from 'expo-router';

import { useAppModel } from '@/model/AppModel';
import { routes } from '@/navigation/routes';
import { ProfileHome, type ProfileRoute } from '@/screens/ProfileScreen';

export default function ProfileHomeRoute() {
  const router = useRouter();
  const { signOut } = useAppModel();

  const open = (route: ProfileRoute) => {
    if (route === 'overview') router.push(routes.profile.overview);
    else if (route === 'insights') router.push(routes.profile.insights);
    else if (route === 'membership') router.push(routes.profile.membership);
    else if (route === 'assistant') router.push(routes.profile.assistant);
    else if (route === 'account') router.push(routes.profile.account);
    else if (route === 'help') router.push(routes.profile.help);
    else if (route === 'about') router.push(routes.profile.about);
  };

  return <ProfileHome onOpen={open} onLogout={signOut} />;
}
