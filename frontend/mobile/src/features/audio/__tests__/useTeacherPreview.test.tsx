import { act, renderHook } from '@testing-library/react-native';

const mockPlay = jest.fn();
const mockStop = jest.fn();
const mockDispose = jest.fn();
jest.mock('../TeacherPreviewPlayer', () => ({ TeacherPreviewPlayer: jest.fn(() => ({ play: mockPlay, stop: mockStop, dispose: mockDispose })) }));
jest.mock('expo-audio', () => ({ createAudioPlayer: jest.fn(() => ({})), setAudioModeAsync: jest.fn(async () => undefined) }));

import { useTeacherPreview } from '../useTeacherPreview';

describe('useTeacherPreview', () => {
  it('lazily creates a player, delegates playback and cleans up', async () => {
    const { result, unmount } = await renderHook(() => useTeacherPreview());
    const teacher = { id: 'clara', name: 'Clara', voiceId: 'Mione', accent: '英式', image: 1 } as any;
    await act(async () => result.current.playTeacher(teacher));
    await act(async () => result.current.stop());
    expect(mockPlay).toHaveBeenCalledWith(teacher);
    expect(mockStop).toHaveBeenCalled();
    unmount();
  });
});
