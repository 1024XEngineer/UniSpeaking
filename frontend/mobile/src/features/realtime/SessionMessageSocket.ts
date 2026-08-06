import type { TokenStore } from '@/infrastructure/auth/SecureTokenStore';

import type { SessionMessage } from './RealtimeSessionController';

export type SessionWebSocketLike = {
  readyState: number;
  onopen: (() => void) | null;
  onmessage: ((event: { data: string }) => void) | null;
  onerror: (() => void) | null;
  onclose: (() => void) | null;
  send(data: string): void;
  close(): void;
};

type SessionSocketAck = {
  type?: string;
  success?: boolean;
  code?: string;
  message?: string;
  sessionId?: string;
};

type PendingAck = {
  operation: 'message' | 'end';
  resolve: (ack: SessionSocketAck) => void;
  reject: (error: Error) => void;
  timer: ReturnType<typeof setTimeout>;
};

export type SessionMessageSocketOptions = {
  baseUrl: string;
  tokenStore: Pick<TokenStore, 'get'>;
  webSocketFactory?: (url: string) => SessionWebSocketLike;
  connectTimeoutMs?: number;
  ackTimeoutMs?: number;
};

export function sessionWebSocketUrl(baseUrl: string, accessToken: string) {
  const normalizedBase = String(baseUrl).trim();
  const url = new URL(normalizedBase.endsWith('/') ? normalizedBase : `${normalizedBase}/`);
  if (url.protocol !== 'http:' && url.protocol !== 'https:') {
    throw new Error('后端地址必须使用 HTTP 或 HTTPS');
  }
  const basePath = url.pathname.replace(/\/$/, '');
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:';
  url.pathname = `${basePath}/ws/session-messages`.replace(/\/{2,}/g, '/');
  url.search = '';
  url.searchParams.set('access_token', accessToken);
  url.hash = '';
  return url.toString();
}

export class SessionMessageSocket {
  private readonly webSocketFactory: (url: string) => SessionWebSocketLike;
  private readonly connectTimeoutMs: number;
  private readonly ackTimeoutMs: number;
  private socket: SessionWebSocketLike | null = null;
  private sessionId: string | null = null;
  private pendingAcks: PendingAck[] = [];
  private connectPromise: Promise<void> | null = null;

  constructor(private readonly options: SessionMessageSocketOptions) {
    this.webSocketFactory =
      options.webSocketFactory ??
      ((url) => new WebSocket(url) as unknown as SessionWebSocketLike);
    this.connectTimeoutMs = options.connectTimeoutMs ?? 5_000;
    this.ackTimeoutMs = options.ackTimeoutMs ?? 5_000;
  }

  async connect(sessionId: string) {
    const normalizedSessionId = sessionId.trim();
    if (!normalizedSessionId) throw new Error('会话 ID 尚未建立');
    if (this.socket?.readyState === 1 && this.sessionId === normalizedSessionId) {
      return;
    }
    if (this.connectPromise && this.sessionId === normalizedSessionId) {
      return this.connectPromise;
    }

    const token = await this.options.tokenStore.get();
    if (!token) throw new Error('请先登录后再建立会话 WebSocket');

    this.close();
    this.sessionId = normalizedSessionId;
    const socket = this.webSocketFactory(
      sessionWebSocketUrl(this.options.baseUrl, token),
    );
    this.socket = socket;
    socket.onmessage = (event) => this.handleAck(event.data);
    socket.onclose = () => {
      this.rejectPendingAcks(new Error('会话 WebSocket 已关闭'));
      this.connectPromise = null;
    };

    this.connectPromise = new Promise<void>((resolve, reject) => {
      const timer = setTimeout(() => {
        this.connectPromise = null;
        reject(new Error('会话 WebSocket 连接超时'));
      }, this.connectTimeoutMs);
      socket.onopen = () => {
        clearTimeout(timer);
        this.connectPromise = null;
        resolve();
      };
      socket.onerror = () => {
        clearTimeout(timer);
        this.connectPromise = null;
        reject(new Error('会话 WebSocket 连接失败'));
      };
    });
    return this.connectPromise;
  }

  async persistMessage(message: SessionMessage) {
    const content = message.content.trim();
    if (!content) return;
    await this.sendFrame('message', {
      owner: message.owner,
      content,
      audio: null,
    });
  }

  end(stopTime: string) {
    return this.sendFrame('end', null, stopTime);
  }

  close() {
    const socket = this.socket;
    this.socket = null;
    this.sessionId = null;
    this.connectPromise = null;
    this.rejectPendingAcks(new Error('会话 WebSocket 已关闭'));
    if (socket && socket.readyState < 2) {
      socket.close();
    }
  }

  private async sendFrame(
    type: 'message' | 'end',
    message: { owner: 0 | 1; content: string; audio: null } | null,
    stopTime: string | null = null,
  ) {
    const socket = this.socket;
    const sessionId = this.sessionId;
    if (!sessionId) throw new Error('会话 ID 尚未建立');
    if (!socket || socket.readyState !== 1) {
      throw new Error('会话 WebSocket 尚未连接');
    }

    const ack = new Promise<SessionSocketAck>((resolve, reject) => {
      const timer = setTimeout(() => {
        this.pendingAcks = this.pendingAcks.filter(
          (pending) => pending.resolve !== resolve,
        );
        reject(new Error(`等待 session.${type}.accepted 超时`));
      }, this.ackTimeoutMs);
      this.pendingAcks.push({ operation: type, resolve, reject, timer });
    });
    socket.send(
      JSON.stringify({ type, sessionId, message, stopTime }),
    );
    return ack;
  }

  private handleAck(data: string) {
    let ack: SessionSocketAck;
    try {
      ack = JSON.parse(data) as SessionSocketAck;
    } catch {
      return;
    }
    const operation = String(ack.type ?? '').split('.')[1];
    const index = this.pendingAcks.findIndex(
      (pending) => pending.operation === operation,
    );
    if (index < 0) return;
    const [pending] = this.pendingAcks.splice(index, 1);
    clearTimeout(pending.timer);
    if (ack.success) {
      pending.resolve(ack);
    } else {
      pending.reject(
        new Error(ack.message || ack.code || '会话消息处理失败'),
      );
    }
  }

  private rejectPendingAcks(error: Error) {
    const pending = this.pendingAcks.splice(0);
    pending.forEach((ack) => {
      clearTimeout(ack.timer);
      ack.reject(error);
    });
  }
}
