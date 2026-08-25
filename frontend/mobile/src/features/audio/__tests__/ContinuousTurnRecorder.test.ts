import { ContinuousTurnRecorder, interviewPcmContract } from '../ContinuousTurnRecorder';

function wavChunk(pcm: number[]) {
  const bytes = new Uint8Array(44 + pcm.length);
  const view = new DataView(bytes.buffer);
  for (const [offset, value] of [[0, 'RIFF'], [8, 'WAVE'], [12, 'fmt '], [36, 'data']] as const) {
    [...value].forEach((character, index) => { bytes[offset + index] = character.charCodeAt(0); });
  }
  view.setUint32(16, 16, true);
  view.setUint16(20, 1, true);
  view.setUint16(22, 1, true);
  view.setUint32(24, 16_000, true);
  view.setUint32(28, 32_000, true);
  view.setUint16(32, 2, true);
  view.setUint16(34, 16, true);
  view.setUint32(40, pcm.length, true);
  bytes.set(pcm, 44);
  return bytes;
}

function base64(bytes: Uint8Array) {
  return Buffer.from(bytes).toString('base64');
}

function fixture() {
  let onAudioStream: ((event: { data: string; eventDataSize: number }) => Promise<void>) | undefined;
  const startResults: any[] = [];
  const recorder = {
    requestPermissionsAsync: jest.fn(async () => ({ granted: true })),
    startRecording: jest.fn(async (config): Promise<any> => {
      onAudioStream = config.onAudioStream;
      return startResults.shift();
    }),
    stopRecording: jest.fn(async () => null),
  };
  const written = new Map<string, Uint8Array>();
  const deleted: string[] = [];
  const storage = {
    prepare: jest.fn(),
    createFile: jest.fn((name: string) => ({
      uri: `file:///cache/${name}`,
      size: 0,
      create: jest.fn(),
      write(bytes: Uint8Array) {
        written.set(name, bytes);
        this.size = bytes.length;
      },
      delete: jest.fn(),
    })),
    remove: jest.fn((uri: string) => deleted.push(uri)),
    cleanup: jest.fn(),
  };
  return { recorder, storage, written, deleted, startResults, emit: async (bytes: Uint8Array) => onAudioStream?.({ data: base64(bytes), eventDataSize: bytes.length }) };
}

describe('ContinuousTurnRecorder', () => {
  afterEach(() => {
    jest.useRealTimers();
  });

  it('keeps one continuous native recording and writes a PCM16 WAV per turn', async () => {
    const test = fixture();
    const recorder = new ContinuousTurnRecorder(test.recorder, test.storage);
    await recorder.start();
    recorder.setInputEnabled(true);
    await test.emit(wavChunk([1, 0, 2, 0]));
    recorder.speechStarted();
    await test.emit(new Uint8Array([3, 0, 4, 0]));
    recorder.speechStopped();
    await test.emit(new Uint8Array(16_000));

    const turn = await recorder.takeTurn(1);
    await recorder.close();

    expect(test.recorder.startRecording).toHaveBeenCalledTimes(1);
    expect(test.recorder.stopRecording).toHaveBeenCalledTimes(1);
    expect(turn).toEqual(expect.objectContaining({ name: 'interview-turn-1.wav' }));
    const wav = test.written.get('interview-turn-1.wav')!;
    expect(Buffer.from(wav.subarray(0, 4)).toString()).toBe('RIFF');
    expect(new DataView(wav.buffer).getUint32(24, true)).toBe(16_000);
    expect(new DataView(wav.buffer).getUint16(22, true)).toBe(1);
    expect(new DataView(wav.buffer).getUint16(34, true)).toBe(16);
  });

  it('is idempotent on start and close and removes submitted temporary files', async () => {
    const test = fixture();
    const recorder = new ContinuousTurnRecorder(test.recorder, test.storage);
    await Promise.all([recorder.start(), recorder.start()]);
    recorder.setInputEnabled(true);
    recorder.speechStarted();
    await test.emit(wavChunk([1, 0]));
    recorder.speechStopped();
    const turnPromise = recorder.takeTurn(2);
    await test.emit(new Uint8Array(16_000));
    const turn = await turnPromise;
    await Promise.all([recorder.close(), recorder.close()]);
    recorder.discard(turn);

    expect(test.recorder.startRecording).toHaveBeenCalledTimes(1);
    expect(test.recorder.stopRecording).toHaveBeenCalledTimes(1);
    expect(test.deleted).toEqual(['file:///cache/interview-turn-2.wav']);
    expect(test.storage.cleanup).toHaveBeenCalledTimes(1);
  });

  it('can start again after close with a fresh native recording and header state', async () => {
    const test = fixture();
    test.startResults.push(
      { fileUri: 'file:///cache/native-1.wav', mimeType: 'audio/wav' },
      { fileUri: 'file:///cache/native-2.wav', mimeType: 'audio/wav' },
    );
    const recorder = new ContinuousTurnRecorder(test.recorder, test.storage);

    await recorder.start();
    await recorder.close();
    await recorder.start();
    recorder.setInputEnabled(true);
    await test.emit(wavChunk([1, 0]));
    recorder.speechStarted();
    await test.emit(new Uint8Array([2, 0]));
    recorder.speechStopped();
    const turnPromise = recorder.takeTurn(1);
    await test.emit(new Uint8Array(16_000));
    const turn = await turnPromise;
    await recorder.close();

    expect(test.recorder.startRecording).toHaveBeenCalledTimes(2);
    expect(test.recorder.stopRecording).toHaveBeenCalledTimes(2);
    expect(test.storage.prepare).toHaveBeenCalledTimes(2);
    expect(test.storage.cleanup).toHaveBeenCalledTimes(2);
    expect(test.deleted).toEqual(expect.arrayContaining([
      'file:///cache/native-1.wav',
      'file:///cache/native-2.wav',
    ]));
    expect(turn).not.toBeNull();
  });

  it('waits for an in-flight native start before closing it', async () => {
    const test = fixture();
    let resolveStart!: (value: any) => void;
    test.recorder.startRecording.mockImplementationOnce(async () => {
      await new Promise<void>((resolve) => { resolveStart = resolve; });
      return { fileUri: 'file:///cache/native-delayed.wav', mimeType: 'audio/wav' };
    });
    const recorder = new ContinuousTurnRecorder(test.recorder, test.storage);

    const starting = recorder.start();
    while (!resolveStart) await Promise.resolve();
    const closing = recorder.close();
    expect(test.recorder.stopRecording).not.toHaveBeenCalled();
    resolveStart(undefined);
    await starting;
    await closing;

    expect(test.recorder.stopRecording).toHaveBeenCalledTimes(1);
    expect(test.deleted).toContain('file:///cache/native-delayed.wav');
    expect(test.storage.cleanup).toHaveBeenCalledTimes(1);
  });

  it('rejects microphone denial before opening native capture', async () => {
    const test = fixture();
    test.recorder.requestPermissionsAsync.mockResolvedValue({ granted: false });
    const recorder = new ContinuousTurnRecorder(test.recorder, test.storage);
    await expect(recorder.start()).rejects.toThrow('请允许麦克风权限');
    expect(test.recorder.startRecording).not.toHaveBeenCalled();
  });

  it('publishes the backend-compatible PCM contract', () => {
    expect(interviewPcmContract).toEqual({ sampleRate: 16_000, channels: 1, bitsPerSample: 16 });
  });

  it('ignores speech markers until capture is ready and returns null without a turn', async () => {
    const test = fixture();
    const recorder = new ContinuousTurnRecorder(test.recorder, test.storage);
    recorder.speechStarted();
    recorder.speechStopped();
    recorder.discard(null);
    await expect(recorder.takeTurn(1)).resolves.toBeNull();

    await recorder.start();
    recorder.speechStarted();
    await expect(recorder.takeTurn(2)).resolves.toBeNull();
    expect(test.storage.remove).not.toHaveBeenCalled();
    await recorder.close();
  });

  it('validates native PCM chunks and drops empty or odd trailing bytes', async () => {
    const test = fixture();
    const recorder = new ContinuousTurnRecorder(test.recorder, test.storage);
    await recorder.start();
    recorder.setInputEnabled(true);

    await expect(
      (test.emit as any)(new Uint8Array([1, 2])),
    ).resolves.toBeUndefined();
    const config = test.recorder.startRecording.mock.calls[0][0];
    await expect(config.onAudioStream({ data: new Uint8Array([1]), eventDataSize: 1 })).rejects.toThrow(
      '必须返回原始 PCM 数据',
    );
    await expect(config.onAudioStream({ data: base64(new Uint8Array([1, 2])), eventDataSize: 3 })).rejects.toThrow(
      '数据块长度不一致',
    );
    await config.onAudioStream({ data: base64(new Uint8Array([1])), eventDataSize: 1 });

    recorder.setInputEnabled(false);
    recorder.speechStarted();
    await expect(recorder.takeTurn(1)).resolves.toBeNull();
    await recorder.close();
  });

  it('finalizes an empty active turn after the bounded post-roll timeout', async () => {
    jest.useFakeTimers();
    const test = fixture();
    const recorder = new ContinuousTurnRecorder(test.recorder, test.storage);
    await recorder.start();
    recorder.setInputEnabled(true);
    recorder.speechStarted();

    const turn = recorder.takeTurn(3);
    jest.advanceTimersByTime(1_200);
    await expect(turn).resolves.toBeNull();
    await recorder.close();
  });

  it('rejects a partial file write', async () => {
    const test = fixture();
    test.storage.createFile.mockImplementationOnce((name: string) => ({
      uri: `file:///cache/${name}`,
      size: 1,
      create: jest.fn(),
      write: jest.fn(),
      delete: jest.fn(),
    }));
    const recorder = new ContinuousTurnRecorder(test.recorder, test.storage);
    await recorder.start();
    recorder.setInputEnabled(true);
    recorder.speechStarted();
    await test.emit(new Uint8Array([1, 0]));
    recorder.speechStopped();
    const pending = recorder.takeTurn(4);
    await test.emit(new Uint8Array(16_000));

    await expect(pending).rejects.toThrow('面试录音文件写入不完整');
    await recorder.close();
  });

  it('resamples assistant PCM and waits for both minimum and capped drain windows', async () => {
    jest.useFakeTimers();
    const test = fixture();
    const recorder = new ContinuousTurnRecorder(test.recorder, test.storage);
    recorder.appendAssistantAudio(base64(new Uint8Array([1])));
    recorder.finishAssistantAudio();
    recorder.appendAssistantAudio(base64(new Uint8Array([1, 0, 2, 0, 3, 0])));
    recorder.finishAssistantAudio();
    recorder.finishAssistantAudio();

    const minimumDrain = recorder.waitForAssistantAudioDrain();
    jest.advanceTimersByTime(1_500);
    await minimumDrain;

    recorder.appendAssistantAudio(base64(new Uint8Array(600_000)));
    recorder.finishAssistantAudio();
    const cappedDrain = recorder.waitForAssistantAudioDrain();
    jest.advanceTimersByTime(4_000);
    await cappedDrain;
  });

  it('cleans storage when native start or stop fails', async () => {
    const failedStart = fixture();
    failedStart.recorder.startRecording.mockRejectedValueOnce(new Error('start failed'));
    const first = new ContinuousTurnRecorder(failedStart.recorder, failedStart.storage);
    const starting = first.start();
    const closing = first.close();
    await expect(starting).rejects.toThrow('start failed');
    await closing;
    expect(failedStart.storage.cleanup).toHaveBeenCalled();

    const failedStop = fixture();
    failedStop.recorder.stopRecording.mockRejectedValueOnce(new Error('stop failed'));
    const second = new ContinuousTurnRecorder(failedStop.recorder, failedStop.storage);
    await second.start();
    await expect(second.close()).rejects.toThrow('stop failed');
    expect(failedStop.storage.cleanup).toHaveBeenCalled();
  });
});
