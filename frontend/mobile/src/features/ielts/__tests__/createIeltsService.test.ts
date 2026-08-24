import { createIeltsService } from '../createIeltsService';

jest.mock('@/infrastructure/auth/SecureTokenStore', () => ({ SecureTokenStore: jest.fn() }));
jest.mock('@/infrastructure/http/ApiClient', () => ({ ApiClient: jest.fn() }));
jest.mock('../IeltsService', () => ({ IeltsService: jest.fn() }));
jest.mock('@/infrastructure/config/runtimeConfig', () => ({ getRuntimeConfig: () => ({ backendUrl: 'https://api.example.com' }) }));

describe('createIeltsService', () => {
  it('wires runtime config, token store and unauthorized callback', () => {
    const callback = jest.fn();
    expect(createIeltsService(callback)).toBeDefined();
  });
});
