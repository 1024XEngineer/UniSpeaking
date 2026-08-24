import { act, renderHook } from '@testing-library/react-native';

import { AuthenticatedMediaClient } from '../AuthenticatedMediaClient';
import { useRecordingPlayback } from '../useRecordingPlayback';

jest.mock('../AuthenticatedMediaClient', () => ({
  AuthenticatedMediaClient: jest.fn(),
}));

const mockedMediaClient = jest.mocked(AuthenticatedMediaClient);

describe('useRecordingPlayback download failure', () => {
  beforeEach(() => {
    jest.useRealTimers();
  });

  it('returns to idle and exposes a rejected recording download', async () => {
    let rejectDownload!: (error: Error) => void;
    const download = jest.fn(() => new Promise<never>((_resolve, reject) => { rejectDownload = reject; }));
    mockedMediaClient.mockImplementation(() => ({ download }) as never);
    const { result } = await renderHook(() => useRecordingPlayback(['/missing.wav']));

    await act(async () => {
      result.current.toggle();
      await Promise.resolve();
    });
    expect(result.current.playing).toBe(true);

    await act(async () => {
      rejectDownload(new Error('录音不存在'));
      await Promise.resolve();
      await Promise.resolve();
    });

    expect(result.current.error).toBe('录音不存在');
    expect(result.current.playing).toBe(false);
  });
});
