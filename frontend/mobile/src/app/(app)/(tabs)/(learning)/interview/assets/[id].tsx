import { Redirect, useLocalSearchParams, useRouter } from 'expo-router';

import { routes } from '@/navigation/routes';
import { InterviewAssetRemoteReport } from '@/screens/SpecialtyAssetsScreen';
import { InterviewAssetService, type InterviewAssetRecord } from '@/features/interview/InterviewAssetService';
import { SecureTokenStore } from '@/infrastructure/auth/SecureTokenStore';
import { getRuntimeConfig } from '@/infrastructure/config/runtimeConfig';
import { ApiClient } from '@/infrastructure/http/ApiClient';
import { useEffect, useState } from 'react';
import { Text } from 'react-native';
import { AppScreen, Card } from '@/components/ui';

export default function InterviewAssetReportRoute() {
  const router = useRouter();
  const { id } = useLocalSearchParams<{ id: string }>();
  const [record, setRecord] = useState<InterviewAssetRecord | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  useEffect(() => {
    let active = true;
    void new InterviewAssetService(new ApiClient({ baseUrl: getRuntimeConfig().backendUrl, tokenStore: new SecureTokenStore() })).listAssets().then((items) => {
      if (active) setRecord(items.find((item) => item.sceneId === id) ?? null);
    }).catch((cause) => {
      if (active) setError(cause instanceof Error ? cause.message : '面试资产加载失败');
    }).finally(() => {
      if (active) setLoading(false);
    });
    return () => { active = false; };
  }, [id]);
  if (loading) return <AppScreen><Text>正在读取面试报告…</Text></AppScreen>;
  if (error) return <AppScreen><Card><Text>{error}</Text></Card></AppScreen>;
  if (!record) return <Redirect href={routes.learning.interview.history} />;
  return <InterviewAssetRemoteReport asset={record} onBack={() => router.replace(routes.learning.interview.history)} onPractice={() => router.replace(routes.specialty.interviewPractice(record.sceneId, record.jobTitle))} />;
}
