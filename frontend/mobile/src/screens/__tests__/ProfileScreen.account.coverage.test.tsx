import { fireEvent, render, waitFor } from '@testing-library/react-native';

const mockApi = { updateNickname: jest.fn(), changePassword: jest.fn() };
const mockAppModel = {
  nickname: 'Ada', email: 'ada@example.com', setNickname: jest.fn(),
};

jest.mock('@/features/profile/ProfileApi', () => ({ ProfileApi: jest.fn(() => mockApi) }));
jest.mock('@/model/AppModel', () => ({ useAppModel: () => mockAppModel }));

import { AccountSettings } from '../ProfileScreen';

it('validates, closes, and retries nickname updates', async () => {
  const view = await render(<AccountSettings onBack={jest.fn()} />);
  await fireEvent.press(view.getByText('展示用户名'));
  await fireEvent.changeText(view.getByDisplayValue('Ada'), '  ');
  await fireEvent.press(view.getByText('保存用户名'));
  expect(view.getByText('用户名需为 1 到 32 个字符')).toBeTruthy();
  await fireEvent.changeText(view.getByDisplayValue('  '), 'x'.repeat(33));
  await fireEvent.press(view.getByText('保存用户名'));
  expect(view.getByText('用户名需为 1 到 32 个字符')).toBeTruthy();
  await fireEvent.changeText(view.getByDisplayValue('x'.repeat(33)), 'Grace');
  mockApi.updateNickname.mockRejectedValueOnce('offline');
  await fireEvent.press(view.getByText('保存用户名'));
  await waitFor(() => expect(view.getByText('用户名保存失败')).toBeTruthy());
  await fireEvent.press(view.getByText('取消'));
  expect(view.queryByDisplayValue('Grace')).toBeNull();
});
