import { getRuntimeConfig } from '../runtimeConfig';

describe('getRuntimeConfig', () => {
  it('removes trailing slashes from the configured backend URL', () => {
    expect(
      getRuntimeConfig({
        EXPO_PUBLIC_BACKEND_URL: 'http://127.0.0.1:8080///',
      }),
    ).toEqual({
      backendUrl: 'http://127.0.0.1:8080',
      umami: {
        enabled: false,
        endpoint: 'https://cloud.umami.is/api/send',
        websiteId: '',
        hostname: 'unispeaking.qnsdk.com',
      },
    });
  });

  it('uses the USB-forwarded Android development URL when no URL is configured', () => {
    expect(getRuntimeConfig({})).toEqual({
      backendUrl: 'http://127.0.0.1:8080',
      umami: {
        enabled: false,
        endpoint: 'https://cloud.umami.is/api/send',
        websiteId: '',
        hostname: 'unispeaking.qnsdk.com',
      },
    });
  });

  it('rejects a relative backend URL', () => {
    expect(() =>
      getRuntimeConfig({
        EXPO_PUBLIC_BACKEND_URL: '/backend',
      }),
    ).toThrow('EXPO_PUBLIC_BACKEND_URL must be an absolute HTTP(S) URL');
  });

  it('enables the shared Umami website only with an explicit valid configuration', () => {
    expect(getRuntimeConfig({
      EXPO_PUBLIC_UMAMI_ENABLED: 'true',
      EXPO_PUBLIC_UMAMI_ENDPOINT: 'https://cloud.umami.is/api/send/',
      EXPO_PUBLIC_UMAMI_WEBSITE_ID: '3ae2dee9-d585-43a9-93f3-fcafcd14b258',
    }).umami).toEqual({
      enabled: true,
      endpoint: 'https://cloud.umami.is/api/send',
      websiteId: '3ae2dee9-d585-43a9-93f3-fcafcd14b258',
      hostname: 'unispeaking.qnsdk.com',
    });
  });

  it('rejects an enabled Umami configuration without a valid Website ID', () => {
    expect(() => getRuntimeConfig({ EXPO_PUBLIC_UMAMI_ENABLED: 'true' }))
      .toThrow('EXPO_PUBLIC_UMAMI_WEBSITE_ID must be a valid Umami Website ID');
  });
});
