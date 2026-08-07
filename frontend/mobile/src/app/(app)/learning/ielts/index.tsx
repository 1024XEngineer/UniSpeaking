import { Redirect } from 'expo-router';

import { routes } from '@/navigation/routes';

export default function IeltsAssetsOverviewRoute() {
  return <Redirect href={routes.learning.ielts.overview} />;
}
