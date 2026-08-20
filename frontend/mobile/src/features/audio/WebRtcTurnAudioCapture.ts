import { NativeModules, Platform } from 'react-native';

import type { TurnAudioCapturePort } from './TurnAudioCapture';

export type WebRtcPcmTapPort = {
  startSegment(): Promise<void>;
  stopSegment(): Promise<void>;
  takeSegment(): Promise<string | null>;
  releaseSegment(): Promise<void>;
};

export function createWebRtcTurnAudioCapture(
  nativeTap: WebRtcPcmTapPort,
): TurnAudioCapturePort {
  let active = false;
  let finalized = false;
  let startPromise: Promise<void> | null = null;
  let stopPromise: Promise<void> | null = null;

  return {
    async start() {
      if (active || finalized || startPromise) return;
      startPromise = Promise.resolve(nativeTap.startSegment());
      try {
        await startPromise;
        active = true;
      } finally {
        startPromise = null;
      }
    },
    stop() {
      if (!active) return false;
      active = false;
      finalized = true;
      stopPromise = Promise.resolve(nativeTap.stopSegment());
      return true;
    },
    async take() {
      if (startPromise) await startPromise;
      if (active) {
        active = false;
        finalized = true;
        stopPromise = Promise.resolve(nativeTap.stopSegment());
      }
      if (stopPromise) await stopPromise;
      const uri = finalized ? await nativeTap.takeSegment() : null;
      finalized = false;
      stopPromise = null;
      return uri;
    },
    async release() {
      if (startPromise) await startPromise.catch(() => undefined);
      await nativeTap.releaseSegment().catch(() => undefined);
      active = false;
      finalized = false;
      startPromise = null;
      stopPromise = null;
    },
  };
}

export function createNativeWebRtcTurnAudioCapture(): TurnAudioCapturePort | null {
  if (Platform.OS !== 'android') return null;
  const nativeTap = NativeModules.WebRtcPcmTap as WebRtcPcmTapPort | undefined;
  return nativeTap ? createWebRtcTurnAudioCapture(nativeTap) : null;
}
