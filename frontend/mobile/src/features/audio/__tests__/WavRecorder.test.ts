import {
  WavRecorder,
  type PcmRecorderPort,
} from '../WavRecorder';

function createNativeRecorder(): PcmRecorderPort & {
  requestPermissionsAsync: jest.Mock;
  startRecording: jest.Mock;
  stopRecording: jest.Mock;
} {
  return {
    requestPermissionsAsync: jest.fn(async () => ({ granted: true })),
    startRecording: jest.fn(async () => undefined),
    stopRecording: jest.fn(async () => ({ fileUri: 'file:///take.wav' })),
  };
}

describe('WavRecorder', () => {
  it('requests permission and records 16 kHz mono PCM16 WAV', async () => {
    const nativeRecorder = createNativeRecorder();
    const recorder = new WavRecorder(nativeRecorder);

    await recorder.start();
    await expect(recorder.stop()).resolves.toBe('file:///take.wav');

    expect(nativeRecorder.requestPermissionsAsync).toHaveBeenCalledTimes(1);
    expect(nativeRecorder.startRecording).toHaveBeenCalledWith({
      sampleRate: 16_000,
      channels: 1,
      encoding: 'pcm_16bit',
      output: {
        primary: {
          enabled: true,
          format: 'wav',
        },
      },
    });
    expect(nativeRecorder.stopRecording).toHaveBeenCalledTimes(1);
  });

  it('maps a denied microphone permission to a user-facing error', async () => {
    const nativeRecorder = createNativeRecorder();
    nativeRecorder.requestPermissionsAsync.mockResolvedValue({ granted: false });
    const recorder = new WavRecorder(nativeRecorder);

    await expect(recorder.start()).rejects.toThrow('请允许麦克风权限后再朗读');
    expect(nativeRecorder.startRecording).not.toHaveBeenCalled();
  });

  it('cancels idempotently and refuses to stop when no recording is active', async () => {
    const nativeRecorder = createNativeRecorder();
    const recorder = new WavRecorder(nativeRecorder);

    await expect(recorder.stop()).rejects.toThrow('当前没有正在进行的录音');
    await recorder.start();
    await recorder.cancel();
    await recorder.cancel();

    expect(nativeRecorder.stopRecording).toHaveBeenCalledTimes(1);
  });

  it('clears an orphaned native recording and retries once', async () => {
    const nativeRecorder = createNativeRecorder();
    nativeRecorder.startRecording
      .mockRejectedValueOnce(Object.assign(new Error('Recording is already in progress'), {
        code: 'ALREADY_RECORDING',
      }))
      .mockResolvedValueOnce(undefined);
    const recorder = new WavRecorder(nativeRecorder);

    await recorder.start();
    await expect(recorder.stop()).resolves.toBe('file:///take.wav');

    expect(nativeRecorder.startRecording).toHaveBeenCalledTimes(2);
    expect(nativeRecorder.stopRecording).toHaveBeenCalledTimes(2);
  });
});
