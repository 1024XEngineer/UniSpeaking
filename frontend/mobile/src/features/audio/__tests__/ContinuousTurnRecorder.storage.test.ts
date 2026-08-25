const mockFiles: any[] = [];
const mockDirectories: any[] = [];

jest.mock('expo-file-system', () => {
  class Directory {
    exists = true;
    uri: string;
    create = jest.fn();
    delete = jest.fn();
    constructor(...parts: any[]) {
      this.uri = parts.map((part) => typeof part === 'string' ? part : part?.uri).join('/');
      mockDirectories.push(this);
    }
  }
  class File {
    exists = true;
    uri: string;
    size = 0;
    create = jest.fn();
    delete = jest.fn();
    constructor(...parts: any[]) {
      this.uri = parts.map((part) => typeof part === 'string' ? part : part?.uri).join('/');
      mockFiles.push(this);
    }
    write(bytes: Uint8Array) { this.size = bytes.length; }
  }
  return { Directory, File, Paths: { cache: { uri: 'file:///cache' }, document: { uri: 'file:///document' } } };
});

import { ContinuousTurnRecorder } from '../ContinuousTurnRecorder';

it('uses default Expo storage for turns, full sessions, discard, and cleanup', async () => {
  let stream: ((event: any) => Promise<void>) | undefined;
  const native = {
    requestPermissionsAsync: jest.fn(async () => ({ granted: true })),
    startRecording: jest.fn(async (config: any) => { stream = config.onAudioStream; return { fileUri: 'file:///native.wav' }; }),
    stopRecording: jest.fn(async () => null),
  };
  const recorder = new ContinuousTurnRecorder(native as any);
  await recorder.start();
  recorder.setInputEnabled(true);
  recorder.speechStarted();
  const chunk = new Uint8Array([1, 0, 2, 0]);
  await stream?.({ data: Buffer.from(chunk).toString('base64'), eventDataSize: chunk.length });
  recorder.speechStopped();
  const pending = recorder.takeTurn(1);
  const post = new Uint8Array(16_000);
  await stream?.({ data: Buffer.from(post).toString('base64'), eventDataSize: post.length });
  const turn = await pending;
  expect(turn?.uri).toContain('interview-turn-1.wav');
  expect(recorder.saveSessionRecording('session-1')).toContain('interview-full-session-1.wav');
  recorder.discard(turn);
  await recorder.close();
  expect(mockFiles.some((file) => file.delete.mock.calls.length > 0)).toBe(true);
  expect(mockDirectories.some((directory) => directory.delete.mock.calls.length > 0)).toBe(true);
});

it('rejects default file creation before storage preparation', () => {
  const native = { requestPermissionsAsync: jest.fn(), startRecording: jest.fn(), stopRecording: jest.fn() };
  const recorder = new ContinuousTurnRecorder(native as any);
  expect(recorder.saveSessionRecording('empty')).toBeNull();
});
