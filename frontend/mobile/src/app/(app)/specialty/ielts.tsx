import { Redirect } from 'expo-router';

import { routes } from '@/navigation/routes';

export default function IeltsRoute() {
  return <Redirect href={routes.specialty.ielts} />;
}
