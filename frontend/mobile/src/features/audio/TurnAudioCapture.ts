import type { WavRecorder } from './WavRecorder';

export type TurnAudioCapturePort = {
  start(): Promise<void>;
  stop(): boolean;
  take(): Promise<string | null>;
};

export function createTurnAudioCapture(
  recorder: Pick<WavRecorder, 'start' | 'stop' | 'cancel'>,
): TurnAudioCapturePort {
  let active = false;
  let finalized = false;
  let audioPromise: Promise<string | null> = Promise.resolve(null);

  const stop = () => {
    if (!active) return false;
    audioPromise = recorder.stop().catch(() => null);
    active = false;
    finalized = true;
    return true;
  };

  return {
    async start() {
      if (active || finalized) return;
      await recorder.start();
      active = true;
    },
    stop,
    async take() {
      if (active) stop();
      const audio = await audioPromise;
      audioPromise = Promise.resolve(null);
      finalized = false;
      return audio;
    },
  };
}
