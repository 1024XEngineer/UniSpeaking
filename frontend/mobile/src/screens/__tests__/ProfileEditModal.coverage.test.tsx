import { cleanup, fireEvent, render, waitFor } from '@testing-library/react-native';

import { ProfileEditModal } from '../ProfileScreen';

afterEach(cleanup);

function picker(permission: boolean, result: unknown) {
  return {
    requestMediaLibraryPermissionsAsync: jest.fn(async () => ({ granted: permission })),
    launchImageLibraryAsync: jest.fn(async () => result),
  } as any;
}

function modal(loadImagePicker: () => Promise<any>, onSave = jest.fn(async () => undefined)) {
  return render(
    <ProfileEditModal
      avatarUrl={null}
      fallbackAvatar={1}
      nickname="Ada"
      onClose={jest.fn()}
      onSave={onSave}
      loadImagePicker={loadImagePicker}
    />,
  );
}

it('reports denied photo permission', async () => {
  const view = await modal(async () => picker(false, { canceled: true }));
  await fireEvent.press(view.getByText('选择新头像'));
  await waitFor(() => expect(view.getByText('需要允许访问照片后才能选择头像')).toBeTruthy());
});

it('keeps the existing avatar when selection is cancelled', async () => {
  const api = picker(true, { canceled: true });
  const onSave = jest.fn(async () => undefined);
  const view = await modal(async () => api, onSave);
  await fireEvent.press(view.getByText('选择新头像'));
  await fireEvent.press(view.getByText('保存修改'));
  await waitFor(() => expect(onSave).toHaveBeenCalledWith('Ada', null));
});

it.each([
  [{ uri: 'file:///avatar.gif', mimeType: 'image/gif', fileSize: 10 }, '请选择 JPEG 或 PNG 图片'],
  [{ uri: 'file:///avatar.png', mimeType: 'image/png', fileSize: 3 * 1024 * 1024 }, '图片不能超过 2 MiB'],
])('validates selected avatar %p', async (asset, message) => {
  const view = await modal(async () => picker(true, { canceled: false, assets: [asset] }));
  await fireEvent.press(view.getByText('选择新头像'));
  await waitFor(() => expect(view.getByText(message)).toBeTruthy());
});

it('passes a normalized successful avatar to profile persistence', async () => {
  const onSave = jest.fn(async () => undefined);
  const view = await modal(async () => picker(true, {
    canceled: false,
    assets: [{ uri: 'file:///avatar.png', mimeType: 'image/png', fileSize: 512, fileName: null }],
  }), onSave);
  await fireEvent.press(view.getByText('选择新头像'));
  await fireEvent.changeText(view.getByDisplayValue('Ada'), ' Grace ');
  await fireEvent.press(view.getByText('保存修改'));
  await waitFor(() => expect(onSave).toHaveBeenCalledWith('Grace', expect.objectContaining({
    uri: 'file:///avatar.png', mimeType: 'image/png', fileName: 'avatar.png', fileSize: 512,
  })));
});

it('shows profile persistence failures and remains open', async () => {
  const view = await modal(async () => picker(true, { canceled: true }), jest.fn(async () => {
    throw 'offline';
  }));
  await fireEvent.press(view.getByText('保存修改'));
  await waitFor(() => expect(view.getByText('个人资料保存失败')).toBeTruthy());
  expect(view.getByText('编辑个人资料')).toBeTruthy();
});
