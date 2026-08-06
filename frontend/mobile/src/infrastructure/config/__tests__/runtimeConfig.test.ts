import { getRuntimeConfig } from '../runtimeConfig';

describe('getRuntimeConfig', () => {
  it('removes trailing slashes from the configured backend URL', () => {
    expect(
      getRuntimeConfig({
        EXPO_PUBLIC_BACKEND_URL: 'http://127.0.0.1:8080///',
      }),
    ).toEqual({
      backendUrl: 'http://127.0.0.1:8080',
    });
  });

  it('uses the USB-forwarded Android development URL when no URL is configured', () => {
    expect(getRuntimeConfig({})).toEqual({
      backendUrl: 'http://127.0.0.1:8080',
    });
  });

  it('rejects a relative backend URL', () => {
    expect(() =>
      getRuntimeConfig({
        EXPO_PUBLIC_BACKEND_URL: '/backend',
      }),
    ).toThrow('EXPO_PUBLIC_BACKEND_URL must be an absolute HTTP(S) URL');
  });
});
