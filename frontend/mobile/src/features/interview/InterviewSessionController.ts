import { normalizeQwenEvent } from '@/features/realtime/QwenEventNormalizer';
import type { RealtimeTransport } from '@/features/realtime/RealtimeSessionController';
import type { RealtimeDomainEvent } from '@/features/realtime/types';
import type {
  InterviewEndResponse,
  InterviewReportStatus,
  InterviewSessionApi,
  InterviewTurnState,
  StartInterviewSessionResponse,
} from './InterviewSessionApi';
import type { ContinuousTurnRecorder } from '@/features/audio/ContinuousTurnRecorder';

export type InterviewTranscript = Readonly<{
  owner: 0 | 1;
  text: string;
  itemId?: string;
}>;

export type InterviewControllerState = 'idle' | 'starting' | 'active' | 'ending' | 'ended' | 'error';

export type InterviewSessionSnapshot = Readonly<{
  state: InterviewControllerState;
  status: InterviewControllerState;
  sessionId: string | null;
  transcripts: readonly InterviewTranscript[];
  muted: boolean;
  turnNo: number;
  interviewState: InterviewTurnState | null;
  reportStatus: InterviewReportStatus | null;
  currentQuestion: string;
  error: Error | null;
}>;

type InterviewSocket = {
  connect(sessionId: string): Promise<void>;
  bindProviderSession(providerSessionId: string): Promise<unknown>;
  persistMessage(message: { owner: 0 | 1; content: string; providerMessageId?: string }): Promise<unknown>;
  close(): void;
};

type InterviewDependencies = {
  recorder: Pick<ContinuousTurnRecorder, 'start' | 'setInputEnabled' | 'speechStarted' | 'speechStopped' | 'takeTurn' | 'discard' | 'close'>;
  transport: RealtimeTransport;
  sessionApi: Pick<InterviewSessionApi, 'startSession' | 'submitTurn' | 'end'>;
  sessionSocket: InterviewSocket;
  createEventId?: () => string;
};

export type InterviewSessionOptions = {
  sceneId: string;
  voice: string;
  model: string;
};

type StartResult = Pick<StartInterviewSessionResponse, 'sessionId' | 'answerSdp' | 'voiceId' | 'systemPrompt'>;

const CLOSING_INSTRUCTION = 'The interview is complete. Give a brief, natural closing and thank the candidate for their time. Do not ask more questions.';
const CONTINUE_AFTER_CUTOFF_INSTRUCTION = 'The candidate may have been cut off while answering. Ask them to continue their answer naturally from where they stopped. Do not advance to a new topic yet.';
const REPEAT_AFTER_MISSING_AUDIO_INSTRUCTION = 'The candidate audio was not captured reliably. Briefly apologize and ask them to repeat the same answer. Do not advance to a new topic yet.';

function looksLikeCutoffTranscript(value: string) {
  const text = value.trim();
  if (!text || /[.!?。！？]$/.test(text)) return false;
  return text.split(/\s+/).length <= 2 && text.length < 16;
}

export class InterviewSessionController {
  private readonly listeners = new Set<(snapshot: InterviewSessionSnapshot) => void>();
  private readonly createEventId: () => string;
  private readonly unsubscribeTransport: () => void;
  private state: InterviewControllerState = 'idle';
  private error: Error | null = null;
  private backend: StartResult | null = null;
  private muted = true;
  private turnNo = 0;
  private interviewState: InterviewTurnState | null = null;
  private reportStatus: InterviewReportStatus | null = null;
  private currentQuestion = '';
  private readonly transcripts: InterviewTranscript[] = [];
  private readonly seenTranscriptIds = new Set<string>();
  private readonly pendingTranscriptIds = new Set<string>();
  private providerQueue: Promise<void> = Promise.resolve();
  private startPromise: Promise<{ sessionId: string }> | null = null;
  private turnOperation: Promise<void> | null = null;
  private endRequested = false;
  private closingRequested = false;
  private endPromise: Promise<InterviewEndResponse | null> | null = null;
  private configured = false;
  private openingRequested = false;

  constructor(
    private readonly dependencies: InterviewDependencies,
    private readonly options: InterviewSessionOptions,
  ) {
    this.createEventId = dependencies.createEventId ?? (() => `event_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`);
    this.unsubscribeTransport = dependencies.transport.subscribe((event) => {
      if (event.type === 'provider.message') {
        this.providerQueue = this.providerQueue
          .then(() => this.handleProviderMessage(event.data))
          .catch((cause) => this.handleFailure(cause));
      } else {
        void this.handleFailure(new Error(event.message ?? '实时连接失败'));
      }
    });
  }

  getSnapshot(): InterviewSessionSnapshot {
    return {
      state: this.state,
      status: this.state,
      sessionId: this.backend?.sessionId ?? null,
      transcripts: [...this.transcripts],
      muted: this.muted,
      turnNo: this.turnNo,
      interviewState: this.interviewState,
      reportStatus: this.reportStatus,
      currentQuestion: this.currentQuestion,
      error: this.error,
    };
  }

  subscribe(listener: (snapshot: InterviewSessionSnapshot) => void) {
    this.listeners.add(listener);
    listener(this.getSnapshot());
    return () => this.listeners.delete(listener);
  }

  start() {
    if (this.state === 'active') return Promise.resolve({ sessionId: this.backend!.sessionId });
    if (this.startPromise) return this.startPromise;
    if (this.state === 'ended' || this.state === 'error') this.reset();
    this.startPromise = this.performStart().finally(() => {
      this.startPromise = null;
    });
    return this.startPromise;
  }

  private async performStart() {
    this.state = 'starting';
    this.publish();
    try {
      // Audio Studio owns the continuous capture and must be ready before WebRTC.
      await this.dependencies.recorder.start();
      this.assertStartActive();
      this.dependencies.recorder.setInputEnabled(false);
      await this.dependencies.transport.prepare();
      this.assertStartActive();
      this.dependencies.transport.setAudioEnabled(false);
      const offerSdp = await this.dependencies.transport.createOffer();
      this.assertStartActive();
      const backend = await this.dependencies.sessionApi.startSession({
        offerSdp,
        provider: 'QWEN',
        model: this.options.model,
        voice: this.options.voice,
        translationEnabled: true,
      });
      if (!backend.answerSdp?.trim() || !backend.systemPrompt?.trim()) throw new Error('面试实时会话参数不完整');
      this.backend = backend;
      this.assertStartActive();
      await this.dependencies.sessionSocket.connect(backend.sessionId);
      this.assertStartActive();
      await this.dependencies.transport.applyAnswer(backend.answerSdp);
      this.assertStartActive();
      await this.dependencies.transport.waitForDataChannel();
      this.assertStartActive();
      this.publish();
      return { sessionId: backend.sessionId };
    } catch (cause) {
      if (!this.endRequested) await this.handleStartFailure(cause);
      throw cause instanceof Error ? cause : new Error(String(cause));
    }
  }

  async handleProviderMessage(data: string) {
    let raw: unknown;
    try { raw = JSON.parse(data); } catch { return; }
    for (const event of normalizeQwenEvent(raw)) await this.applyEvent(event);
  }

  setMuted(muted: boolean) {
    this.muted = muted;
    this.applyInput();
    this.publish();
  }

  end() {
    this.endRequested = true;
    if (!this.endPromise) this.endPromise = this.performEnd();
    return this.endPromise;
  }

  private async applyEvent(event: RealtimeDomainEvent) {
    switch (event.type) {
      case 'session.created':
        if (!this.configured && this.backend) {
          if (event.providerSessionId) {
            await this.dependencies.sessionSocket.bindProviderSession(event.providerSessionId);
          }
          this.dependencies.transport.sendProviderEvent({
            event_id: this.createEventId(), type: 'session.update', session: {
              modalities: ['text', 'audio'], voice: this.backend.voiceId || this.options.voice,
              instructions: this.backend.systemPrompt, input_audio_format: 'pcm', output_audio_format: 'pcm',
              input_audio_transcription: { model: 'qwen3-asr-flash-realtime' }, smooth_output: false,
              // Interview answers commonly contain thinking pauses. Keep the microphone
              // open through natural pauses and never let a new user turn cancel an
              // interviewer response while the candidate is still speaking.
              turn_detection: { type: 'semantic_vad', threshold: 0.8, prefix_padding_ms: 1_000, silence_duration_ms: 4_000, create_response: false, interrupt_response: true },
            },
          });
          this.configured = true;
        }
        return;
      case 'session.updated':
        this.state = 'active';
        this.muted = false;
        this.dependencies.recorder.setInputEnabled(true);
        this.applyInput();
        this.publish();
        if (!this.openingRequested) {
          this.dependencies.transport.sendProviderEvent({ event_id: this.createEventId(), type: 'response.create' });
          this.openingRequested = true;
        }
        return;
      case 'assistant.response.started':
        // Keep the candidate microphone live while the interviewer is speaking so
        // Qwen can detect a deliberate barge-in and stop its response.
        this.muted = false;
        this.state = 'active';
        this.dependencies.recorder.setInputEnabled(true);
        this.currentQuestion = '';
        this.applyInput();
        this.publish();
        return;
      case 'assistant.response.completed':
        if (!this.closingRequested && !this.endRequested) {
          this.muted = false;
          this.dependencies.recorder.setInputEnabled(true);
          this.state = 'active';
          this.applyInput();
          this.publish();
        } else if (this.closingRequested) {
          await this.end();
        }
        return;
      case 'user.speech.started':
        if (this.closingRequested || this.endRequested) return;
        this.dependencies.transport.sendProviderEvent({ event_id: this.createEventId(), type: 'response.cancel' });
        this.dependencies.recorder.speechStarted();
        return;
      case 'user.speech.stopped':
        this.dependencies.recorder.speechStopped();
        return;
      case 'user.transcript.completed':
        if (this.closingRequested || this.endRequested || !event.text.trim()) return;
        await this.processTranscriptOnce(1, event, () => {
          this.turnOperation = this.processTurn(event.text.trim(), event.itemId)
            .finally(() => { this.turnOperation = null; });
          return this.turnOperation;
        });
        return;
      case 'assistant.transcript.completed':
        if (!event.text.trim()) return;
        this.currentQuestion = event.text.trim();
        this.publish();
        await this.processTranscriptOnce(0, event, () =>
          this.persistTranscript(0, event.text.trim(), event.itemId));
        return;
      case 'provider.error':
        throw new Error(event.message);
      default:
        return;
    }
  }

  private async processTurn(transcript: string, itemId?: string) {
    this.muted = true;
    this.dependencies.recorder.setInputEnabled(false);
    this.applyInput();
    // Qwen can occasionally finalize a transcript after a brief pause. Do not
    // persist or submit a fragment as a complete interview turn: doing so would
    // advance the backend topic state (and could trigger an early end). Ask for
    // continuation and keep the same interview turn open instead.
    if (looksLikeCutoffTranscript(transcript)) {
      const fragmentAudio = await this.dependencies.recorder.takeTurn(this.turnNo + 1);
      this.dependencies.recorder.discard(fragmentAudio);
      this.muted = false;
      this.dependencies.recorder.setInputEnabled(true);
      this.applyInput();
      this.dependencies.transport.sendProviderEvent({
        event_id: this.createEventId(),
        type: 'response.create',
        response: {
          instructions: CONTINUE_AFTER_CUTOFF_INSTRUCTION,
          modalities: ['text', 'audio'],
        },
      });
      this.publish();
      return;
    }
    const nextTurnNo = this.turnNo + 1;
    const wav = await this.dependencies.recorder.takeTurn(nextTurnNo);
    if (!wav || wav.durationMs < 300) {
      this.muted = false;
      this.dependencies.recorder.setInputEnabled(true);
      this.applyInput();
      this.dependencies.transport.sendProviderEvent({
        event_id: this.createEventId(),
        type: 'response.create',
        response: { instructions: REPEAT_AFTER_MISSING_AUDIO_INSTRUCTION, modalities: ['text', 'audio'] },
      });
      this.publish();
      return;
    }
    this.turnNo = nextTurnNo;
    await this.persistTranscript(1, transcript, itemId);
    try {
      const result = await this.dependencies.sessionApi.submitTurn(this.backend!.sessionId, this.turnNo, transcript, wav?.uri);
      this.interviewState = result.state;
      this.reportStatus = result.reportStatus;
      this.publish();
      if (this.endRequested) return;
      if (result.state.shouldEnd) {
        this.closingRequested = true;
        this.muted = true;
        this.dependencies.recorder.setInputEnabled(false);
        this.applyInput();
        this.dependencies.transport.sendProviderEvent({
          event_id: this.createEventId(), type: 'response.create',
          response: { instructions: result.state.controlInstruction?.trim() || CLOSING_INSTRUCTION, modalities: ['text', 'audio'] },
        });
      } else {
        this.dependencies.transport.sendProviderEvent({
          event_id: this.createEventId(), type: 'response.create',
          response: { instructions: result.state.controlInstruction?.trim() || '', modalities: ['text', 'audio'] },
        });
      }
    } finally {
      this.dependencies.recorder.discard(wav);
    }
  }

  private async persistTranscript(owner: 0 | 1, text: string, itemId?: string) {
    await this.dependencies.sessionSocket.persistMessage({ owner, content: text, providerMessageId: itemId });
    this.transcripts.push({ owner, text, ...(itemId ? { itemId } : {}) });
    this.publish();
  }

  private async performEnd(): Promise<InterviewEndResponse | null> {
    if (this.state === 'ended') return null;
    if (this.state === 'idle') { await this.cleanupNative(); this.state = 'ended'; this.publish(); return null; }
    this.state = 'ending';
    this.muted = true;
    this.dependencies.recorder.setInputEnabled(false);
    this.applyInput();
    let turnFailure: unknown = null;
    try {
      if (this.startPromise) {
        try { await this.startPromise; } catch { /* Cancellation/failure is finalized below. */ }
      }
      if (this.turnOperation) {
        try { await this.turnOperation; } catch (cause) { turnFailure = cause; }
      }
      // Keep the data channel alive until the active turn has finished; endRequested
      // prevents it from requesting another provider response.
      this.dependencies.transport.close();
      const result = this.backend ? await this.dependencies.sessionApi.end(this.backend.sessionId) : null;
      if (result) this.reportStatus = result.reportStatus;
      if (turnFailure) this.error = turnFailure instanceof Error ? turnFailure : new Error(String(turnFailure));
      return result;
    } finally {
      await this.dependencies.recorder.close();
      this.dependencies.sessionSocket.close();
      this.unsubscribeTransport();
      this.state = 'ended';
      this.publish();
    }
  }

  private async cleanupNative() {
    this.dependencies.transport.setAudioEnabled(false);
    this.dependencies.transport.close();
    await this.dependencies.recorder.close();
    this.dependencies.sessionSocket.close();
  }

  private applyInput() {
    this.dependencies.transport.setAudioEnabled(this.state === 'active' && !this.muted && !this.closingRequested && !this.endRequested);
  }

  private assertStartActive() {
    if (this.endRequested) throw new Error('面试启动已取消');
  }

  private async processTranscriptOnce(
    owner: 0 | 1,
    event: { itemId?: string },
    operation: () => Promise<unknown>,
  ) {
    const key = event.itemId ? `${owner}:${event.itemId}` : null;
    if (key && (this.seenTranscriptIds.has(key) || this.pendingTranscriptIds.has(key))) return;
    if (key) this.pendingTranscriptIds.add(key);
    try {
      await operation();
      if (key) this.seenTranscriptIds.add(key);
    } finally {
      if (key) this.pendingTranscriptIds.delete(key);
    }
  }

  private async handleStartFailure(cause: unknown) {
    const failure = cause instanceof Error ? cause : new Error(String(cause));
    this.error = failure;
    this.state = 'error';
    this.publish();
    if (this.backend) {
      try {
        const result = await this.dependencies.sessionApi.end(this.backend.sessionId);
        this.reportStatus = result.reportStatus;
      } catch { /* Preserve the startup failure shown to the user. */ }
    }
    await this.cleanupNative();
  }

  private async handleFailure(cause: unknown) {
    if (this.state === 'ending' || this.state === 'ended') return;
    const failure = cause instanceof Error ? cause : new Error(String(cause));
    this.error = failure;
    this.endRequested = true;
    this.state = 'error';
    this.publish();
    if (this.startPromise) {
      try { await this.startPromise; } catch { /* Expected after cancellation. */ }
    }
    if (this.turnOperation) {
      try { await this.turnOperation; } catch { /* The original failure is retained. */ }
    }
    let ended = false;
    if (this.backend) {
      try {
        const result = await this.dependencies.sessionApi.end(this.backend.sessionId);
        this.reportStatus = result.reportStatus;
        ended = true;
      } catch { /* Keep the error screen so the user can explicitly end again. */ }
    }
    await this.cleanupNative();
    this.unsubscribeTransport();
    if (ended) this.state = 'ended';
    this.publish();
  }

  private reset() {
    this.state = 'idle'; this.error = null; this.backend = null; this.turnNo = 0; this.interviewState = null; this.reportStatus = null;
    this.transcripts.length = 0; this.seenTranscriptIds.clear(); this.pendingTranscriptIds.clear(); this.endRequested = false; this.closingRequested = false; this.configured = false; this.openingRequested = false; this.endPromise = null;
  }

  private publish() { const snapshot = this.getSnapshot(); this.listeners.forEach((listener) => listener(snapshot)); }
}
