export type RuntimeEnvironment = Readonly<{
  EXPO_PUBLIC_BACKEND_URL?: string;
  EXPO_PUBLIC_UMAMI_ENABLED?: string;
  EXPO_PUBLIC_UMAMI_ENDPOINT?: string;
  EXPO_PUBLIC_UMAMI_WEBSITE_ID?: string;
}>;

export type RuntimeConfig = Readonly<{
  backendUrl: string;
  umami: Readonly<{
    enabled: boolean;
    endpoint: string;
    websiteId: string;
    hostname: string;
  }>;
}>;

const androidUsbDevelopmentUrl = 'http://127.0.0.1:8080';
const umamiCloudEndpoint = 'https://cloud.umami.is/api/send';
const productionHostname = 'unispeaking.qnsdk.com';

function normalizeHttpUrl(value: string, variableName: string) {
  let parsedUrl: URL;
  try {
    parsedUrl = new URL(value);
  } catch {
    throw new Error(`${variableName} must be an absolute HTTP(S) URL`);
  }

  if (parsedUrl.protocol !== 'http:' && parsedUrl.protocol !== 'https:') {
    throw new Error(`${variableName} must be an absolute HTTP(S) URL`);
  }

  return value.replace(/\/+$/, '');
}

function normalizeHttpsUrl(value: string, variableName: string) {
  const normalized = normalizeHttpUrl(value, variableName);
  if (!normalized.toLowerCase().startsWith('https://')) {
    throw new Error(`${variableName} must be an HTTPS URL`);
  }
  return normalized;
}

export function getRuntimeConfig(
  environment: RuntimeEnvironment = {
    EXPO_PUBLIC_BACKEND_URL: process.env.EXPO_PUBLIC_BACKEND_URL,
    EXPO_PUBLIC_UMAMI_ENABLED: process.env.EXPO_PUBLIC_UMAMI_ENABLED,
    EXPO_PUBLIC_UMAMI_ENDPOINT: process.env.EXPO_PUBLIC_UMAMI_ENDPOINT,
    EXPO_PUBLIC_UMAMI_WEBSITE_ID: process.env.EXPO_PUBLIC_UMAMI_WEBSITE_ID,
  },
): RuntimeConfig {
  const configuredUrl = environment.EXPO_PUBLIC_BACKEND_URL?.trim();
  const backendUrl = configuredUrl || androidUsbDevelopmentUrl;
  const umamiEnabled = environment.EXPO_PUBLIC_UMAMI_ENABLED === 'true';
  const umamiEndpoint = environment.EXPO_PUBLIC_UMAMI_ENDPOINT?.trim() || umamiCloudEndpoint;
  const umamiWebsiteId = environment.EXPO_PUBLIC_UMAMI_WEBSITE_ID?.trim() || '';

  if (umamiEnabled && !/^[a-zA-Z0-9-]{8,80}$/.test(umamiWebsiteId)) {
    throw new Error('EXPO_PUBLIC_UMAMI_WEBSITE_ID must be a valid Umami Website ID');
  }

  return {
    backendUrl: normalizeHttpUrl(backendUrl, 'EXPO_PUBLIC_BACKEND_URL'),
    umami: {
      enabled: umamiEnabled,
      endpoint: normalizeHttpsUrl(umamiEndpoint, 'EXPO_PUBLIC_UMAMI_ENDPOINT'),
      websiteId: umamiEnabled ? umamiWebsiteId : '',
      hostname: productionHostname,
    },
  };
}
