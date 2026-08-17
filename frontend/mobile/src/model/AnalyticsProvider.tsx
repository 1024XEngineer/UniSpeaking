import { usePathname } from 'expo-router';
import { createContext, type PropsWithChildren, useContext, useEffect, useMemo } from 'react';
import { AppState, Dimensions, Platform } from 'react-native';

import { AnalyticsClient } from '@/infrastructure/analytics/AnalyticsClient';
import { getRuntimeConfig } from '@/infrastructure/config/runtimeConfig';

import { useAppModel } from './AppModel';

export type AnalyticsPort = Pick<
  AnalyticsClient,
  'setDistinctId' | 'setAppVisible' | 'trackPageView' | 'trackModeSelection' | 'trackLearningAsset' | 'training'
>;

const AnalyticsContext = createContext<AnalyticsPort | null>(null);

function createDefaultAnalyticsClient() {
  const config = getRuntimeConfig().umami;
  return new AnalyticsClient(config, {
    fetch: globalThis.fetch.bind(globalThis),
    language: () => Intl.DateTimeFormat().resolvedOptions().locale || 'zh-CN',
    screen: () => {
      const { width, height } = Dimensions.get('screen');
      return `${Math.round(width)}x${Math.round(height)}`;
    },
    userAgent: () => `UniSpeaking-Mobile/1.0 (${Platform.OS})`,
  });
}

export function AnalyticsProvider({
  children,
  analyticsClient,
}: PropsWithChildren<{ analyticsClient?: AnalyticsPort }>) {
  const client = useMemo(() => analyticsClient ?? createDefaultAnalyticsClient(), [analyticsClient]);
  const pathname = usePathname();
  const { userId } = useAppModel();

  useEffect(() => {
    client.setDistinctId(userId);
  }, [client, userId]);

  useEffect(() => {
    client.trackPageView(pathname);
  }, [client, pathname]);

  useEffect(() => {
    client.setAppVisible(AppState.currentState === 'active');
    const subscription = AppState.addEventListener('change', (nextState) => {
      client.setAppVisible(nextState === 'active');
    });
    return () => subscription.remove();
  }, [client]);

  return <AnalyticsContext.Provider value={client}>{children}</AnalyticsContext.Provider>;
}

export function useAnalytics() {
  const context = useContext(AnalyticsContext);
  if (!context) throw new Error('useAnalytics must be used inside AnalyticsProvider');
  return context;
}
