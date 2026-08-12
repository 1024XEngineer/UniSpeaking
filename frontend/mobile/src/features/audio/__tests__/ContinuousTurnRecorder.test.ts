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
});
