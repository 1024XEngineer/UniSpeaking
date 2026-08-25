import { render, waitFor } from '@testing-library/react-native';
import { Text } from 'react-native';

const mockAuthService = { refresh: jest.fn(async () => ({ accessToken: 'a', refreshToken: 'r' })) };
const mockController = {
  getSnapshot: jest.fn(() => ({ status: 'anonymous', user: null, preference: null, error: null })),
  subscribe: jest.fn(() => jest.fn()), bootstrap: jest.fn(async () => undefined),
  login: jest.fn(), issueEmailChallenge: jest.fn(), issuePasswordResetChallenge: jest.fn(),
  resetPassword: jest.fn(), register: jest.fn(), updatePreference: jest.fn(), logout: jest.fn(),
  unauthorized: jest.fn(async () => undefined),
};
let mockApiOptions: any;
let mockCoordinatorOptions: any;
const mockCoordinatorClear = jest.fn();

jest.mock('@/infrastructure/auth/SecureTokenStore', () => ({ SecureTokenStore: jest.fn() }));
jest.mock('@/infrastructure/http/ApiClient', () => ({ ApiClient: jest.fn((options) => { mockApiOptions = options; return {}; }) }));
jest.mock('@/features/auth/AuthService', () => ({ AuthService: jest.fn(() => mockAuthService) }));
jest.mock('@/features/auth/AuthSessionController', () => ({ AuthSessionController: jest.fn(() => mockController) }));
jest.mock('@/infrastructure/auth/AuthTokenCoordinator', () => ({
  authTokenCoordinator: {
    configure: (options: any) => { mockCoordinatorOptions = options; },
    clear: () => mockCoordinatorClear(),
  },
}));
jest.mock('@/infrastructure/config/runtimeConfig', () => ({ getRuntimeConfig: () => ({ backendUrl: 'https://api.example.test' }) }));

import { AppModelProvider, useAppModel } from '../AppModel';

function Probe() {
  const model = useAppModel();
  return <Text>{model.authStatus}</Text>;
}

describe('AppModel default authentication wiring', () => {
  it('constructs and wires the default authentication stack', async () => {
    const view = await render(<AppModelProvider><Probe /></AppModelProvider>);
    await waitFor(() => expect(view.getByText('anonymous')).toBeTruthy());
    expect(mockController.bootstrap).toHaveBeenCalled();
    await mockApiOptions.onUnauthorized();
    expect(mockController.unauthorized).toHaveBeenCalled();
    await expect(mockCoordinatorOptions.refresh('refresh-token')).resolves.toEqual({ accessToken: 'a', refreshToken: 'r' });
    expect(mockAuthService.refresh).toHaveBeenCalledWith('refresh-token');
    mockCoordinatorOptions.onRefreshFailure();
    expect(mockCoordinatorClear).toHaveBeenCalled();
    view.unmount();
  });

  it('rejects model access outside its provider', async () => {
    await expect(render(<Probe />)).rejects.toThrow('useAppModel must be used inside AppModelProvider');
  });
});
