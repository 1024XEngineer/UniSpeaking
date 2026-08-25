import { render } from '@testing-library/react-native';
import { AppState, Text } from 'react-native';

let mockAnalyticsDependencies: any;
const mockDefaultAnalytics = {
  setDistinctId: jest.fn(), setAppVisible: jest.fn(), trackPageView: jest.fn(), trackModeSelection: jest.fn(),
  trackLearningAsset: jest.fn(), training: jest.fn(),
};

jest.mock('@/infrastructure/telemetry/MobileTelemetry', () => ({ mobileTelemetry: { setUser: jest.fn() } }));
jest.mock('@/model/AppModel', () => ({ useAppModel: () => ({ userId: 'user-1' }) }));
jest.mock('expo-router', () => ({ usePathname: () => '/profile' }));
jest.mock('@/infrastructure/config/runtimeConfig', () => ({ getRuntimeConfig: () => ({ umami: { websiteId: 'site' } }) }));
jest.mock('@/infrastructure/analytics/AnalyticsClient', () => ({
  AnalyticsClient: jest.fn((_config, dependencies) => { mockAnalyticsDependencies = dependencies; return mockDefaultAnalytics; }),
}));

import { AnalyticsProvider, useAnalytics } from '../AnalyticsProvider';
import { TelemetryProvider } from '../TelemetryProvider';
import { mobileTelemetry } from '@/infrastructure/telemetry/MobileTelemetry';

const analytics = {
  setDistinctId: jest.fn(), setAppVisible: jest.fn(), trackPageView: jest.fn(), trackModeSelection: jest.fn(),
  trackLearningAsset: jest.fn(), training: jest.fn(),
};

function AnalyticsChild() {
  const client = useAnalytics();
  return <Text>{client === analytics ? 'ready' : 'missing'}</Text>;
}

describe('mobile providers', () => {
  it('connects analytics context and tracks identity/page visibility', async () => {
    const view = await render(<AnalyticsProvider analyticsClient={analytics}><AnalyticsChild /></AnalyticsProvider>);
    expect(view.getByText('ready')).toBeTruthy();
    expect(analytics.setDistinctId).toHaveBeenCalledWith('user-1');
    expect(analytics.trackPageView).toHaveBeenCalledWith('/profile');
    expect(analytics.setAppVisible).toHaveBeenCalled();
    view.unmount();
  });

  it('sets the telemetry user from the app model', async () => {
    const view = await render(<TelemetryProvider><Text>content</Text></TelemetryProvider>);
    expect(view.getByText('content')).toBeTruthy();
    expect(mobileTelemetry.setUser).toHaveBeenCalledWith('user-1');
    view.unmount();
  });

  it('constructs the default analytics client and handles every app visibility state', async () => {
    let listener!: (state: string) => void;
    const remove = jest.fn();
    const appState = jest.spyOn(AppState, 'addEventListener').mockImplementation((_event, callback) => {
      listener = callback as (state: string) => void;
      return { remove } as any;
    });
    const view = await render(<AnalyticsProvider><Text>default analytics</Text></AnalyticsProvider>);
    expect(view.getByText('default analytics')).toBeTruthy();
    listener('background');
    listener('active');
    expect(mockDefaultAnalytics.setAppVisible).toHaveBeenCalledWith(false);
    expect(mockDefaultAnalytics.setAppVisible).toHaveBeenCalledWith(true);
    expect(mockAnalyticsDependencies.language()).toEqual(expect.any(String));
    expect(mockAnalyticsDependencies.screen()).toMatch(/^\d+x\d+$/);
    expect(mockAnalyticsDependencies.userAgent()).toContain('UniSpeaking-Mobile/1.0');
    view.unmount();
    appState.mockRestore();
  });

  it('rejects analytics access outside its provider', async () => {
    await expect(render(<AnalyticsChild />)).rejects.toThrow('useAnalytics must be used inside AnalyticsProvider');
  });
});
