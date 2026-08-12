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
import type { ContinuousTurnRecorder, TurnWav } from '@/features/audio/ContinuousTurnRecorder';

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
  error: Error | null;
}>;

type InterviewSocket = {
  connect(sessionId: string): Promise<void>;
  persistMessage(message: { owner: 0 | 1; content: string; providerMessageId?: string }): Promise<unknown>;
  close(): void;
};

type InterviewDependencies = {
  recorder: Pick<ContinuousTurnRecorder, 'start' | 'setInputEnabled' | 'speechStarted' | 'speechStopped' | 'takeTurn' | 'discard' | 'close'>;
  transport: RealtimeTransport;
  sessionApi: Pick<InterviewSessionApi, 'startSession' | 'submitTurn' | 'end'>;
  sessionSocket: InterviewSocket;
  now?: () => Date;
  createEventId?: () => string;
};

export type InterviewSessionOptions = {
  sceneId: string;
  voice: string;
  model: string;
};

type StartResult = Pick<StartInterviewSessionResponse, 'sessionId' | 'answerSdp' | 'voiceId' | 'systemPrompt'>;

const CLOSING_INSTRUCTION = 'The interview is complete. Give a brief, natural closing and thank the candidate for their time. Do not ask more questions.';

export class InterviewSessionController {
  private readonly listeners = new Set<(snapshot: InterviewSessionSnapshot) => void>();
  private readonly now: () => Date;
  private readonly createEventId: () => string;
  private readonly unsubscribeTransport: () => void;
  private state: InterviewControllerState = 'idle';
  private error: Error | null = null;
  private backend: StartResult | null = null;
  private muted = true;
  private turnNo = 0;
  private interviewState: InterviewTurnState | null = null;
  private reportStatus: InterviewReportStatus | null = null;
  private readonly transcripts: InterviewTranscript[] = [];
  private readonly seenTranscriptIds = new Set<string>();
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
    this.now = dependencies.now ?? (() => new Date());
    this.createEventId = dependencies.createEventId ?? (() => `event_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`);
    this.unsubscribeTransport = dependencies.transport.subscribe((event) => {
      if (event.type === 'provider.message') void this.handleProviderMessage(event.data);
      else void this.fail(new Error(event.message ?? '实时连接失败'));
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
      error: this.error,
    };
  }

  subscribe(listener: (snapshot: InterviewSessionSnapshot) => void) {
    this.listeners.add(listener);
    listener(this.getSnapshot());
    return () => this.listeners.delete(listener);
  }

  async start() {
    if (this.state === 'active') return { sessionId: this.backend!.sessionId };
    if (this.state === 'ended' || this.state === 'error') this.reset();
    this.state = 'starting';
    this.publish();
    try {
      // Audio Studio owns the continuous capture and must be ready before WebRTC.
      await this.dependencies.recorder.start();
      this.dependencies.recorder.setInputEnabled(false);
      await this.dependencies.transport.prepare();
      this.dependencies.transport.setAudioEnabled(false);
      const offerSdp = await this.dependencies.transport.createOffer();
      const backend = await this.dependencies.sessionApi.startSession({
        offerSdp,
        provider: 'QWEN',
        model: this.options.model,
        voice: this.options.voice,
        translationEnabled: true,
      });
      if (!backend.answerSdp?.trim() || !backend.systemPrompt?.trim()) throw new Error('面试实时会话参数不完整');
      this.backend = backend;
      await this.dependencies.sessionSocket.connect(backend.sessionId);
      await this.dependencies.transport.applyAnswer(backend.answerSdp);
      await this.dependencies.transport.waitForDataChannel();
      this.publish();
      return { sessionId: backend.sessionId };
    } catch (cause) {
      await this.cleanupNative();
      return this.fail(cause);
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
          this.dependencies.transport.sendProviderEvent({
            event_id: this.createEventId(), type: 'session.update', session: {
              modalities: ['text', 'audio'], voice: this.backend.voiceId || this.options.voice,
              instructions: this.backend.systemPrompt, input_audio_format: 'pcm', output_audio_format: 'pcm',
              input_audio_transcription: { model: 'qwen3-asr-flash-realtime' }, smooth_output: false,
              turn_detection: { type: 'semantic_vad', threshold: 0.5, prefix_padding_ms: 500, silence_duration_ms: 600, create_response: false, interrupt_response: true },
            },
          });
          this.configured = true;
        }
        return;
      case 'session.updated':
        if (!this.openingRequested) {
          this.dependencies.transport.sendProviderEvent({ event_id: this.createEventId(), type: 'response.create' });
          this.openingRequested = true;
        }
        return;
      case 'assistant.response.started':
        this.muted = true;
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
          await this.performEnd();
        }
        return;
      case 'user.speech.started':
        if (this.closingRequested || this.endRequested) return;
        this.dependencies.recorder.speechStarted();
        return;
      case 'user.speech.stopped':
        this.dependencies.recorder.speechStopped();
        return;
      case 'user.transcript.completed':
        if (this.closingRequested || !event.text.trim()) return;
        if (event.itemId && this.seenTranscriptIds.has(`1:${event.itemId}`)) return;
        if (event.itemId) this.seenTranscriptIds.add(`1:${event.itemId}`);
        this.turnOperation = this.processTurn(event.text.trim(), event.itemId).finally(() => { this.turnOperation = null; });
        await this.turnOperation;
        return;
      case 'assistant.transcript.completed':
        if (!event.text.trim() || (event.itemId && this.seenTranscriptIds.has(`0:${event.itemId}`))) return;
        if (event.itemId) this.seenTranscriptIds.add(`0:${event.itemId}`);
        await this.persistTranscript(0, event.text.trim(), event.itemId);
        return;
      case 'provider.error':
        await this.fail(new Error(event.message));
        return;
      default:
        return;
    }
  }

  private async processTurn(transcript: string, itemId?: string) {
    this.muted = true;
    this.dependencies.recorder.setInputEnabled(false);
    this.applyInput();
    await this.persistTranscript(1, transcript, itemId);
    const wav = await this.dependencies.recorder.takeTurn(++this.turnNo);
    try {
      const result = await this.dependencies.sessionApi.submitTurn(this.backend!.sessionId, this.turnNo, transcript, wav?.uri);
      this.interviewState = result.state;
      this.reportStatus = result.reportStatus;
      this.publish();
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
    try {
      // The peer is stopped before Audio Studio so no more provider audio can open a turn.
      this.dependencies.transport.close();
      if (this.turnOperation) await this.turnOperation;
      const result = this.backend ? await this.dependencies.sessionApi.end(this.backend.sessionId) : null;
      if (result) this.reportStatus = result.reportStatus;
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

  private async fail(cause: unknown): Promise<never> {
    const failure = cause instanceof Error ? cause : new Error(String(cause));
    this.error = failure;
    this.state = 'error';
    this.publish();
    throw failure;
  }

  private reset() {
    this.state = 'idle'; this.error = null; this.backend = null; this.turnNo = 0; this.interviewState = null; this.reportStatus = null;
    this.transcripts.length = 0; this.seenTranscriptIds.clear(); this.endRequested = false; this.closingRequested = false; this.configured = false; this.openingRequested = false; this.endPromise = null;
  }

  private publish() { const snapshot = this.getSnapshot(); this.listeners.forEach((listener) => listener(snapshot)); }
}
