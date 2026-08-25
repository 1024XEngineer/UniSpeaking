import { NativeModules, Platform } from 'react-native';
import { createNativeWebRtcTurnAudioCapture, createWebRtcTurnAudioCapture } from '../WebRtcTurnAudioCapture';

describe('createWebRtcTurnAudioCapture', () => {
  it('segments the microphone samples already owned by WebRTC', async () => {
    let finishStop!: () => void;
    const nativeTap = {
      startSegment: jest.fn(async () => undefined),
      stopSegment: jest.fn(() => new Promise<void>((resolve) => {
        finishStop = resolve;
      })),
      takeSegment: jest.fn(async () => 'file:///webrtc-turn.wav'),
      releaseSegment: jest.fn(async () => undefined),
    };
    const capture = createWebRtcTurnAudioCapture(nativeTap);

    await capture.start();
    expect(capture.stop()).toBe(true);
    const audio = capture.take();
    expect(nativeTap.takeSegment).not.toHaveBeenCalled();
    finishStop();

    await expect(audio).resolves.toBe('file:///webrtc-turn.wav');
    expect(nativeTap.startSegment).toHaveBeenCalledTimes(1);
    expect(nativeTap.stopSegment).toHaveBeenCalledTimes(1);
    expect(nativeTap.takeSegment).toHaveBeenCalledTimes(1);
  });

  it('releases an active segment without producing a WAV', async () => {
    const nativeTap = {
      startSegment: jest.fn(async () => undefined),
      stopSegment: jest.fn(async () => undefined),
      takeSegment: jest.fn(async () => 'file:///unused.wav'),
      releaseSegment: jest.fn(async () => undefined),
    };
    const capture = createWebRtcTurnAudioCapture(nativeTap);

    await capture.start();
    await capture.release();

    expect(nativeTap.releaseSegment).toHaveBeenCalledTimes(1);
    expect(nativeTap.takeSegment).not.toHaveBeenCalled();
    expect(capture.stop()).toBe(false);
  });

  it('deduplicates an in-flight start and can take directly from an active segment', async () => {
    let finishStart!: () => void;
    const nativeTap = {
      startSegment: jest.fn(() => new Promise<void>((resolve) => { finishStart = resolve; })),
      stopSegment: jest.fn(async () => undefined),
      takeSegment: jest.fn(async () => 'file:///active.wav'),
      releaseSegment: jest.fn(async () => undefined),
    };
    const capture = createWebRtcTurnAudioCapture(nativeTap);
    const first = capture.start();
    const second = capture.start();
    finishStart();
    await Promise.all([first, second]);
    await capture.start();
    await expect(capture.take()).resolves.toBe('file:///active.wav');
    expect(nativeTap.startSegment).toHaveBeenCalledTimes(1);
    expect(nativeTap.stopSegment).toHaveBeenCalledTimes(1);
  });

  it('returns null before a segment and resets after each take', async () => {
    const nativeTap = {
      startSegment: jest.fn(async () => undefined),
      stopSegment: jest.fn(async () => undefined),
      takeSegment: jest.fn(async () => 'file:///once.wav'),
      releaseSegment: jest.fn(async () => undefined),
    };
    const capture = createWebRtcTurnAudioCapture(nativeTap);
    await expect(capture.take()).resolves.toBeNull();
    await capture.start();
    expect(capture.stop()).toBe(true);
    expect(capture.stop()).toBe(false);
    await expect(capture.take()).resolves.toBe('file:///once.wav');
    await expect(capture.take()).resolves.toBeNull();
  });

  it('waits through failed starts and suppresses native release errors', async () => {
    let rejectStart!: (error: Error) => void;
    const nativeTap = {
      startSegment: jest.fn(() => new Promise<void>((_, reject) => { rejectStart = reject; })),
      stopSegment: jest.fn(async () => undefined),
      takeSegment: jest.fn(async () => null),
      releaseSegment: jest.fn(async () => { throw new Error('release failed'); }),
    };
    const capture = createWebRtcTurnAudioCapture(nativeTap);
    const starting = capture.start();
    const releasing = capture.release();
    rejectStart(new Error('start failed'));
    await expect(starting).rejects.toThrow('start failed');
    await expect(releasing).resolves.toBeUndefined();
  });

  it('creates the native adapter only on Android when the module exists', () => {
    const originalOs = Platform.OS;
    const originalTap = NativeModules.WebRtcPcmTap;
    Object.defineProperty(Platform, 'OS', { configurable: true, value: 'ios' });
    expect(createNativeWebRtcTurnAudioCapture()).toBeNull();
    Object.defineProperty(Platform, 'OS', { configurable: true, value: 'android' });
    delete NativeModules.WebRtcPcmTap;
    expect(createNativeWebRtcTurnAudioCapture()).toBeNull();
    NativeModules.WebRtcPcmTap = {
      startSegment: jest.fn(), stopSegment: jest.fn(), takeSegment: jest.fn(), releaseSegment: jest.fn(),
    };
    expect(createNativeWebRtcTurnAudioCapture()).not.toBeNull();
    Object.defineProperty(Platform, 'OS', { configurable: true, value: originalOs });
    NativeModules.WebRtcPcmTap = originalTap;
  });
});
