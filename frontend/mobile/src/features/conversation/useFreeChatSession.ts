import { useCallback, useEffect, useRef, useState } from 'react';

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

export type FreeChatConfig = Pick<
  RealtimeSessionOptions,
  'voice' | 'model' | 'speechSpeed'
>;

export type FreeChatControllerPort = {
  getSnapshot(): RealtimeSessionSnapshot;
  subscribe(listener: (snapshot: RealtimeSessionSnapshot) => void): () => void;
  start(): Promise<unknown>;
  setMuted(muted: boolean): void;
  interrupt(): void;
  end(): Promise<unknown>;
};

export type FreeChatControllerFactory = (
  config: FreeChatConfig,
) => FreeChatControllerPort;

const statusLabels: Record<RealtimeState, string> = {
  idle: '正在准备',
  requesting_permission: '正在请求麦克风权限',
  creating_offer: '正在创建实时连接',
  exchanging_sdp: '正在连接服务',
  connecting: '正在连接 AI',
  ready: '可以开始说了',
  user_speaking: '正在聆听',
  assistant_speaking: 'AI 正在回答',
  paused: '会话已暂停',
  ending: '正在结束',
  ended: '会话已结束',
  error: '连接失败',
};

export function createFreeChatController(
  config: FreeChatConfig,
): FreeChatControllerPort {
  const tokenStore = new SecureTokenStore();
  const { backendUrl } = getRuntimeConfig();
  const apiClient = new ApiClient({ baseUrl: backendUrl, tokenStore });
  return new RealtimeSessionController(
    {
      transport: new ReactNativeWebRTCTransport(),
      sessionApi: new RealtimeSessionApi(apiClient),
      sessionSocket: new SessionMessageSocket({ baseUrl: backendUrl, tokenStore }),
    },
    {
      mode: 'free_chat',
      ...config,
    },
  );
}

const initialSnapshot: RealtimeSessionSnapshot = {
  state: 'idle',
  muted: false,
  sessionId: null,
  userTranscript: '',
  assistantTranscript: '',
  transcriptHistory: [],
  error: null,
};

export function useFreeChatSession(
  config: FreeChatConfig,
  createController: FreeChatControllerFactory = createFreeChatController,
) {
  const [controller] = useState(() => createController(config));
  const [snapshot, setSnapshot] = useState<RealtimeSessionSnapshot>(() =>
    controller.getSnapshot?.() ?? initialSnapshot,
  );
  const [startupError, setStartupError] = useState<string | null>(null);
  const [elapsed, setElapsed] = useState(0);
  const startPromise = useRef<Promise<unknown> | null>(null);
  const endPromise = useRef<Promise<unknown> | null>(null);
  const lifecycleVersion = useRef(0);

  const end = useCallback(() => {
    if (!endPromise.current) {
      endPromise.current = Promise.resolve(controller.end());
    }
    return endPromise.current;
  }, [controller]);

  useEffect(() => controller.subscribe(setSnapshot), [controller]);

  useEffect(() => {
    lifecycleVersion.current += 1;
    let active = true;
    if (!startPromise.current) {
      startPromise.current = Promise.resolve().then(() => controller.start());
    }
    void startPromise.current.catch((error: unknown) => {
      if (active) {
        setStartupError(
          error instanceof Error ? error.message : '实时对话启动失败',
        );
      }
    });
    return () => {
      active = false;
      lifecycleVersion.current += 1;
      const cleanupVersion = lifecycleVersion.current;
      queueMicrotask(() => {
        if (lifecycleVersion.current === cleanupVersion) {
          void end().catch(() => undefined);
        }
      });
    };
  }, [controller, end]);

  useEffect(() => {
    const active = !['idle', 'ending', 'ended', 'error'].includes(snapshot.state);
    if (!active) return;
    const timer = setInterval(() => setElapsed((current) => current + 1), 1_000);
    return () => clearInterval(timer);
  }, [snapshot.state]);

  const toggleMuted = useCallback(() => {
    controller.setMuted(!snapshot.muted);
  }, [controller, snapshot.muted]);

  return {
    ...snapshot,
    elapsed,
    statusLabel: statusLabels[snapshot.state],
    error: snapshot.error?.message ?? startupError,
    toggleMuted,
    interrupt: () => controller.interrupt(),
    end,
  };
}
