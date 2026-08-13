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
  let stopRequested = false;
  let startPromise: Promise<void> | null = null;
  let audioPromise: Promise<string | null> = Promise.resolve(null);

  const stop = () => {
    if (startPromise && !active) {
      stopRequested = true;
      return true;
    }
    if (!active) return false;
    audioPromise = recorder.stop().catch(() => null);
    active = false;
    finalized = true;
    return true;
  };

  return {
    async start() {
      if (active || finalized || startPromise) return;
      stopRequested = false;
      startPromise = (async () => {
        await recorder.start();
        active = true;
        if (stopRequested) stop();
      })();
      try {
        await startPromise;
      } finally {
        startPromise = null;
      }
    },
    stop,
    async take() {
      if (startPromise) await startPromise;
      if (active) stop();
      const audio = await audioPromise;
      audioPromise = Promise.resolve(null);
      finalized = false;
      return audio;
    },
  };
}
