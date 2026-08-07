import { Redirect } from 'expo-router';

import { routes } from '@/navigation/routes';

export default function IeltsAssetsHistoryRoute() {
  return <Redirect href={routes.learning.ielts.history} />;
}
