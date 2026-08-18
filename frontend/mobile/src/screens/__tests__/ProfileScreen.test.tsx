import { fireEvent, render } from '@testing-library/react-native';

import { CalendarCard } from '../ProfileScreen';

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
