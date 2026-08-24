import { fireEvent, render, waitFor } from '@testing-library/react-native';

const mockPlayTeacher = jest.fn();

jest.mock('react-native-gesture-handler', () => {
  const { View } = require('react-native');
  const pan = { activeOffsetX: () => pan, onBegin: () => pan, onUpdate: () => pan, onEnd: () => pan };
  return { Gesture: { Pan: () => pan }, GestureDetector: ({ children }: { children: React.ReactNode }) => <View>{children}</View> };
});
jest.mock('react-native-reanimated', () => {
  const { View } = require('react-native');
  const animated = Object.assign(View, { View });
  return {
    __esModule: true, default: animated, Easing: { out: (value: unknown) => value, inOut: (value: unknown) => value, cubic: 'cubic' },
    FadeIn: { duration: () => ({ easing: () => ({}) }) }, FadeOut: { duration: () => ({}) },
    interpolate: (value: number) => value, runOnJS: (fn: (...args: any[]) => unknown) => fn,
    useAnimatedStyle: (factory: () => unknown) => factory(), useSharedValue: (value: number) => ({ value }), withTiming: (value: number) => value,
  };
});
jest.mock('@/features/audio/useTeacherPreview', () => ({ useTeacherPreview: () => ({ playTeacher: mockPlayTeacher }) }));

import { TeacherSwipeStack } from '../TeacherSwipeStack';
import { teachers } from '@/theme/tokens';

describe('TeacherSwipeStack', () => {
  beforeEach(() => jest.clearAllMocks());

  it('renders the selected teacher, previews it, and reports a dial selection', async () => {
    const onSelect = jest.fn();
    const view = await render(<TeacherSwipeStack selected={teachers[0]} onSelect={onSelect} />);
    expect(view.getByText('Clara')).toBeTruthy();
    await fireEvent.press(view.getByLabelText('试听 Clara'));
    expect(mockPlayTeacher).toHaveBeenCalledWith(teachers[0]);
    await fireEvent.press(view.getByLabelText('选择 James'));
    await waitFor(() => expect(onSelect).toHaveBeenCalledWith(expect.objectContaining({ id: 'james' })));
    expect(view.getByText('James')).toBeTruthy();
    view.unmount();
  });

  it('falls back to the first teacher when a stale selected value is supplied', async () => {
    const view = await render(<TeacherSwipeStack selected={{ ...teachers[0], id: 'removed-teacher' }} onSelect={jest.fn()} />);
    expect(view.getByText('Clara')).toBeTruthy();
    expect(view.getByLabelText('选择 Clara').props.accessibilityState).toEqual({ selected: true });
    view.unmount();
  });
});
