import { Redirect } from 'expo-router';

import { routes } from '@/navigation/routes';

export default function IeltsAssetsTrendsRoute() {
  return <Redirect href={routes.learning.ielts.trends} />;
}
