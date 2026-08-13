import { useCallback, useEffect, useRef, useState } from 'react';

import { speedCodeForLabel } from '@/features/auth/preferenceMappings';
import { WavRecorder } from '@/features/audio/WavRecorder';
import { createTurnAudioCapture } from '@/features/audio/TurnAudioCapture';
import { IeltsDialogueApi } from '@/features/ielts/IeltsDialogueApi';
import type { IeltsPart, IeltsPart2Event } from '@/features/ielts/types';
import { ReactNativeWebRTCTransport } from '@/features/realtime/ReactNativeWebRTCTransport';
import {
  RealtimeSessionController,
  type RealtimeSessionOptions,
  type RealtimeSessionSnapshot,
} from '@/features/realtime/RealtimeSessionController';
import { RealtimeSessionApi } from '@/features/realtime/RealtimeSessionApi';
import { SessionMessageSocket } from '@/features/realtime/SessionMessageSocket';
import type { RealtimeState } from '@/features/realtime/types';
import { SecureTokenStore } from '@/infrastructure/auth/SecureTokenStore';
import { getRuntimeConfig } from '@/infrastructure/config/runtimeConfig';
import { ApiClient } from '@/infrastructure/http/ApiClient';
import { useAppModel } from '@/model/AppModel';

import { IELTS_REALTIME_MODEL } from './ieltsMappings';

export type IeltsSessionConfig = {
  ieltsId: string;
  voiceId: string;
  part: IeltsPart;
};

export type IeltsSessionControllerPort = {
  getSnapshot(): RealtimeSessionSnapshot;
  subscribe(listener: (snapshot: RealtimeSessionSnapshot) => void): () => void;
  start(): Promise<unknown>;
  setMuted(muted: boolean): void;
  end(): Promise<unknown>;
  waitForTurnEvaluations(): Promise<void>;
  transitionPart2(event: IeltsPart2Event): Promise<unknown>;
  forcePart3Timeout(): Promise<unknown>;
  restoreIeltsState(): Promise<unknown>;
};

const statusLabels: Record<RealtimeState, string> = {
  idle: '正在准备',
  requesting_permission: '正在请求麦克风权限',
  creating_offer: '正在创建实时连接',
  exchanging_sdp: '正在连接服务',
  connecting: '正在连接考官',
  ready: '可以开始说了',
  user_speaking: '正在聆听',
  assistant_speaking: '考官正在提问',
  paused: '会话已暂停',
  ending: '正在结束',
  ended: '会话已结束',
  error: '连接失败',
};

const initialSnapshot: RealtimeSessionSnapshot = {
  state: 'idle',
  muted: false,
  sessionId: null,
  userTranscript: '',
  assistantTranscript: '',
  transcriptHistory: [],
  error: null,
};

export function createIeltsSessionController(
  config: IeltsSessionConfig,
  speechSpeedLabel: string,
): IeltsSessionControllerPort {
  const tokenStore = new SecureTokenStore();
  const { backendUrl } = getRuntimeConfig();
  const apiClient = new ApiClient({ baseUrl: backendUrl, tokenStore });
  const options: RealtimeSessionOptions = {
    mode: 'ielts',
    ieltsId: config.ieltsId,
    ieltsPart: config.part,
    voice: config.voiceId,
    model: IELTS_REALTIME_MODEL,
    speechSpeed: speedCodeForLabel(speechSpeedLabel),
  };
  const controller = new RealtimeSessionController(
    {
      transport: new ReactNativeWebRTCTransport(),
      sessionApi: new RealtimeSessionApi(apiClient),
      sessionSocket: new SessionMessageSocket({ baseUrl: backendUrl, tokenStore }),
      ieltsDialogue: new IeltsDialogueApi(apiClient, config.ieltsId),
      turnAudioCapture: createTurnAudioCapture(new WavRecorder()),
    },
    options,
  );
  return controller;
}

export function useIeltsSession(config: IeltsSessionConfig | null) {
  const { speed } = useAppModel();
  const [controller] = useState<IeltsSessionControllerPort | null>(() =>
    config ? createIeltsSessionController(config, speed) : null,
  );
  const [snapshot, setSnapshot] = useState<RealtimeSessionSnapshot>(initialSnapshot);
  const [startupError, setStartupError] = useState<string | null>(null);
  const endPromise = useRef<Promise<unknown> | null>(null);

  const end = useCallback(() => {
    if (!controller) return Promise.resolve(null);
    if (!endPromise.current) {
      endPromise.current = Promise.resolve(controller.end());
    }
    return endPromise.current;
  }, [controller]);

  useEffect(() => {
    if (!controller) return;
    return controller.subscribe(setSnapshot);
  }, [controller]);

  useEffect(() => {
    if (!controller) return;
    let active = true;
    if (config?.part === 'PART_2') {
      controller.setMuted(true);
    }
    void controller.start().catch((error: unknown) => {
      if (active) {
        setStartupError(error instanceof Error ? error.message : 'IELTS 会话启动失败');
      }
    });
    return () => {
      active = false;
      void end().catch(() => undefined);
    };
  }, [controller, config?.part, end]);

  const toggleMuted = useCallback(
    (muted: boolean) => {
      controller?.setMuted(muted);
    },
    [controller],
  );

  const transitionPart2 = useCallback(
    (event: IeltsPart2Event) => {
      if (!controller) return Promise.resolve(null);
      return controller.transitionPart2(event);
    },
    [controller],
  );

  const forcePart3Timeout = useCallback(() => {
    if (!controller) return Promise.resolve(null);
    return controller.forcePart3Timeout();
  }, [controller]);

  const restoreIeltsState = useCallback(() => {
    if (!controller) return Promise.resolve(null);
    return controller.restoreIeltsState();
  }, [controller]);

  const waitForTurnEvaluations = useCallback(() => {
    if (!controller) return Promise.resolve();
    return controller.waitForTurnEvaluations();
  }, [controller]);

  return {
    snapshot,
    startupError,
    statusLabel: statusLabels[snapshot.state],
    sessionId: snapshot.sessionId,
    end,
    waitForTurnEvaluations,
    toggleMuted,
    transitionPart2,
    forcePart3Timeout,
    restoreIeltsState,
  };
}
