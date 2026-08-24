import { Alert } from 'react-native';
import { fireEvent, render } from '@testing-library/react-native';

import { CalendarCard, Membership, requestLogoutConfirmation } from '../ProfileScreen';

const calendarFor = (month: string) => ({
  month,
  checkedDates: [],
  checkedInToday: false,
});

describe('CalendarCard', () => {
  beforeEach(() => {
    jest.useFakeTimers();
    jest.setSystemTime(new Date('2026-08-18T00:00:00.000Z'));
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it('disables future days and keeps the selected day unchanged when pressed', async () => {
    const view = await render(<CalendarCard calendar={calendarFor('2026-08')} onMonthChange={jest.fn()} />);

    expect(view.getByLabelText('8月19日，未打卡')).toBeDisabled();
    expect(view.getByText('8 月 18 日')).toBeTruthy();

    fireEvent.press(view.getByLabelText('8月19日，未打卡'));

    expect(view.getByText('8 月 18 日')).toBeTruthy();
    expect(view.queryByText('8 月 19 日')).toBeNull();
  });

  it('keeps historical month days available for review', async () => {
    const view = await render(<CalendarCard calendar={calendarFor('2026-07')} onMonthChange={jest.fn()} />);

    expect(view.getByLabelText('7月31日，未打卡')).not.toBeDisabled();
    await fireEvent.press(view.getByLabelText('7月31日，未打卡'));

    expect(view.getByText(/7\s*月\s*31\s*日/)).toBeTruthy();
  });
});

describe('requestLogoutConfirmation', () => {
  it('only logs out after destructive confirmation', () => {
    const logout = jest.fn(async () => undefined);
    const alert = jest.spyOn(Alert, 'alert').mockImplementation(() => undefined);

    requestLogoutConfirmation(logout);

    expect(logout).not.toHaveBeenCalled();
    const actions = alert.mock.calls[0]?.[2];
    actions?.find((action) => action.text === '退出登录')?.onPress?.();
    expect(logout).toHaveBeenCalledTimes(1);
    alert.mockRestore();
  });

  it('keeps the session when the user cancels', () => {
    const logout = jest.fn(async () => undefined);
    const alert = jest.spyOn(Alert, 'alert').mockImplementation(() => undefined);

    requestLogoutConfirmation(logout);

    const actions = alert.mock.calls[0]?.[2];
    actions?.find((action) => action.text === '取消')?.onPress?.();
    expect(logout).not.toHaveBeenCalled();
    alert.mockRestore();
  });
});

describe('Membership', () => {
  it('shows the plans without exposing a fake purchase action', async () => {
    const onBack = jest.fn();
    const view = await render(<Membership onBack={onBack} />);
    expect(view.getByText('免费版')).toBeTruthy();
    expect(view.getByText('专业版')).toBeTruthy();
    expect(view.getByText('特训版')).toBeTruthy();
    expect(view.getAllByText('暂未开放')).toHaveLength(2);
    expect(view.getByRole('button', { name: '当前方案' })).toBeDisabled();
    await fireEvent.press(view.getByRole('button', { name: '返回' }));
    expect(onBack).toHaveBeenCalledTimes(1);
    view.unmount();
  });
});
