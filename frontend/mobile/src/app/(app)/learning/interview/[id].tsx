import { Redirect, useLocalSearchParams, useRouter } from 'expo-router';

import { useAppModel } from '@/model/AppModel';
import { routes } from '@/navigation/routes';
import { InterviewAssetReport } from '@/screens/SpecialtyAssetsScreen';

export default function InterviewAssetReportRoute() {
  const router = useRouter();
  const { id } = useLocalSearchParams<{ id: string }>();
  const { interviewRecords } = useAppModel();
  const record = interviewRecords.find((item) => item.id === id);
  if (!record) return <Redirect href={routes.learning.interview.history} />;
  return <InterviewAssetReport record={record} onBack={() => router.back()} />;
}
