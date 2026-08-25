import {
  WavRecorder,
  type PcmRecorderPort,
} from '../WavRecorder';

jest.mock('@siteed/audio-studio', () => ({
  AudioStudioModule: {
    requestPermissionsAsync: jest.fn(async () => ({ granted: true })),
    startRecording: jest.fn(async () => undefined),
    stopRecording: jest.fn(async () => ({ fileUri: 'file:///native.wav' })),
  },
}));

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

  it('uses the default native adapter and ignores duplicate starts', async () => {
    const { AudioStudioModule } = jest.requireMock('@siteed/audio-studio');
    const recorder = new WavRecorder();
    await recorder.start();
    await recorder.start();
    await expect(recorder.stop()).resolves.toBe('file:///native.wav');
    expect(AudioStudioModule.requestPermissionsAsync).toHaveBeenCalledTimes(1);
    expect(AudioStudioModule.startRecording).toHaveBeenCalledTimes(1);
  });

  it('rethrows unrelated native start failures', async () => {
    for (const failure of [new Error('microphone busy'), null, 'busy']) {
      const nativeRecorder = createNativeRecorder();
      nativeRecorder.startRecording.mockRejectedValueOnce(failure);
      const recorder = new WavRecorder(nativeRecorder);
      await expect(recorder.start()).rejects.toBe(failure);
    }
  });

  it('recognizes already-recording errors by message and tolerates orphan cleanup failure', async () => {
    const nativeRecorder = createNativeRecorder();
    nativeRecorder.startRecording
      .mockRejectedValueOnce({ message: 'Recording is already in progress' })
      .mockResolvedValueOnce(undefined);
    nativeRecorder.stopRecording
      .mockRejectedValueOnce(new Error('orphan cleanup failed'))
      .mockResolvedValueOnce({ fileUri: 'file:///retry.wav' });
    const recorder = new WavRecorder(nativeRecorder);
    await recorder.start();
    await expect(recorder.stop()).resolves.toBe('file:///retry.wav');
  });

  it('resets active state when stop/cancel fail and rejects missing output files', async () => {
    const failedStop = createNativeRecorder();
    failedStop.stopRecording.mockRejectedValueOnce(new Error('stop failed'));
    const first = new WavRecorder(failedStop);
    await first.start();
    await expect(first.stop()).rejects.toThrow('stop failed');
    await first.start();

    const missing = createNativeRecorder();
    missing.stopRecording.mockResolvedValueOnce(null);
    const second = new WavRecorder(missing);
    await second.start();
    await expect(second.stop()).rejects.toThrow('录音文件生成失败');

    const failedCancel = createNativeRecorder();
    failedCancel.stopRecording.mockRejectedValueOnce(new Error('cancel failed'));
    const third = new WavRecorder(failedCancel);
    await third.start();
    await expect(third.cancel()).rejects.toThrow('cancel failed');
    await third.start();
  });
});
