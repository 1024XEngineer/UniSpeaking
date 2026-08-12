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
import type {
  IeltsDialogueState,
  IeltsPart,
  IeltsPart2Event,
  IeltsPart2State,
} from '@/features/ielts/types';
import type { TurnAudioCapturePort } from '@/features/audio/TurnAudioCapture';

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
  ieltsId?: string | null;
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
  currentStage?: IeltsPart;
};

export type SessionMessage = {
  owner: 0 | 1;
  content: string;
  providerMessageId?: string;
};

export type RealtimeTranscriptEntry = Readonly<{
  id: string;
  owner: 0 | 1;
  content: string;
}>;

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
      wavUri?: string | null,
    ): Promise<unknown>;
    complete(
      sessionId: string,
      stopTime: string,
    ): Promise<DialogueCompletion | null>;
  };
  ieltsDialogue?: {
    advanceState(
      sessionId: string,
      turnNo: number,
      timedOut?: boolean,
    ): Promise<IeltsDialogueState>;
    evaluateTurn(
      sessionId: string,
      turnNo: number,
      transcript: string,
      wavUri?: string | null,
    ): Promise<unknown>;
    advancePart2State(
      sessionId: string,
      event: IeltsPart2Event,
    ): Promise<IeltsPart2State>;
    getDialogueState(sessionId: string): Promise<IeltsDialogueState>;
    getPart2State(sessionId: string): Promise<IeltsPart2State>;
  };
  turnAudioCapture?: TurnAudioCapturePort;
  now?: () => Date;
  createEventId?: () => string;
};

export type RealtimeSessionOptions = {
  mode: 'free_chat' | 'scene' | 'ielts';
  sceneId?: string;
  ieltsId?: string;
  ieltsPart?: IeltsPart;
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
  transcriptHistory: readonly RealtimeTranscriptEntry[];
  error: RealtimeError | null;
  sceneState?: ScenarioDialogueState | null;
  completion?: DialogueCompletion | null;
  ieltsDialogueState?: IeltsDialogueState | null;
  ieltsPart2State?: IeltsPart2State | null;
  ieltsDialogueCompleted?: boolean;
  ieltsInputReadyTick?: number;
  ieltsPart2CompletionReady?: boolean;
  ieltsStateRestored?: boolean;
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
        type:
          options.mode === 'ielts' &&
          (options.ieltsPart === 'PART_1' || options.ieltsPart === 'PART_3')
            ? 'server_vad'
            : options.model.startsWith('qwen3.5-omni-')
              ? 'semantic_vad'
              : 'server_vad',
        threshold: 0.5,
        prefix_padding_ms: 500,
        silence_duration_ms: options.mode === 'ielts' ? 3_000 : 600,
        create_response: options.mode === 'free_chat',
        interrupt_response:
          options.mode === 'ielts' ? options.ieltsPart !== 'PART_2' : true,
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

type PendingResponseRequest = Readonly<{
  sessionUpdate?: Record<string, unknown>;
  responseCreate: Record<string, unknown>;
}>;

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
  private transcriptHistory: RealtimeTranscriptEntry[] = [];
  private transcriptSequence = 0;
  private providerConfigured = false;
  private initialResponseRequested = false;
  private responseInFlight = false;
  private currentResponseRequest: PendingResponseRequest | null = null;
  private pendingResponseRequest: PendingResponseRequest | null = null;
  private readonly persistedMessageIds = new Set<string>();
  private readonly coordinatedUserMessageIds = new Set<string>();
  private readonly pendingTurnEvaluations = new Set<Promise<unknown>>();
  private endPromise: Promise<unknown> | null = null;
  private learnerTurnNo = 0;
  private sceneState: ScenarioDialogueState | null = null;
  private completion: DialogueCompletion | null = null;
  private sceneCompletionPending = false;
  private ieltsActivePart: IeltsPart | null = null;
  private ieltsDialogueState: IeltsDialogueState | null = null;
  private ieltsPart2State: IeltsPart2State | null = null;
  private ieltsDialogueCompleted = false;
  private ieltsInputReadyTick = 0;
  private ieltsPart2CompletionReady = false;
  private ieltsTimedOutTurn: { turnNo: number } | null = null;
  private ieltsStateRestored = false;
  private turnAudioWarning = false;

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
      transcriptHistory: this.transcriptHistory,
      error: this.machine.error,
      sceneState: this.sceneState,
      completion: this.completion,
      ieltsDialogueState: this.ieltsDialogueState,
      ieltsPart2State: this.ieltsPart2State,
      ieltsDialogueCompleted: this.ieltsDialogueCompleted,
      ieltsInputReadyTick: this.ieltsInputReadyTick,
      ieltsPart2CompletionReady: this.ieltsPart2CompletionReady,
      ieltsStateRestored: this.ieltsStateRestored,
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
        ieltsId: this.options.ieltsId ?? null,
        offerSdp,
        provider: 'QWEN',
        model: this.options.model,
        voice: this.options.voice,
        translationEnabled: true,
      });
      if (!backend.answerSdp?.trim()) throw new Error('后端没有返回 Answer SDP');
      if (!backend.systemPrompt?.trim()) throw new Error('后端没有返回会话提示词');
      this.backendSession = backend;
      if (this.options.mode === 'ielts') {
        this.ieltsActivePart =
          backend.currentStage ?? this.options.ieltsPart ?? null;
      }

      failureCode = 'SESSION_SOCKET_FAILED';
      await this.dependencies.sessionSocket.connect(backend.sessionId);

      failureCode = 'ANSWER_APPLY_FAILED';
      await this.dependencies.transport.applyAnswer(backend.answerSdp);
      this.transition({ type: 'ANSWER_APPLIED' });

      failureCode = 'DATA_CHANNEL_FAILED';
      await this.dependencies.transport.waitForDataChannel();
      if (this.options.mode === 'ielts') {
        await this.restoreIeltsState();
      }
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

  async transitionPart2(event: IeltsPart2Event) {
    const sessionId = this.backendSession?.sessionId;
    const ieltsDialogue = this.dependencies.ieltsDialogue;
    if (
      !sessionId ||
      !ieltsDialogue ||
      this.ieltsActivePart !== 'PART_2'
    ) {
      throw new Error('当前会话不是 IELTS Part 2');
    }
    const completing =
      event === 'ANSWER_COMPLETE' || event === 'LONG_TURN_TIME_LIMIT';
    if (completing) {
      this.inputEnabled = false;
      this.muted = true;
      this.applyAudioEnabled();
      this.dependencies.turnAudioCapture?.stop();
    }
    const state = await ieltsDialogue.advancePart2State(sessionId, event);
    this.ieltsPart2State = state;
    this.ieltsDialogueCompleted = Boolean(state.completed);
    if (event === 'PREPARATION_COMPLETE') {
      this.muted = false;
      this.inputEnabled = false;
      this.applyAudioEnabled();
    }
    this.publish();
    this.sendIeltsControlInstruction(state.controlInstruction);
    this.requestIeltsResponse(state.controlInstruction);
    return state;
  }

  async forcePart3Timeout() {
    const sessionId = this.backendSession?.sessionId;
    const ieltsDialogue = this.dependencies.ieltsDialogue;
    if (
      !sessionId ||
      !ieltsDialogue ||
      this.ieltsActivePart !== 'PART_3' ||
      this.ieltsDialogueCompleted
    ) {
      return null;
    }
    const turnNo = this.learnerTurnNo + 1;
    this.inputEnabled = false;
    this.muted = true;
    this.applyAudioEnabled();
    this.ieltsTimedOutTurn = { turnNo };
    const state = await ieltsDialogue.advanceState(sessionId, turnNo, true);
    this.learnerTurnNo = turnNo;
    this.ieltsDialogueState = state;
    this.ieltsDialogueCompleted = Boolean(state.completed);
    this.publish();
    this.sendIeltsControlInstruction(state.controlInstruction);
    this.requestIeltsResponse(state.controlInstruction);
    this.muted = false;
    this.applyAudioEnabled();
    return state;
  }

  async restoreIeltsState() {
    const sessionId = this.backendSession?.sessionId;
    const ieltsDialogue = this.dependencies.ieltsDialogue;
    if (
      this.options.mode !== 'ielts' ||
      !sessionId ||
      !ieltsDialogue ||
      this.ieltsStateRestored
    ) {
      return null;
    }
    try {
      if (this.ieltsActivePart === 'PART_2') {
        const state = await ieltsDialogue.getPart2State(sessionId);
        this.ieltsPart2State = state;
        this.ieltsDialogueCompleted = Boolean(state.completed);
        this.applyRestoredInstruction(state.controlInstruction);
        this.ieltsStateRestored = true;
        this.publish();
        return state;
      }
      if (this.isDeterministicIeltsPart()) {
        const state = await ieltsDialogue.getDialogueState(sessionId);
        this.ieltsDialogueState = state;
        this.learnerTurnNo = state.answeredQuestions;
        this.ieltsDialogueCompleted = Boolean(state.completed);
        this.applyRestoredInstruction(state.controlInstruction);
        if (state.completed) {
          this.inputEnabled = false;
          this.applyAudioEnabled();
        }
        this.ieltsStateRestored = true;
        this.publish();
        return state;
      }
    } catch {
      return null;
    }
    return null;
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
          this.requestAssistantResponse();
          this.initialResponseRequested = true;
        }
        this.inputEnabled =
          this.options.mode === 'free_chat'
            ? true
            : this.options.mode === 'ielts'
              ? false
              : false;
        this.applyAudioEnabled();
        this.publish();
        return;
      case 'user.speech.started':
        if (
          this.machine.state === 'ready' ||
          this.machine.state === 'assistant_speaking'
        ) {
          this.captureTranscript(0, this.assistantTranscript);
          this.userTranscript = '';
          this.assistantTranscript = '';
          this.transition({ type: 'USER_SPEECH_STARTED' });
          this.beginTurnAudioCapture();
        }
        return;
      case 'user.speech.stopped':
        if (this.machine.state === 'user_speaking') {
          this.transition({ type: 'USER_SPEECH_STOPPED' });
          this.dependencies.turnAudioCapture?.stop();
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
        this.captureTranscript(1, event.text, event.itemId);
        this.publish();
        if (
          event.itemId &&
          this.coordinatedUserMessageIds.has(event.itemId)
        ) {
          return;
        }
        if (event.itemId) this.coordinatedUserMessageIds.add(event.itemId);
        try {
          await this.persistTranscript(1, event.text, event.itemId);
        } catch (error) {
          if (this.options.mode !== 'ielts') throw error;
        }
        if (this.options.mode === 'scene') {
          await this.coordinateSceneTurn(event.text);
        } else if (this.options.mode === 'ielts') {
          await this.coordinateIeltsTurn(event.text);
        }
        return;
      case 'assistant.response.started':
        this.responseInFlight = true;
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
        this.captureTranscript(0, event.text, event.itemId);
        this.publish();
        await this.persistTranscript(0, event.text, event.itemId);
        return;
      case 'assistant.response.completed':
        this.responseInFlight = false;
        this.currentResponseRequest = null;
        if (this.machine.state === 'assistant_speaking') {
          this.transition({ type: 'ASSISTANT_SPEECH_STOPPED' });
        }
        if (this.flushPendingResponse()) return;
        if (this.options.mode === 'scene') {
          this.inputEnabled = true;
          this.applyAudioEnabled();
          if (this.sceneCompletionPending) {
            await this.end();
          }
        } else if (this.options.mode === 'ielts') {
          this.handleIeltsAssistantResponseCompleted();
        }
        return;
      case 'provider.error':
        if (/conversation already has an active response/i.test(event.message)) {
          if (!this.pendingResponseRequest && this.currentResponseRequest) {
            this.pendingResponseRequest = this.currentResponseRequest;
          }
          this.currentResponseRequest = null;
          this.responseInFlight = true;
          this.inputEnabled = this.options.mode === 'free_chat';
          this.applyAudioEnabled();
          this.publish();
          return;
        }
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

  private captureTranscript(
    owner: 0 | 1,
    content: string,
    providerMessageId?: string,
  ) {
    const text = content.trim();
    if (!text) return;
    const id = providerMessageId
      ? `${owner}:${providerMessageId}`
      : `${owner}:local-${this.transcriptSequence++}`;
    const existingIndex = this.transcriptHistory.findIndex((item) => item.id === id);
    const entry = { id, owner, content: text } as const;
    if (existingIndex >= 0) {
      this.transcriptHistory = this.transcriptHistory.map((item, index) =>
        index === existingIndex ? entry : item,
      );
      return;
    }
    const previous = this.transcriptHistory[this.transcriptHistory.length - 1];
    if (previous?.owner === owner && previous.content === text) return;
    this.transcriptHistory = [...this.transcriptHistory, entry];
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
        await this.waitForPendingTurnEvaluations();
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

  private isDeterministicIeltsPart() {
    return (
      this.ieltsActivePart === 'PART_1' || this.ieltsActivePart === 'PART_3'
    );
  }

  private bumpIeltsInputReady() {
    this.ieltsInputReadyTick += 1;
    this.publish();
  }

  private releaseIeltsInput() {
    if (
      !this.isDeterministicIeltsPart() ||
      this.ieltsDialogueCompleted ||
      this.muted
    ) {
      return;
    }
    this.inputEnabled = true;
    this.applyAudioEnabled();
    this.bumpIeltsInputReady();
  }

  private handleIeltsAssistantResponseCompleted() {
    if (this.ieltsActivePart === 'PART_2' && this.ieltsDialogueCompleted) {
      this.inputEnabled = false;
      this.ieltsPart2CompletionReady = true;
      this.applyAudioEnabled();
      this.publish();
      return;
    }
    if (this.isDeterministicIeltsPart()) {
      if (this.ieltsDialogueCompleted) {
        this.inputEnabled = false;
        this.applyAudioEnabled();
        void this.end();
        return;
      }
      this.releaseIeltsInput();
      return;
    }
    if (this.ieltsActivePart === 'PART_2' && !this.ieltsDialogueCompleted) {
      this.inputEnabled = true;
      this.applyAudioEnabled();
      this.bumpIeltsInputReady();
    }
  }

  private applyRestoredInstruction(controlInstruction?: string | null) {
    const instruction = controlInstruction?.trim();
    if (!instruction || !this.backendSession || !this.providerConfigured) return;
    this.sendIeltsControlInstruction(instruction);
  }

  private beginTurnAudioCapture() {
    if (
      !this.dependencies.turnAudioCapture ||
      (this.options.mode !== 'ielts' && this.options.mode !== 'scene')
    ) {
      return;
    }
    void this.dependencies.turnAudioCapture.start().catch(() => {
      this.turnAudioWarning = true;
    });
  }

  private async takeTurnAudioUri() {
    const capture = this.dependencies.turnAudioCapture;
    if (!capture) return null;
    try {
      return await capture.take();
    } catch {
      return null;
    }
  }

  private async evaluateIeltsTurn(
    sessionId: string,
    turnNo: number,
    transcript: string,
  ) {
    const ieltsDialogue = this.dependencies.ieltsDialogue;
    if (!ieltsDialogue) return null;
    const evaluation = (async () => {
      const wavUri = await this.takeTurnAudioUri();
      return ieltsDialogue
        .evaluateTurn(sessionId, turnNo, transcript, wavUri)
        .catch(() => null);
    })();
    this.pendingTurnEvaluations.add(evaluation);
    try {
      return await evaluation;
    } finally {
      this.pendingTurnEvaluations.delete(evaluation);
    }
  }

  private async waitForPendingTurnEvaluations() {
    while (this.pendingTurnEvaluations.size > 0) {
      await Promise.allSettled([...this.pendingTurnEvaluations]);
    }
  }

  private sendIeltsControlInstruction(controlInstruction?: string | null) {
    const instruction = controlInstruction?.trim();
    if (!instruction || !this.backendSession) return;
    const update = buildSessionUpdate(
      this.createEventId(),
      this.backendSession,
      {
        ...this.options,
        ieltsPart: this.ieltsActivePart ?? this.options.ieltsPart,
      },
    );
    update.session.instructions = [
      update.session.instructions,
      instruction,
    ]
      .filter(Boolean)
      .join('\n\n');
    this.dependencies.transport.sendProviderEvent(update);
  }

  private requestIeltsResponse(instructions?: string | null) {
    const turnInstructions = instructions?.trim() ?? '';
    this.requestAssistantResponse(
      turnInstructions
        ? {
            response: {
              instructions: turnInstructions,
              modalities: ['text', 'audio'],
            },
          }
        : undefined,
    );
  }

  private async coordinateIeltsTurn(transcript: string) {
    const sessionId = this.backendSession?.sessionId;
    const ieltsDialogue = this.dependencies.ieltsDialogue;
    if (!sessionId || !ieltsDialogue) {
      throw new Error('IELTS 对话服务尚未配置');
    }
    const timedOutTurn =
      this.ieltsActivePart === 'PART_3' && this.ieltsTimedOutTurn
        ? this.ieltsTimedOutTurn
        : null;
    if (timedOutTurn) {
      this.ieltsTimedOutTurn = null;
      void this.evaluateIeltsTurn(sessionId, timedOutTurn.turnNo, transcript);
      return;
    }
    if (this.isDeterministicIeltsPart()) {
      this.inputEnabled = false;
      this.applyAudioEnabled();
      const turnNo = ++this.learnerTurnNo;
      const evaluation = this.evaluateIeltsTurn(sessionId, turnNo, transcript);
      let state: IeltsDialogueState | null = null;
      try {
        state = await ieltsDialogue.advanceState(sessionId, turnNo, false);
      } catch {
        state = null;
      }
      await evaluation;
      if (state) {
        this.ieltsDialogueState = state;
        this.ieltsDialogueCompleted = Boolean(state.completed);
        this.publish();
        this.sendIeltsControlInstruction(state.controlInstruction);
        this.requestIeltsResponse(state.controlInstruction);
      }
      return;
    }
    if (this.ieltsActivePart === 'PART_2') {
      const turnNo = ++this.learnerTurnNo;
      await this.evaluateIeltsTurn(sessionId, turnNo, transcript);
      if (!this.ieltsDialogueCompleted) {
        this.inputEnabled = true;
        this.applyAudioEnabled();
        this.bumpIeltsInputReady();
      }
    }
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
    const wavUri = await this.takeTurnAudioUri();
    const evaluation = sceneDialogue
      .evaluateTurn(sessionId, turnNo, transcript, wavUri)
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

    let sessionUpdate: Record<string, unknown> | undefined;
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
      sessionUpdate = update;
    }
    this.requestAssistantResponse(undefined, sessionUpdate);
  }

  private requestAssistantResponse(
    response?: Record<string, unknown>,
    sessionUpdate?: Record<string, unknown>,
  ) {
    const request: PendingResponseRequest = {
      sessionUpdate,
      responseCreate: {
        event_id: this.createEventId(),
        type: 'response.create',
        ...(response ? { response } : {}),
      },
    };
    if (this.responseInFlight) {
      this.pendingResponseRequest = request;
      return false;
    }
    this.dispatchResponseRequest(request);
    return true;
  }

  private dispatchResponseRequest(request: PendingResponseRequest) {
    this.responseInFlight = true;
    this.currentResponseRequest = request;
    try {
      if (request.sessionUpdate) {
        this.dependencies.transport.sendProviderEvent(request.sessionUpdate);
      }
      this.dependencies.transport.sendProviderEvent(request.responseCreate);
    } catch (error) {
      this.responseInFlight = false;
      this.currentResponseRequest = null;
      throw error;
    }
  }

  private flushPendingResponse() {
    const request = this.pendingResponseRequest;
    if (!request) return false;
    this.pendingResponseRequest = null;
    this.dispatchResponseRequest(request);
    return true;
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
    this.transcriptHistory = [];
    this.transcriptSequence = 0;
    this.providerConfigured = false;
    this.initialResponseRequested = false;
    this.responseInFlight = false;
    this.currentResponseRequest = null;
    this.pendingResponseRequest = null;
    this.inputEnabled = false;
    this.persistedMessageIds.clear();
    this.coordinatedUserMessageIds.clear();
    this.pendingTurnEvaluations.clear();
    this.endPromise = null;
    this.learnerTurnNo = 0;
    this.sceneState = null;
    this.completion = null;
    this.sceneCompletionPending = false;
    this.ieltsActivePart =
      this.options.mode === 'ielts' ? this.options.ieltsPart ?? null : null;
    this.ieltsDialogueState = null;
    this.ieltsPart2State = null;
    this.ieltsDialogueCompleted = false;
    this.ieltsInputReadyTick = 0;
    this.ieltsPart2CompletionReady = false;
    this.ieltsTimedOutTurn = null;
    this.ieltsStateRestored = false;
    this.turnAudioWarning = false;
  }
}
