import { render } from '@testing-library/react-native';
import { Text } from 'react-native';

jest.mock('@/infrastructure/telemetry/MobileTelemetry', () => ({ mobileTelemetry: { setUser: jest.fn() } }));
jest.mock('@/model/AppModel', () => ({ useAppModel: () => ({ userId: 'user-1' }) }));
jest.mock('expo-router', () => ({ usePathname: () => '/profile' }));

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
});
