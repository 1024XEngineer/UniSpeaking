export type RealtimeState =
  | 'idle'
  | 'requesting_permission'
  | 'creating_offer'
  | 'exchanging_sdp'
  | 'connecting'
  | 'ready'
  | 'user_speaking'
  | 'assistant_speaking'
  | 'paused'
  | 'ending'
  | 'ended'
  | 'error';

export type RealtimeErrorCode =
  | 'MICROPHONE_DENIED'
  | 'MEDIA_PREPARATION_FAILED'
  | 'OFFER_CREATION_FAILED'
  | 'SDP_EXCHANGE_FAILED'
  | 'SDP_EXCHANGE_TIMEOUT'
  | 'ANSWER_APPLY_FAILED'
  | 'ICE_FAILED'
  | 'PEER_CONNECTION_FAILED'
  | 'DATA_CHANNEL_FAILED'
  | 'PROVIDER_ERROR'
  | 'SESSION_SOCKET_FAILED'
  | 'UNKNOWN';

export type RealtimeError = Readonly<{
  code: RealtimeErrorCode;
  message: string;
  retryable: boolean;
}>;

export type RealtimeDomainEvent =
  | { type: 'session.created'; providerSessionId?: string }
  | { type: 'session.updated' }
  | { type: 'user.speech.started' }
  | { type: 'user.speech.stopped' }
  | { type: 'user.transcript.delta'; itemId?: string; text: string }
  | { type: 'user.transcript.preview'; itemId?: string; text: string }
  | { type: 'user.transcript.completed'; itemId?: string; text: string }
  | { type: 'assistant.response.started' }
  | { type: 'assistant.audio.delta'; audio: string }
  | { type: 'assistant.transcript.delta'; text: string }
  | { type: 'assistant.transcript.completed'; itemId?: string; text: string }
  | { type: 'assistant.response.completed'; cancelled: boolean }
  | { type: 'provider.error'; code?: string; message: string };
