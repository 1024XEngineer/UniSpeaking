import { Redirect, useLocalSearchParams, useRouter } from 'expo-router';

import { useAppModel } from '@/model/AppModel';
import { routes } from '@/navigation/routes';
import { IeltsAssetReport } from '@/screens/SpecialtyAssetsScreen';

export default function IeltsAssetReportRoute() {
  const router = useRouter();
  const { id } = useLocalSearchParams<{ id: string }>();
  const { ieltsRecords } = useAppModel();
  const record = ieltsRecords.find((item) => item.id === id);
  if (!record) return <Redirect href={routes.learning.ielts.history} />;
  return <IeltsAssetReport record={record} onBack={() => router.back()} />;
}
