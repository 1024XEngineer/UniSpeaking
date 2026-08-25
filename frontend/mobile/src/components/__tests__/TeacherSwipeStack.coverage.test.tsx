import { fireEvent, render, waitFor } from '@testing-library/react-native';

const mockPlayTeacher = jest.fn();
const mockPanCallbacks: {
  begin?: () => void;
  update?: (event: { translationX: number }) => void;
  end?: (event: { velocityX: number }) => void;
} = {};

jest.mock('react-native-gesture-handler', () => {
  const { View } = require('react-native');
  const pan = {
    activeOffsetX: () => pan,
    onBegin: (callback: () => void) => { mockPanCallbacks.begin = callback; return pan; },
    onUpdate: (callback: (event: { translationX: number }) => void) => { mockPanCallbacks.update = callback; return pan; },
    onEnd: (callback: (event: { velocityX: number }) => void) => { mockPanCallbacks.end = callback; return pan; },
  };
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

  it('commits drag updates and bounded momentum in both directions', async () => {
    const onSelect = jest.fn();
    const view = await render(<TeacherSwipeStack selected={teachers[0]} onSelect={onSelect} />);
    mockPanCallbacks.begin?.();
    mockPanCallbacks.update?.({ translationX: 0 });
    expect(onSelect).not.toHaveBeenCalled();
    mockPanCallbacks.update?.({ translationX: -120 });
    await waitFor(() => expect(onSelect).toHaveBeenCalled());
    mockPanCallbacks.end?.({ velocityX: 2_000 });
    mockPanCallbacks.begin?.();
    mockPanCallbacks.update?.({ translationX: 120 });
    mockPanCallbacks.end?.({ velocityX: -2_000 });
    expect(onSelect.mock.calls.length).toBeGreaterThanOrEqual(3);
    view.unmount();
  });
});
