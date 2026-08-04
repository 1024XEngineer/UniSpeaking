import { Redirect } from 'expo-router';

import { useAppModel } from '@/model/AppModel';
import { routes } from '@/navigation/routes';

export default function IndexRoute() {
  const { isAuthenticated, hasCompletedOnboarding } = useAppModel();

  if (!isAuthenticated) return <Redirect href={routes.public.welcome} />;
  if (!hasCompletedOnboarding) return <Redirect href={routes.onboarding.level} />;
  return <Redirect href={routes.tabs.conversation} />;
}
