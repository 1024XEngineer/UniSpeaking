import { Redirect } from 'expo-router';

import { routes } from '@/navigation/routes';

export default function ScenarioIntroRoute() {
  return <Redirect href={routes.tabs.scenes} />;
}
