import { SecureTokenStore } from '@/infrastructure/auth/SecureTokenStore';
import { ApiClient } from '@/infrastructure/http/ApiClient';
import { getRuntimeConfig } from '@/infrastructure/config/runtimeConfig';

import { IeltsService } from './IeltsService';

export function createIeltsService(onUnauthorized?: () => void | Promise<void>) {
  const tokenStore = new SecureTokenStore();
  const { backendUrl } = getRuntimeConfig();
  const client = new ApiClient({ baseUrl: backendUrl, tokenStore, onUnauthorized });
  return new IeltsService(client);
}
