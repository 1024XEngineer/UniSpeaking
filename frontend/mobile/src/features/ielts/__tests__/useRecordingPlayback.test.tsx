import { act, renderHook } from '@testing-library/react-native';

import { AuthenticatedMediaClient } from '../AuthenticatedMediaClient';
import { useRecordingPlayback } from '../useRecordingPlayback';

const mockCreateAudioPlayer = jest.fn();

jest.mock('expo-audio', () => ({
  createAudioPlayer: mockCreateAudioPlayer,
}));

jest.mock('../AuthenticatedMediaClient', () => ({
  AuthenticatedMediaClient: jest.fn(),
}));

const mockedMediaClient = jest.mocked(AuthenticatedMediaClient);

describe('useRecordingPlayback', () => {
  beforeEach(() => {
    jest.useFakeTimers();
    mockCreateAudioPlayer.mockReset();
    mockedMediaClient.mockImplementation(() => ({
      download: jest.fn(async (url: string) => ({ uri: `file://${url}`, remove: jest.fn() })),
    }) as never);
  });

  afterEach(() => {
    jest.useRealTimers();
    jest.clearAllMocks();
  });

  it('plays every downloaded recording and releases native and cached resources', async () => {
    const firstPlayer = { play: jest.fn(), pause: jest.fn(), remove: jest.fn() };
    const secondPlayer = { play: jest.fn(), pause: jest.fn(), remove: jest.fn() };
    mockCreateAudioPlayer.mockReturnValueOnce(firstPlayer).mockReturnValueOnce(secondPlayer);
    const download = jest.fn()
      .mockResolvedValueOnce({ uri: 'file://first', remove: jest.fn() })
      .mockResolvedValueOnce({ uri: 'file://second', remove: jest.fn() });
    mockedMediaClient.mockImplementation(() => ({ download }) as never);
    const { result } = await renderHook(() => useRecordingPlayback(['/one.wav', '/two.mp3']));

    await act(async () => {
      result.current.toggle();
      await jest.runAllTimersAsync();
    });

    expect(download).toHaveBeenNthCalledWith(1, '/one.wav');
    expect(download).toHaveBeenNthCalledWith(2, '/two.mp3');
    expect(firstPlayer.play).toHaveBeenCalledTimes(1);
    expect(secondPlayer.play).toHaveBeenCalledTimes(1);
    expect(secondPlayer.pause).toHaveBeenCalledTimes(1);
    expect(secondPlayer.remove).toHaveBeenCalledTimes(1);
    expect(result.current.playing).toBe(false);
  });

  it('stops an in-flight request without creating a native player', async () => {
    let resolveDownload!: (value: { uri: string; remove: jest.Mock }) => void;
    const download = jest.fn(() => new Promise((resolve) => { resolveDownload = resolve; }));
    mockedMediaClient.mockImplementation(() => ({ download }) as never);
    const { result } = await renderHook(() => useRecordingPlayback(['/one.wav']));

    await act(async () => {
      result.current.toggle();
      await Promise.resolve();
    });
    await act(async () => {
      result.current.stop();
    });
    expect(result.current.playing).toBe(false);
    await act(async () => {
      resolveDownload({ uri: 'file://first', remove: jest.fn() });
      await Promise.resolve();
    });

    expect(mockCreateAudioPlayer).not.toHaveBeenCalled();
  });

  it('reports whether playback is available without starting an empty queue', async () => {
    const empty = await renderHook(() => useRecordingPlayback([]));
    expect(empty.result.current.canPlay).toBe(false);
    act(() => empty.result.current.toggle());
    expect(empty.result.current.playing).toBe(false);
  });

});
