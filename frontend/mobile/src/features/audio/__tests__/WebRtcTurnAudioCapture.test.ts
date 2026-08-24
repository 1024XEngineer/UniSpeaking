import { createWebRtcTurnAudioCapture } from '../WebRtcTurnAudioCapture';

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
});
