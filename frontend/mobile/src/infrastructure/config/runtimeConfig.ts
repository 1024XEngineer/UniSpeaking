export type RuntimeEnvironment = Readonly<{
  EXPO_PUBLIC_BACKEND_URL?: string;
}>;

export type RuntimeConfig = Readonly<{
  backendUrl: string;
}>;

const androidUsbDevelopmentUrl = 'http://127.0.0.1:8080';

export function getRuntimeConfig(
  environment: RuntimeEnvironment = {
    EXPO_PUBLIC_BACKEND_URL: process.env.EXPO_PUBLIC_BACKEND_URL,
  },
): RuntimeConfig {
  const configuredUrl = environment.EXPO_PUBLIC_BACKEND_URL?.trim();
  const backendUrl = configuredUrl || androidUsbDevelopmentUrl;

  let parsedUrl: URL;
  try {
    parsedUrl = new URL(backendUrl);
  } catch {
    throw new Error('EXPO_PUBLIC_BACKEND_URL must be an absolute HTTP(S) URL');
  }

  if (parsedUrl.protocol !== 'http:' && parsedUrl.protocol !== 'https:') {
    throw new Error('EXPO_PUBLIC_BACKEND_URL must be an absolute HTTP(S) URL');
  }

  return {
    backendUrl: backendUrl.replace(/\/+$/, ''),
  };
}
