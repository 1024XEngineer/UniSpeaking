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
  bindSession?(sessionId: string): void;
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
  ieltsCompletionReady?: boolean;
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

const SCENE_AUDIO_DRAIN_MS = 1_200;
const SCENE_FILLER_CONTINUATION_MS = 1_200;
const SCENE_TRAILING_FILLER =
  /(?:^|[\s,;:])(?:uh+|um+|erm+|em+|hmm+|ah+|well|you know|let me (?:think|see))[\s.!?,;:~-]*$/i;

function hasTrailingSceneFiller(text: string) {
  return SCENE_TRAILING_FILLER.test(text.trim());
}

function mergeSceneTranscript(left: string, right: string) {
  const prefix = left.trim().replace(/[.!?]+$/, '');
  const suffix = right.trim();
  return [prefix, suffix].filter(Boolean).join(' ');
}

function assistantResponseInvitesReply(text: string) {
  const terminalText = text
    .trim()
    .replace(/["'\u2019\u201D)\]]+$/, '')
    .trimEnd();
  return /[?\uFF1F]$/.test(terminalText);
}

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
      input_audio_transcription: {
        model: 'qwen3-asr-flash-realtime',
        ...(options.mode === 'scene' ? { language: 'en' } : {}),
      },
      smooth_output: false,
      turn_detection: {
        type:
          options.mode === 'ielts' &&
          (options.ieltsPart === 'PART_1' || options.ieltsPart === 'PART_3')
            ? 'server_vad'
            : options.model.startsWith('qwen3.5-omni-')
              ? 'semantic_vad'
              : 'server_vad',
        threshold: options.mode === 'scene' ? 0.4 : 0.5,
        prefix_padding_ms: options.mode === 'scene' ? 1_000 : 500,
        silence_duration_ms: options.mode === 'ielts' ? 3_000 : 600,
        // Scene dialogue uses an explicit opening response, then lets Realtime
        // VAD own subsequent responses. State advancement and WAV scoring stay
        // off this response path.
        create_response: options.mode !== 'ielts',
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

type CompletedUserTranscriptEvent = Extract<
  RealtimeDomainEvent,
  { type: 'user.transcript.completed' }
>;

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
  private sceneTurnResponseCompleted = false;
  private sceneStatePipeline: Promise<ScenarioDialogueState | null> = Promise.resolve(null);
  private sceneAudioDrainTimer: ReturnType<typeof setTimeout> | null = null;
  private sceneContinuationTimer: ReturnType<typeof setTimeout> | null = null;
  private scenePendingTranscript: CompletedUserTranscriptEvent | null = null;
  private sceneUserTurnPending = false;
  private ieltsActivePart: IeltsPart | null = null;
  private ieltsDialogueState: IeltsDialogueState | null = null;
  private ieltsPart2State: IeltsPart2State | null = null;
  private ieltsDialogueCompleted = false;
  private ieltsCompletionReady = false;
  private ieltsInputReadyTick = 0;
  private ieltsPart2CompletionReady = false;
  private ieltsTimedOutTurn: { turnNo: number } | null = null;
  private ieltsStateRestored = false;
  private turnAudioWarning = false;
  private turnAudioCaptureStarted = false;
  private turnAudioCaptureStartPromise: Promise<void> | null = null;
  private sceneTurnWithoutCapture = false;

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
      ieltsCompletionReady: this.ieltsCompletionReady,
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
	  this.dependencies.transport.bindSession?.(backend.sessionId);
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
      await this.releaseTurnAudioCapture();
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
    if (completing) {
      this.pendingResponseRequest = null;
      if (this.responseInFlight) {
        this.dependencies.transport.sendProviderEvent({
          event_id: this.createEventId(),
          type: 'response.cancel',
        });
      }
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
        // A fresh Part 1 session must use the prompt's introduction first. The
        // backend state already points at question one, which is only valid
        // after the candidate has introduced themselves.
        if (state.part !== 'PART_1' || state.openingCompleted) {
          this.applyRestoredInstruction(state.controlInstruction);
        }
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

  waitForTurnEvaluations() {
    return this.waitForPendingTurnEvaluations();
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
    void this.releaseTurnAudioCapture();
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
        if (this.options.mode === 'scene' && !this.inputEnabled) return;
        if (this.options.mode === 'scene') {
          this.clearSceneContinuationTimer();
          this.sceneUserTurnPending = true;
        }
        if (
          this.machine.state === 'ready' ||
          this.machine.state === 'assistant_speaking'
        ) {
          const sceneTurnWithoutCapture =
            this.options.mode === 'scene' &&
            (this.machine.state === 'assistant_speaking' || !this.turnAudioCaptureStarted);
          if (sceneTurnWithoutCapture) {
            this.sceneTurnWithoutCapture = true;
            await this.releaseTurnAudioCapture();
          }
          this.captureTranscript(0, this.assistantTranscript);
          this.userTranscript = this.scenePendingTranscript
            ? `${this.scenePendingTranscript.text.trim().replace(/[.!?]+$/, '')} `
            : '';
          this.assistantTranscript = '';
          this.transition({ type: 'USER_SPEECH_STARTED' });
          if (!sceneTurnWithoutCapture) void this.beginTurnAudioCapture();
        }
        return;
      case 'user.speech.stopped':
        if (this.options.mode === 'scene' && !this.inputEnabled) return;
        if (this.machine.state === 'user_speaking') {
          this.transition({ type: 'USER_SPEECH_STOPPED' });
          this.dependencies.turnAudioCapture?.stop();
        }
        return;
      case 'user.transcript.delta':
        if (this.options.mode === 'scene' && !this.inputEnabled) return;
        this.userTranscript += event.text;
        this.publish();
        return;
      case 'user.transcript.preview':
        if (this.options.mode === 'scene' && !this.inputEnabled) return;
        this.userTranscript = this.scenePendingTranscript
          ? mergeSceneTranscript(this.scenePendingTranscript.text, event.text)
          : event.text;
        this.publish();
        return;
      case 'user.transcript.completed':
        await this.handleCompletedUserTranscript(event);
        return;
      case 'assistant.response.started':
        this.clearSceneAudioDrain();
        this.responseInFlight = true;
        if (
          this.machine.state === 'ready' ||
          this.machine.state === 'user_speaking'
        ) {
          this.assistantTranscript = '';
          this.transition({ type: 'ASSISTANT_SPEECH_STARTED' });
        }
        if (this.options.mode === 'scene' && !this.sceneCompletionPending) {
          this.inputEnabled = true;
          this.applyAudioEnabled();
          this.publish();
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
          this.sceneTurnResponseCompleted = true;
          if (this.deferSceneCompletionForAssistantQuestion()) return;
          this.scheduleSceneAfterAudioDrain();
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
          this.inputEnabled =
            this.options.mode === 'free_chat' ||
            (this.options.mode === 'scene' && !this.sceneCompletionPending);
          this.applyAudioEnabled();
          this.publish();
          return;
        }
        this.inputEnabled = false;
        this.applyAudioEnabled();
        void this.releaseTurnAudioCapture();
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
    this.clearSceneAudioDrain();
    this.clearSceneContinuationTimer();
    this.scenePendingTranscript = null;
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
        if (this.options.mode === 'scene') {
          await this.waitForPendingTurnEvaluations();
        }
        if (this.dependencies.turnAudioCapture?.release) {
          await this.releaseTurnAudioCapture();
        }
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
      if (this.dependencies.turnAudioCapture?.release) {
        await this.releaseTurnAudioCapture();
      }
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
      this.machine.state === 'ready' ||
      this.machine.state === 'user_speaking' ||
      (this.options.mode === 'scene' && this.machine.state === 'assistant_speaking');
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
        this.ieltsCompletionReady = true;
        this.applyAudioEnabled();
        this.publish();
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

  private beginTurnAudioCapture(): Promise<void> {
    if (
      !this.dependencies.turnAudioCapture ||
      (this.options.mode !== 'ielts' && this.options.mode !== 'scene')
    ) {
      return Promise.resolve();
    }
    if (this.turnAudioCaptureStartPromise) {
      return this.turnAudioCaptureStartPromise;
    }
    if (this.turnAudioCaptureStarted) return Promise.resolve();
    const startPromise = this.dependencies.turnAudioCapture.start()
      .then(() => {
        this.turnAudioCaptureStarted = true;
      })
      .catch(() => {
        this.turnAudioCaptureStarted = false;
        this.turnAudioWarning = true;
      });
    this.turnAudioCaptureStartPromise = startPromise;
    return startPromise;
  }

  private async takeTurnAudioUri() {
    const capture = this.dependencies.turnAudioCapture;
    if (!capture) return null;
    try {
      return await capture.take();
    } catch {
      return null;
    } finally {
      this.turnAudioCaptureStarted = false;
      this.turnAudioCaptureStartPromise = null;
    }
  }

  private async releaseTurnAudioCapture() {
    const capture = this.dependencies.turnAudioCapture;
    if (this.turnAudioCaptureStartPromise) {
      await this.turnAudioCaptureStartPromise;
    }
    const release = capture?.release.bind(capture);
    if (typeof release === 'function') {
      await release().catch(() => undefined);
    }
    this.turnAudioCaptureStarted = false;
    this.turnAudioCaptureStartPromise = null;
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
            instructions: turnInstructions,
            modalities: ['text', 'audio'],
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
      const isPartOneIntroduction =
        this.ieltsActivePart === 'PART_1' &&
        this.ieltsDialogueState?.openingCompleted === false;
      if (!isPartOneIntroduction) {
        void this.evaluateIeltsTurn(sessionId, turnNo, transcript);
      }
      let state: IeltsDialogueState | null = null;
      try {
        state = await ieltsDialogue.advanceState(sessionId, turnNo, false);
      } catch {
        state = null;
      }
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

  private async handleCompletedUserTranscript(
    event: CompletedUserTranscriptEvent,
    allowFillerDeferral = true,
  ) {
    let completed = event;
    if (this.options.mode === 'scene') {
      if (!this.inputEnabled || this.sceneCompletionPending) {
        this.dependencies.turnAudioCapture?.stop();
        return;
      }
      const pending = this.scenePendingTranscript;
      const repeatedPendingItem = Boolean(
        pending?.itemId && event.itemId && pending.itemId === event.itemId,
      );
      completed = {
        ...event,
        text: pending && !repeatedPendingItem
          ? mergeSceneTranscript(pending.text, event.text)
          : event.text,
        itemId: event.itemId ?? pending?.itemId,
      };
      if (allowFillerDeferral && hasTrailingSceneFiller(completed.text)) {
        this.scenePendingTranscript = completed;
        this.userTranscript = completed.text;
        this.dependencies.turnAudioCapture?.stop();
        this.sceneTurnWithoutCapture = true;
        await this.releaseTurnAudioCapture();
        this.scheduleSceneContinuation();
        this.publish();
        return;
      }
      this.clearSceneContinuationTimer();
      this.scenePendingTranscript = null;
      this.dependencies.turnAudioCapture?.stop();
    }
    this.userTranscript = completed.text;
    if (this.options.mode === 'scene') {
      this.sceneTurnResponseCompleted = false;
    }
    this.captureTranscript(1, completed.text, completed.itemId);
    this.publish();
    if (
      completed.itemId &&
      this.coordinatedUserMessageIds.has(completed.itemId)
    ) {
      return;
    }
    if (completed.itemId) {
      this.coordinatedUserMessageIds.add(completed.itemId);
    }
    try {
      await this.persistTranscript(1, completed.text, completed.itemId);
    } catch (error) {
      if (this.options.mode !== 'ielts') throw error;
    }
    if (this.options.mode === 'scene') {
      this.sceneUserTurnPending = false;
      this.coordinateSceneTurn(completed.text);
    } else if (this.options.mode === 'ielts') {
      await this.coordinateIeltsTurn(completed.text);
    }
  }

  private coordinateSceneTurn(transcript: string) {
    const sessionId = this.backendSession?.sessionId;
    const sceneDialogue = this.dependencies.sceneDialogue;
    if (!sessionId || !sceneDialogue) {
      throw new Error('场景对话服务尚未配置');
    }
    const turnNo = ++this.learnerTurnNo;
    const wavUri = this.sceneTurnWithoutCapture
      ? this.releaseTurnAudioCapture().then(() => null)
      : this.takeTurnAudioUri();
    this.sceneTurnWithoutCapture = false;
    const evaluation = wavUri
      .then((uri) => sceneDialogue.evaluateTurn(
        sessionId,
        turnNo,
        transcript,
        uri,
      ))
      .catch(() => null);
    this.pendingTurnEvaluations.add(evaluation);
    void evaluation.finally(() => {
      this.pendingTurnEvaluations.delete(evaluation);
    });
    const stateOperation = this.sceneStatePipeline.then(() =>
      sceneDialogue.advanceState(sessionId, turnNo, transcript),
    );
    this.sceneStatePipeline = stateOperation.catch(() => null);
    void stateOperation
      .then((state) => {
        if (turnNo !== this.learnerTurnNo || this.sceneUserTurnPending) return;
        this.sceneState = state;
        this.sceneCompletionPending = Boolean(state.completed);
        this.publish();

        const turnInstruction = state.controlInstruction?.trim() ?? '';
        if (turnInstruction && this.backendSession) {
          const update = buildSessionUpdate(
            this.createEventId(),
            this.backendSession,
            this.options,
          );
          update.session.instructions = [
            update.session.instructions,
            turnInstruction,
          ]
            .filter(Boolean)
            .join('\n\n');
          this.dependencies.transport.sendProviderEvent(update);
        }

        if (state.completed) {
          if (this.deferSceneCompletionForAssistantQuestion()) return;
          this.inputEnabled = false;
          this.applyAudioEnabled();
          if (this.sceneTurnResponseCompleted && !this.responseInFlight) {
            this.scheduleSceneAfterAudioDrain();
          }
        }
      })
      .catch(() => {
        // Observation failures must not interrupt the realtime dialogue.
        this.publish();
      });
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

  private clearSceneAudioDrain() {
    if (!this.sceneAudioDrainTimer) return;
    clearTimeout(this.sceneAudioDrainTimer);
    this.sceneAudioDrainTimer = null;
  }

  private clearSceneContinuationTimer() {
    if (!this.sceneContinuationTimer) return;
    clearTimeout(this.sceneContinuationTimer);
    this.sceneContinuationTimer = null;
  }

  private scheduleSceneContinuation() {
    this.clearSceneContinuationTimer();
    this.sceneContinuationTimer = setTimeout(() => {
      this.sceneContinuationTimer = null;
      const pending = this.scenePendingTranscript;
      this.scenePendingTranscript = null;
      if (!pending || this.sceneCompletionPending) return;
      void this.handleCompletedUserTranscript(pending, false);
    }, SCENE_FILLER_CONTINUATION_MS);
  }

  private scheduleSceneAfterAudioDrain() {
    this.clearSceneAudioDrain();
    if (this.sceneCompletionPending) {
      this.inputEnabled = false;
    } else {
      this.inputEnabled = true;
    }
    this.applyAudioEnabled();
    this.publish();
    if (this.sceneTurnWithoutCapture && !this.sceneCompletionPending) return;
    this.sceneAudioDrainTimer = setTimeout(() => {
      this.sceneAudioDrainTimer = null;
      if (this.sceneCompletionPending) {
        void this.end();
        return;
      }
      if (this.machine.state !== 'ready' || this.responseInFlight) return;
      void this.enableSceneInputAfterRecordingStarts();
    }, SCENE_AUDIO_DRAIN_MS);
  }

  private deferSceneCompletionForAssistantQuestion() {
    if (
      !this.sceneCompletionPending ||
      !this.sceneTurnResponseCompleted ||
      this.responseInFlight ||
      !assistantResponseInvitesReply(this.assistantTranscript)
    ) {
      return false;
    }
    this.sceneCompletionPending = false;
    this.clearSceneAudioDrain();
    this.scheduleSceneAfterAudioDrain();
    return true;
  }

  private async enableSceneInputAfterRecordingStarts() {
    await this.beginTurnAudioCapture();
    if (
      this.sceneCompletionPending ||
      this.machine.state !== 'ready' ||
      this.responseInFlight
    ) {
      await this.releaseTurnAudioCapture();
      return;
    }
    this.inputEnabled = true;
    this.applyAudioEnabled();
    this.publish();
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
    this.clearSceneAudioDrain();
    this.clearSceneContinuationTimer();
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
    this.sceneTurnResponseCompleted = false;
    this.sceneStatePipeline = Promise.resolve(null);
    this.scenePendingTranscript = null;
    this.sceneUserTurnPending = false;
    this.ieltsActivePart =
      this.options.mode === 'ielts' ? this.options.ieltsPart ?? null : null;
    this.ieltsDialogueState = null;
    this.ieltsPart2State = null;
    this.ieltsDialogueCompleted = false;
    this.ieltsCompletionReady = false;
    this.ieltsInputReadyTick = 0;
    this.ieltsPart2CompletionReady = false;
    this.ieltsTimedOutTurn = null;
    this.ieltsStateRestored = false;
    this.turnAudioWarning = false;
    this.turnAudioCaptureStarted = false;
    this.turnAudioCaptureStartPromise = null;
    this.sceneTurnWithoutCapture = false;
  }
}
