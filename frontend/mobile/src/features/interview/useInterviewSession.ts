import { useCallback, useEffect, useRef, useState } from 'react';

import { ContinuousTurnRecorder } from '@/features/audio/ContinuousTurnRecorder';
import { ReactNativeWebRTCTransport } from '@/features/realtime/ReactNativeWebRTCTransport';
import { SessionMessageSocket } from '@/features/realtime/SessionMessageSocket';
import { SecureTokenStore } from '@/infrastructure/auth/SecureTokenStore';
import { getRuntimeConfig } from '@/infrastructure/config/runtimeConfig';
import { ApiClient } from '@/infrastructure/http/ApiClient';
import type { TrainingTracker } from '@/infrastructure/analytics/AnalyticsClient';

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
  userMuted: false,
  turnNo: 0,
  interviewState: null,
  reportStatus: null,
  currentQuestion: '',
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
}, analytics?: TrainingTracker) {
  // Keep native Audio Studio loading out of Jest's module graph while Metro
  // still includes the production hook in Android development builds.
  // eslint-disable-next-line @typescript-eslint/no-require-imports
  const { AudioStudioModule, useAudioRecorder } = require('@siteed/audio-studio') as typeof import('@siteed/audio-studio');
  const nativeRecorder = useAudioRecorder();
  const [controller] = useState(() => {
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
  });
  const [snapshot, setSnapshot] = useState<InterviewSessionSnapshot>(initialSnapshot);
  const [elapsed, setElapsed] = useState(0);
  const endPromise = useRef<Promise<unknown> | null>(null);
  const lifecycleVersion = useRef(0);
  const end = useCallback(() => {
    if (!endPromise.current) {
      endPromise.current = Promise.resolve(controller.end()).then((result) => {
        analytics?.complete();
        return result;
      }).catch((error: unknown) => {
        if (analytics?.isStarted()) analytics.abandon('REALTIME_ERROR');
        else analytics?.fail('REALTIME_ERROR');
        throw error;
      });
    }
    return endPromise.current;
  }, [analytics, controller]);

  useEffect(() => {
    lifecycleVersion.current += 1;
    const unsubscribe = controller.subscribe(setSnapshot);
    analytics?.attempt();
    void controller.start().catch(() => analytics?.fail('REALTIME_ERROR'));
    return () => {
      unsubscribe();
      lifecycleVersion.current += 1;
      const cleanupVersion = lifecycleVersion.current;
      queueMicrotask(() => {
        if (lifecycleVersion.current === cleanupVersion) {
          analytics?.abandon('USER_EXIT');
          void end().catch(() => undefined);
        }
      });
    };
  }, [analytics, controller, end]);

  useEffect(() => {
    if (snapshot.state === 'active') {
      analytics?.started();
      analytics?.resume();
    } else if (snapshot.state === 'error') {
      if (analytics?.isStarted()) analytics.abandon('REALTIME_ERROR');
      else analytics?.fail('REALTIME_ERROR');
    }
  }, [analytics, snapshot.state]);

  useEffect(() => {
    if (snapshot.state !== 'active') return;
    const timer = setInterval(() => setElapsed((current) => current + 1), 1_000);
    return () => clearInterval(timer);
  }, [snapshot.state]);

  return {
    ...snapshot,
    elapsed,
    setMuted: (muted: boolean) => controller.setMuted(muted),
    end,
  };
}
