import type { RealtimeError, RealtimeState } from './types';

export type RealtimeStateEvent =
  | { type: 'START' }
  | { type: 'PERMISSION_GRANTED' }
  | { type: 'OFFER_CREATED' }
  | { type: 'ANSWER_APPLIED' }
  | { type: 'CHANNEL_OPEN' }
  | { type: 'USER_SPEECH_STARTED' }
  | { type: 'USER_SPEECH_STOPPED' }
  | { type: 'ASSISTANT_SPEECH_STARTED' }
  | { type: 'ASSISTANT_SPEECH_STOPPED' }
  | { type: 'PAUSE' }
  | { type: 'RESUME' }
  | { type: 'STOP' }
  | { type: 'ENDED' }
  | { type: 'RESET' }
  | { type: 'FAIL'; error: RealtimeError };

const transitions: Partial<
  Record<RealtimeState, Partial<Record<RealtimeStateEvent['type'], RealtimeState>>>
> = {
  idle: { START: 'requesting_permission' },
  requesting_permission: {
    PERMISSION_GRANTED: 'creating_offer',
    STOP: 'ending',
  },
  creating_offer: { OFFER_CREATED: 'exchanging_sdp', STOP: 'ending' },
  exchanging_sdp: { ANSWER_APPLIED: 'connecting', STOP: 'ending' },
  connecting: { CHANNEL_OPEN: 'ready', STOP: 'ending' },
  ready: {
    USER_SPEECH_STARTED: 'user_speaking',
    ASSISTANT_SPEECH_STARTED: 'assistant_speaking',
    PAUSE: 'paused',
    STOP: 'ending',
  },
  user_speaking: {
    USER_SPEECH_STOPPED: 'ready',
    ASSISTANT_SPEECH_STARTED: 'assistant_speaking',
    PAUSE: 'paused',
    STOP: 'ending',
  },
  assistant_speaking: {
    USER_SPEECH_STARTED: 'user_speaking',
    ASSISTANT_SPEECH_STOPPED: 'ready',
    PAUSE: 'paused',
    STOP: 'ending',
  },
  paused: { RESUME: 'ready', STOP: 'ending' },
  ending: { ENDED: 'ended' },
  ended: { RESET: 'idle' },
  error: { RESET: 'idle', STOP: 'ending' },
};

export class RealtimeStateMachine {
  private currentError: RealtimeError | null = null;

  constructor(private currentState: RealtimeState = 'idle') {}

  static ready() {
    return new RealtimeStateMachine('ready');
  }

  get state() {
    return this.currentState;
  }

  get error() {
    return this.currentError;
  }

  dispatch(event: RealtimeStateEvent): RealtimeState {
    if (this.currentState === 'ended' && event.type !== 'RESET') {
      return this.currentState;
    }
    if (event.type === 'FAIL') {
      this.currentError = event.error;
      this.currentState = 'error';
      return this.currentState;
    }

    const nextState = transitions[this.currentState]?.[event.type];
    if (!nextState) {
      throw new Error(`INVALID_TRANSITION:${this.currentState}:${event.type}`);
    }
    this.currentState = nextState;
    if (event.type === 'RESET' || event.type === 'START') {
      this.currentError = null;
    }
    return this.currentState;
  }
}
