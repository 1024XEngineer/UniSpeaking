import { useEffect, useMemo, useState } from 'react';

import { ContinuousTurnRecorder } from '@/features/audio/ContinuousTurnRecorder';
import { ReactNativeWebRTCTransport } from '@/features/realtime/ReactNativeWebRTCTransport';
import { SessionMessageSocket } from '@/features/realtime/SessionMessageSocket';
import { SecureTokenStore } from '@/infrastructure/auth/SecureTokenStore';
import { getRuntimeConfig } from '@/infrastructure/config/runtimeConfig';
import { ApiClient } from '@/infrastructure/http/ApiClient';

import {
  InterviewSessionController,
  type InterviewSessionSnapshot,
} from './InterviewSessionController';
import { InterviewSessionApi } from './InterviewSessionApi';

const initialSnapshot: InterviewSessionSnapshot = {
  state: 'idle',
  status: 'idle',
  sessionId: null,
  transcripts: [],
  muted: true,
  turnNo: 0,
  interviewState: null,
  reportStatus: null,
  error: null,
};

export function createInterviewApi(sceneId: string) {
  const client = new ApiClient({
    baseUrl: getRuntimeConfig().backendUrl,
    tokenStore: new SecureTokenStore(),
  });
  return new InterviewSessionApi(client, sceneId);
}

export function useInterviewSession({
  sceneId,
  voice,
  model = 'qwen3.5-omni-flash-realtime',
}: {
  sceneId: string;
  voice: string;
  model?: string;
}) {
  // Keep native Audio Studio loading out of Jest's module graph while Metro
  // still includes the production hook in Android development builds.
  // eslint-disable-next-line @typescript-eslint/no-require-imports
  const { AudioStudioModule, useAudioRecorder } = require('@siteed/audio-studio') as typeof import('@siteed/audio-studio');
  const nativeRecorder = useAudioRecorder();
  const controller = useMemo(() => {
    const tokenStore = new SecureTokenStore();
    const { backendUrl } = getRuntimeConfig();
    const client = new ApiClient({ baseUrl: backendUrl, tokenStore });
    return new InterviewSessionController(
      {
        recorder: new ContinuousTurnRecorder({
          requestPermissionsAsync: () => AudioStudioModule.requestPermissionsAsync(),
          startRecording: nativeRecorder.startRecording,
          stopRecording: nativeRecorder.stopRecording,
        }),
        transport: new ReactNativeWebRTCTransport(),
        sessionApi: new InterviewSessionApi(client, sceneId),
        sessionSocket: new SessionMessageSocket({ baseUrl: backendUrl, tokenStore }),
      },
      { sceneId, voice, model },
    );
  }, [AudioStudioModule, model, nativeRecorder, sceneId, voice]);
  const [snapshot, setSnapshot] = useState<InterviewSessionSnapshot>(initialSnapshot);
  const [elapsed, setElapsed] = useState(0);

  useEffect(() => {
    const unsubscribe = controller.subscribe(setSnapshot);
    void controller.start().catch(() => undefined);
    return () => {
      unsubscribe();
      void controller.end().catch(() => undefined);
    };
  }, [controller]);

  useEffect(() => {
    if (snapshot.state !== 'active') return;
    const timer = setInterval(() => setElapsed((current) => current + 1), 1_000);
    return () => clearInterval(timer);
  }, [snapshot.state]);

  return {
    ...snapshot,
    elapsed,
    setMuted: (muted: boolean) => controller.setMuted(muted),
    end: () => controller.end(),
  };
}
