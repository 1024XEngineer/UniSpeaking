import { Redirect, useLocalSearchParams, useRouter } from 'expo-router';
import { useEffect, useState } from 'react';

import { useAppModel } from '@/model/AppModel';
import { routes } from '@/navigation/routes';
import { IeltsAssetReport } from '@/screens/SpecialtyAssetsScreen';
import { useIeltsFlowController } from '@/features/ielts/useIeltsFlowController';
import { AppScreen, PageHeader } from '@/components/ui';
import { Text } from 'react-native';

export default function IeltsAssetReportRoute() {
  const router = useRouter();
  const { id } = useLocalSearchParams<{ id: string }>();
  const { ieltsRecords } = useAppModel();
  const ielts = useIeltsFlowController();
  const [historyLoaded, setHistoryLoaded] = useState(false);
  useEffect(() => {
    let active = true;
    void ielts.refreshHistory().finally(() => {
      if (active) setHistoryLoaded(true);
    });
    return () => { active = false; };
  }, [ielts.refreshHistory]);
  const record = ielts.historyRecords.find((item) => item.id === id)
    ?? ieltsRecords.find((item) => item.id === id);
  if (!record && !historyLoaded) {
    return <AppScreen fixedHeader={<PageHeader fixed onBack={() => router.replace(routes.learning.ielts.history)} title="雅思报告" />}><Text>正在加载报告…</Text></AppScreen>;
  }
  if (!record) return <Redirect href={routes.learning.ielts.history} />;
  return <IeltsAssetReport record={record} onBack={() => router.replace(routes.learning.ielts.history)} />;
}
