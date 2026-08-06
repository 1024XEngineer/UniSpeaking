import { normalizeQwenEvent } from './QwenEventNormalizer';
import { RealtimeStateMachine } from './RealtimeStateMachine';
import type {
  RealtimeDomainEvent,
  RealtimeError,
  RealtimeErrorCode,
  RealtimeState,
} from './types';
import type {
  DialogueCompletion,
  ScenarioDialogueState,
} from '@/features/scenes/SceneDialogueApi';

export type RealtimeTransportEvent =
  | { type: 'provider.message'; data: string }
  | { type: 'connection.failed'; message?: string }
  | { type: 'ice.failed'; message?: string }
  | { type: 'datachannel.failed'; message?: string };

export type RealtimeTransport = {
  subscribe(listener: (event: RealtimeTransportEvent) => void): () => void;
  prepare(): Promise<void>;
  createOffer(): Promise<string>;
  applyAnswer(answerSdp: string): Promise<void>;
  waitForDataChannel(): Promise<void>;
  sendProviderEvent(event: Record<string, unknown>): void;
  setAudioEnabled(enabled: boolean): void;
  close(): void;
};

export type RealtimeSessionStartRequest = {
  sceneId: string | null;
  offerSdp: string;
  provider: 'QWEN';
  model: string;
  voice: string;
  translationEnabled: boolean;
};

export type RealtimeSessionStartResponse = {
  sessionId: string;
  answerSdp: string;
  voiceId: string;
  systemPrompt: string;
};

export type SessionMessage = {
  owner: 0 | 1;
  content: string;
  providerMessageId?: string;
};

export type RealtimeSessionDependencies = {
  transport: RealtimeTransport;
  sessionApi: {
    start(request: RealtimeSessionStartRequest): Promise<RealtimeSessionStartResponse>;
  };
  sessionSocket: {
    connect(sessionId: string): Promise<void>;
    persistMessage(message: SessionMessage): Promise<void>;
    end(stopTime: string): Promise<unknown>;
    close(): void;
  };
  sceneDialogue?: {
    advanceState(
      sessionId: string,
      turnNo: number,
      transcript: string,
    ): Promise<ScenarioDialogueState>;
    evaluateTurn(
      sessionId: string,
      turnNo: number,
      transcript: string,
    ): Promise<unknown>;
    complete(
      sessionId: string,
      stopTime: string,
    ): Promise<DialogueCompletion | null>;
  };
  now?: () => Date;
  createEventId?: () => string;
};

export type RealtimeSessionOptions = {
  mode: 'free_chat' | 'scene';
  sceneId?: string;
  voice: string;
  model: string;
  speechSpeed: 'SLOWER' | 'MODERATE' | 'NATURAL' | 'FASTER';
};

export type RealtimeSessionSnapshot = Readonly<{
  state: RealtimeState;
  muted: boolean;
  sessionId: string | null;
  userTranscript: string;
  assistantTranscript: string;
  error: RealtimeError | null;
  sceneState?: ScenarioDialogueState | null;
  completion?: DialogueCompletion | null;
}>;

const speechSpeedInstructions = {
  SLOWER:
    'Voice delivery rule: speak distinctly and very slowly, around 70 English words per minute, with clear pauses between short phrases.',
  MODERATE:
    'Voice delivery rule: speak at a calm moderate pace, around 120 English words per minute, with clear pauses between ideas.',
  NATURAL:
    'Voice delivery rule: speak at a natural conversational pace, around 165 English words per minute.',
  FASTER:
    'Voice delivery rule: speak quickly but clearly, around 210 English words per minute, without dropping or slurring words.',
} as const;

function buildSessionUpdate(
  eventId: string,
  response: RealtimeSessionStartResponse,
  options: RealtimeSessionOptions,
) {
  return {
    event_id: eventId,
    type: 'session.update',
    session: {
      modalities: ['text', 'audio'],
      voice: response.voiceId || options.voice,
      instructions: [
        response.systemPrompt,
        speechSpeedInstructions[options.speechSpeed],
      ]
        .filter(Boolean)
        .join('\n\n'),
      input_audio_format: 'pcm',
      output_audio_format: 'pcm',
      input_audio_transcription: { model: 'qwen3-asr-flash-realtime' },
      smooth_output: false,
      turn_detection: {
        type: options.model.startsWith('qwen3.5-omni-')
          ? 'semantic_vad'
          : 'server_vad',
        threshold: 0.5,
        prefix_padding_ms: 500,
        silence_duration_ms: 600,
        create_response: options.mode === 'free_chat',
        interrupt_response: true,
      },
    },
  };
}

function toRealtimeError(
  code: RealtimeErrorCode,
  error: unknown,
  retryable: boolean,
): RealtimeError {
  return {
    code,
    message: error instanceof Error ? error.message : '无法开始实时对话',
    retryable,
  };
}

type SnapshotListener = (snapshot: RealtimeSessionSnapshot) => void;

export class RealtimeSessionController {
  private readonly machine = new RealtimeStateMachine();
  private readonly listeners = new Set<SnapshotListener>();
  private readonly now: () => Date;
  private readonly createEventId: () => string;
  private readonly unsubscribeTransport: () => void;
  private backendSession: RealtimeSessionStartResponse | null = null;
  private muted = false;
  private inputEnabled = false;
  private userTranscript = '';
  private assistantTranscript = '';
  private providerConfigured = false;
  private initialResponseRequested = false;
  private readonly persistedMessageIds = new Set<string>();
  private endPromise: Promise<unknown> | null = null;
  private learnerTurnNo = 0;
  private sceneState: ScenarioDialogueState | null = null;
  private completion: DialogueCompletion | null = null;
  private sceneCompletionPending = false;

  constructor(
    private readonly dependencies: RealtimeSessionDependencies,
    private readonly options: RealtimeSessionOptions,
  ) {
    this.now = dependencies.now ?? (() => new Date());
    this.createEventId =
      dependencies.createEventId ??
      (() => `event_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`);
    this.unsubscribeTransport = dependencies.transport.subscribe((event) => {
      void this.handleTransportEvent(event);
    });
  }

  getSnapshot(): RealtimeSessionSnapshot {
    return {
      state: this.machine.state,
      muted: this.muted,
      sessionId: this.backendSession?.sessionId ?? null,
      userTranscript: this.userTranscript,
      assistantTranscript: this.assistantTranscript,
      error: this.machine.error,
      sceneState: this.sceneState,
      completion: this.completion,
    };
  }

  subscribe(listener: SnapshotListener) {
    this.listeners.add(listener);
    listener(this.getSnapshot());
    return () => {
      this.listeners.delete(listener);
    };
  }

  async start() {
    if (this.machine.state === 'ended' || this.machine.state === 'error') {
      this.machine.dispatch({ type: 'RESET' });
    }
    this.resetSessionValues();
    let failureCode: RealtimeErrorCode = 'MEDIA_PREPARATION_FAILED';
    try {
      this.transition({ type: 'START' });
      await this.dependencies.transport.prepare();
      this.dependencies.transport.setAudioEnabled(false);
      this.transition({ type: 'PERMISSION_GRANTED' });

      failureCode = 'OFFER_CREATION_FAILED';
      const offerSdp = await this.dependencies.transport.createOffer();
      this.transition({ type: 'OFFER_CREATED' });

      failureCode = 'SDP_EXCHANGE_FAILED';
      const backend = await this.dependencies.sessionApi.start({
        sceneId: this.options.sceneId ?? null,
        offerSdp,
        provider: 'QWEN',
        model: this.options.model,
        voice: this.options.voice,
        translationEnabled: true,
      });
      if (!backend.answerSdp?.trim()) throw new Error('后端没有返回 Answer SDP');
      if (!backend.systemPrompt?.trim()) throw new Error('后端没有返回会话提示词');
      this.backendSession = backend;

      failureCode = 'SESSION_SOCKET_FAILED';
      await this.dependencies.sessionSocket.connect(backend.sessionId);

      failureCode = 'ANSWER_APPLY_FAILED';
      await this.dependencies.transport.applyAnswer(backend.answerSdp);
      this.transition({ type: 'ANSWER_APPLIED' });

      failureCode = 'DATA_CHANNEL_FAILED';
      await this.dependencies.transport.waitForDataChannel();
      this.publish();
      return { sessionId: backend.sessionId };
    } catch (error) {
      this.inputEnabled = false;
      this.dependencies.transport.setAudioEnabled(false);
      this.dependencies.transport.close();
      this.dependencies.sessionSocket.close();
      this.machine.dispatch({
        type: 'FAIL',
        error: toRealtimeError(
          failureCode,
          error,
          failureCode !== 'MEDIA_PREPARATION_FAILED',
        ),
      });
      this.publish();
      throw error;
    }
  }

  async handleProviderMessage(data: string) {
    let raw: unknown;
    try {
      raw = JSON.parse(data);
    } catch {
      return;
    }
    for (const event of normalizeQwenEvent(raw)) {
      await this.applyProviderEvent(event);
    }
  }

  setMuted(muted: boolean) {
    this.muted = muted;
    this.applyAudioEnabled();
    this.publish();
  }

  interrupt() {
    if (this.machine.state !== 'assistant_speaking') return;
    this.dependencies.transport.sendProviderEvent({
      event_id: this.createEventId(),
      type: 'response.cancel',
    });
  }

  end() {
    if (!this.endPromise) {
      this.endPromise = this.performEnd();
    }
    return this.endPromise;
  }

  private async handleTransportEvent(event: RealtimeTransportEvent) {
    if (event.type === 'provider.message') {
      await this.handleProviderMessage(event.data);
      return;
    }
    const code: RealtimeErrorCode =
      event.type === 'ice.failed'
        ? 'ICE_FAILED'
        : event.type === 'datachannel.failed'
          ? 'DATA_CHANNEL_FAILED'
          : 'PEER_CONNECTION_FAILED';
    this.inputEnabled = false;
    this.applyAudioEnabled();
    this.machine.dispatch({
      type: 'FAIL',
      error: {
        code,
        message: event.message ?? '实时连接失败',
        retryable: true,
      },
    });
    this.publish();
  }

  private async applyProviderEvent(event: RealtimeDomainEvent) {
    switch (event.type) {
      case 'session.created':
        if (!this.providerConfigured && this.backendSession) {
          this.dependencies.transport.sendProviderEvent(
            buildSessionUpdate(
              this.createEventId(),
              this.backendSession,
              this.options,
            ),
          );
          this.providerConfigured = true;
        }
        return;
      case 'session.updated':
        if (this.machine.state === 'connecting') {
          this.transition({ type: 'CHANNEL_OPEN' });
        }
        if (!this.initialResponseRequested) {
          this.dependencies.transport.sendProviderEvent({
            event_id: this.createEventId(),
            type: 'response.create',
          });
          this.initialResponseRequested = true;
        }
        this.inputEnabled = this.options.mode === 'free_chat';
        this.applyAudioEnabled();
        this.publish();
        return;
      case 'user.speech.started':
        if (
          this.machine.state === 'ready' ||
          this.machine.state === 'assistant_speaking'
        ) {
          this.userTranscript = '';
          this.assistantTranscript = '';
          this.transition({ type: 'USER_SPEECH_STARTED' });
        }
        return;
      case 'user.speech.stopped':
        if (this.machine.state === 'user_speaking') {
          this.transition({ type: 'USER_SPEECH_STOPPED' });
        }
        return;
      case 'user.transcript.delta':
        this.userTranscript += event.text;
        this.publish();
        return;
      case 'user.transcript.preview':
        this.userTranscript = event.text;
        this.publish();
        return;
      case 'user.transcript.completed':
        this.userTranscript = event.text;
        this.publish();
        await this.persistTranscript(1, event.text, event.itemId);
        if (this.options.mode === 'scene') {
          await this.coordinateSceneTurn(event.text);
        }
        return;
      case 'assistant.response.started':
        if (
          this.machine.state === 'ready' ||
          this.machine.state === 'user_speaking'
        ) {
          this.assistantTranscript = '';
          this.transition({ type: 'ASSISTANT_SPEECH_STARTED' });
        }
        return;
      case 'assistant.transcript.delta':
        this.assistantTranscript += event.text;
        this.publish();
        return;
      case 'assistant.transcript.completed':
        this.assistantTranscript = event.text;
        this.publish();
        await this.persistTranscript(0, event.text, event.itemId);
        return;
      case 'assistant.response.completed':
        if (this.machine.state === 'assistant_speaking') {
          this.transition({ type: 'ASSISTANT_SPEECH_STOPPED' });
        }
        if (this.options.mode === 'scene') {
          this.inputEnabled = true;
          this.applyAudioEnabled();
          if (this.sceneCompletionPending) {
            await this.end();
          }
        }
        return;
      case 'provider.error':
        this.inputEnabled = false;
        this.applyAudioEnabled();
        this.machine.dispatch({
          type: 'FAIL',
          error: {
            code: 'PROVIDER_ERROR',
            message: event.message,
            retryable: true,
          },
        });
        this.publish();
        return;
    }
  }

  private async persistTranscript(
    owner: 0 | 1,
    content: string,
    providerMessageId?: string,
  ) {
    const messageId = providerMessageId ? `${owner}:${providerMessageId}` : null;
    if (messageId && this.persistedMessageIds.has(messageId)) return;
    if (messageId) this.persistedMessageIds.add(messageId);
    try {
      await this.dependencies.sessionSocket.persistMessage({
        owner,
        content,
        providerMessageId,
      });
    } catch (error) {
      if (messageId) this.persistedMessageIds.delete(messageId);
      throw error;
    }
  }

  private async performEnd() {
    if (this.machine.state === 'ended') return null;
    if (this.machine.state === 'idle') {
      this.dependencies.transport.close();
      this.dependencies.sessionSocket.close();
      return null;
    }
    this.transition({ type: 'STOP' });
    this.inputEnabled = false;
    this.applyAudioEnabled();
    let completion: unknown = null;
    try {
      if (this.backendSession) {
        const stopTime = this.now().toISOString();
        completion =
          this.options.mode === 'scene' && this.dependencies.sceneDialogue
            ? await this.dependencies.sceneDialogue.complete(
                this.backendSession.sessionId,
                stopTime,
              )
            : await this.dependencies.sessionSocket.end(stopTime);
        if (completion && typeof completion === 'object' && 'sessionId' in completion) {
          this.completion = completion as DialogueCompletion;
        }
      }
      return completion;
    } finally {
      this.dependencies.transport.close();
      this.dependencies.sessionSocket.close();
      this.unsubscribeTransport();
      if (this.machine.state === 'ending') {
        this.transition({ type: 'ENDED' });
      }
    }
  }

  private applyAudioEnabled() {
    const activeState =
      this.machine.state === 'ready' || this.machine.state === 'user_speaking';
    this.dependencies.transport.setAudioEnabled(
      activeState && this.inputEnabled && !this.muted,
    );
  }

  private async coordinateSceneTurn(transcript: string) {
    const sessionId = this.backendSession?.sessionId;
    const sceneDialogue = this.dependencies.sceneDialogue;
    if (!sessionId || !sceneDialogue) {
      throw new Error('场景对话服务尚未配置');
    }
    this.inputEnabled = false;
    this.applyAudioEnabled();
    const turnNo = ++this.learnerTurnNo;
    const evaluation = sceneDialogue
      .evaluateTurn(sessionId, turnNo, transcript)
      .catch(() => null);
    const state = await sceneDialogue.advanceState(
      sessionId,
      turnNo,
      transcript,
    );
    await evaluation;
    this.sceneState = state;
    this.sceneCompletionPending = state.completed;
    this.publish();

    if (state.controlInstruction?.trim() && this.backendSession) {
      const update = buildSessionUpdate(
        this.createEventId(),
        this.backendSession,
        this.options,
      );
      update.session.instructions = [
        update.session.instructions,
        state.controlInstruction.trim(),
      ]
        .filter(Boolean)
        .join('\n\n');
      this.dependencies.transport.sendProviderEvent(update);
    }
    this.dependencies.transport.sendProviderEvent({
      event_id: this.createEventId(),
      type: 'response.create',
    });
  }

  private transition(event: Parameters<RealtimeStateMachine['dispatch']>[0]) {
    this.machine.dispatch(event);
    this.publish();
  }

  private publish() {
    const snapshot = this.getSnapshot();
    this.listeners.forEach((listener) => listener(snapshot));
  }

  private resetSessionValues() {
    this.backendSession = null;
    this.userTranscript = '';
    this.assistantTranscript = '';
    this.providerConfigured = false;
    this.initialResponseRequested = false;
    this.inputEnabled = false;
    this.persistedMessageIds.clear();
    this.endPromise = null;
    this.learnerTurnNo = 0;
    this.sceneState = null;
    this.completion = null;
    this.sceneCompletionPending = false;
  }
}
