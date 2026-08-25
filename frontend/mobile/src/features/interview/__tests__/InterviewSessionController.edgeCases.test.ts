import { InterviewSessionController } from '../InterviewSessionController';

function fixture() {
  let listener: ((event: any) => void) | null = null;
  const transport = {
    subscribe: jest.fn((next: (event: any) => void) => { listener = next; return () => { listener = null; }; }),
    prepare: jest.fn(async () => undefined),
    createOffer: jest.fn(async () => 'offer'),
    applyAnswer: jest.fn(async () => undefined),
    waitForDataChannel: jest.fn(async () => undefined),
    sendProviderEvent: jest.fn(),
    setAudioEnabled: jest.fn(),
    close: jest.fn(),
    emit: (event: any) => listener?.(event),
  };
  const recorder = {
    start: jest.fn(async () => undefined), setInputEnabled: jest.fn(),
    speechStarted: jest.fn(), speechStopped: jest.fn(),
    takeTurn: jest.fn(async () => ({ uri: 'turn.wav', durationMs: 500 })),
    discard: jest.fn(), appendAssistantAudio: jest.fn(), finishAssistantAudio: jest.fn(),
    waitForAssistantAudioDrain: jest.fn(async () => undefined),
    saveSessionRecording: jest.fn(() => 'file:///full.wav'), close: jest.fn(async () => undefined),
  };
  const sessionApi = {
    startSession: jest.fn(async () => ({ sessionId: 'session-1', answerSdp: 'answer', voiceId: 'voice', systemPrompt: 'prompt' })),
    submitTurn: jest.fn(async () => ({ state: { shouldEnd: false, completedTopicCount: 0, coveredTopicCount: 0, currentTopic: 'topic', controlInstruction: null }, reportStatus: 'PENDING' })),
    end: jest.fn(async () => ({ sessionId: 'session-1', reportStatus: 'PROCESSING' })),
  };
  const sessionSocket = { connect: jest.fn(async () => undefined), bindProviderSession: jest.fn(async () => undefined), persistMessage: jest.fn(async () => undefined), close: jest.fn() };
  const dependencies = { recorder, transport, sessionApi, sessionSocket, createEventId: () => 'event-1' } as any;
  return {
    controller: new InterviewSessionController(dependencies, { sceneId: 'scene', voice: 'voice', model: 'model' }),
    recorder, transport, sessionApi, sessionSocket, dependencies,
  };
}

async function provider(test: ReturnType<typeof fixture>, event: unknown) {
  await test.controller.handleProviderMessage(JSON.stringify(event));
}

async function activate(test: ReturnType<typeof fixture>) {
  await test.controller.start();
  await provider(test, { type: 'session.created' });
  await provider(test, { type: 'session.updated' });
  await provider(test, { type: 'response.done', response: { status: 'completed' } });
}

describe('InterviewSessionController edge cases', () => {
  it('keeps the same turn open and requests a repeat when captured audio is too short', async () => {
    const test = fixture();
    test.recorder.takeTurn.mockResolvedValueOnce({ uri: 'short.wav', durationMs: 120 });
    await activate(test);

    await provider(test, { type: 'input_audio_buffer.speech_started', item_id: 'short-audio' });
    await provider(test, { type: 'input_audio_buffer.speech_stopped', item_id: 'short-audio' });
    await provider(test, { type: 'conversation.item.input_audio_transcription.completed', item_id: 'short-audio', transcript: 'A complete answer.' });

    expect(test.sessionApi.submitTurn).not.toHaveBeenCalled();
    expect(test.controller.getSnapshot()).toEqual(expect.objectContaining({ turnNo: 0, muted: false }));
    expect(test.transport.sendProviderEvent).toHaveBeenLastCalledWith(expect.objectContaining({
      type: 'response.create', response: expect.objectContaining({ instructions: expect.stringContaining('not captured reliably') }),
    }));
  });

  it('persists interviewer text and cancels a response when the candidate barges in', async () => {
    const test = fixture();
    await activate(test);

    await provider(test, { type: 'response.text.done', item_id: 'assistant-1', text: 'Tell me about yourself.' });
    await provider(test, { type: 'input_audio_buffer.speech_started', item_id: 'candidate-1' });
    await provider(test, { type: 'input_audio_buffer.speech_stopped', item_id: 'candidate-1' });

    expect(test.controller.getSnapshot()).toEqual(expect.objectContaining({ currentQuestion: 'Tell me about yourself.' }));
    expect(test.sessionSocket.persistMessage).toHaveBeenCalledWith({ owner: 0, content: 'Tell me about yourself.', providerMessageId: 'assistant-1' });
    expect(test.recorder.speechStarted).toHaveBeenCalledTimes(1);
    expect(test.recorder.speechStopped).toHaveBeenCalledTimes(1);
    expect(test.transport.sendProviderEvent).toHaveBeenLastCalledWith(expect.objectContaining({ type: 'response.cancel' }));
  });

  it('cleans native resources when the backend omits required realtime parameters', async () => {
    const test = fixture();
    test.sessionApi.startSession.mockResolvedValueOnce({ sessionId: 'session-1', answerSdp: '', voiceId: 'voice', systemPrompt: '' });

    await expect(test.controller.start()).rejects.toThrow('面试实时会话参数不完整');

    expect(test.recorder.close).toHaveBeenCalledTimes(1);
    expect(test.transport.close).toHaveBeenCalledTimes(1);
    expect(test.controller.getSnapshot()).toEqual(expect.objectContaining({ state: 'error', error: expect.any(Error) }));
  });

  it('publishes snapshots, supports unsubscribe, toggles mute and reuses an active session', async () => {
    const test = fixture();
    const listener = jest.fn();
    const unsubscribe = test.controller.subscribe(listener);
    expect(listener).toHaveBeenCalledWith(expect.objectContaining({ state: 'idle', status: 'idle' }));

    await test.controller.start();
    await provider(test, { type: 'session.created' });
    await provider(test, { type: 'session.updated' });
    test.controller.setMuted(true);
    test.controller.setMuted(false);
    await expect(test.controller.start()).resolves.toEqual({ sessionId: 'session-1' });
    unsubscribe();
    const count = listener.mock.calls.length;
    test.controller.setMuted(true);

    expect(listener).toHaveBeenCalled();
    expect(listener).toHaveBeenCalledWith(expect.objectContaining({ userMuted: true }));
    expect(listener).toHaveBeenCalledTimes(count);
    expect(test.sessionApi.startSession).toHaveBeenCalledTimes(1);
  });

  it('ignores malformed, unknown and blank provider messages and configures a session once', async () => {
    const test = fixture();
    test.sessionApi.startSession.mockResolvedValueOnce({
      sessionId: 'session-1', answerSdp: 'answer', voiceId: '', systemPrompt: 'prompt',
    });
    await test.controller.handleProviderMessage('{broken');
    await provider(test, { type: 'unknown.event' });
    await test.controller.start();
    await provider(test, { type: 'session.created' });
    await provider(test, { type: 'session.created', session: { id: 'provider-late' } });
    await provider(test, { type: 'session.updated' });
    await provider(test, { type: 'session.updated' });
    await provider(test, { type: 'response.text.done', text: '   ' });
    await provider(test, { type: 'conversation.item.input_audio_transcription.completed', transcript: '   ' });

    const updates = test.transport.sendProviderEvent.mock.calls.filter(([event]: any[]) => event.type === 'session.update');
    const openings = test.transport.sendProviderEvent.mock.calls.filter(([event]: any[]) => event.type === 'response.create');
    expect(updates).toHaveLength(1);
    expect(updates[0][0].session.voice).toBe('voice');
    expect(openings).toHaveLength(1);
    expect(test.sessionSocket.bindProviderSession).not.toHaveBeenCalled();
  });

  it('queues the next backend instruction behind an active provider response', async () => {
    const test = fixture();
    test.sessionApi.submitTurn.mockResolvedValueOnce({
      state: {
        shouldEnd: false,
        completedTopicCount: 1,
        coveredTopicCount: 2,
        currentTopic: 'next',
        controlInstruction: 'Ask the next question',
      },
      reportStatus: 'PENDING',
    } as any);
    await test.controller.start();
    await provider(test, { type: 'session.created' });
    await provider(test, { type: 'session.updated' });
    await provider(test, { type: 'response.created' });
    await provider(test, { type: 'response.audio.delta', delta: 'AQI=' });
    await provider(test, {
      type: 'conversation.item.input_audio_transcription.completed',
      transcript: 'This is a complete answer.',
    });

    expect(test.recorder.appendAssistantAudio).toHaveBeenCalledWith('AQI=');
    expect(test.transport.sendProviderEvent).toHaveBeenCalledWith(expect.objectContaining({ type: 'response.cancel' }));
    await provider(test, { type: 'response.done', response: { status: 'cancelled' } });
    expect(test.transport.sendProviderEvent).toHaveBeenLastCalledWith(expect.objectContaining({
      type: 'response.create',
      response: expect.objectContaining({ instructions: 'Ask the next question' }),
    }));
  });

  it('ends safely from idle, returns null when already ended, then resets for a new start', async () => {
    const test = fixture();
    await expect(test.controller.end()).resolves.toBeNull();
    expect(test.controller.getSnapshot().state).toBe('ended');
    await expect(test.controller.end()).resolves.toBeNull();
    await expect(test.controller.start()).resolves.toEqual({ sessionId: 'session-1' });
    expect(test.recorder.start).toHaveBeenCalledTimes(1);
    expect(test.controller.getSnapshot()).toEqual(expect.objectContaining({
      state: 'starting', error: null, turnNo: 0, transcripts: [],
    }));
  });

  it('preserves a startup error when backend cleanup also fails', async () => {
    const test = fixture();
    test.sessionSocket.connect.mockRejectedValueOnce('socket failed');
    test.sessionApi.end.mockRejectedValueOnce(new Error('cleanup failed'));

    await expect(test.controller.start()).rejects.toThrow('socket failed');
    expect(test.sessionApi.end).toHaveBeenCalledWith('session-1');
    expect(test.controller.getSnapshot()).toEqual(expect.objectContaining({
      state: 'error', error: expect.objectContaining({ message: 'socket failed' }),
    }));
  });

  it('keeps provider failures visible when backend end fails and allows explicit cleanup', async () => {
    const test = fixture();
    test.sessionApi.end
      .mockRejectedValueOnce(new Error('end failed'))
      .mockResolvedValueOnce({ sessionId: 'session-1', reportStatus: 'PROCESSING' });
    await test.controller.start();
    test.transport.emit({ type: 'provider.message', data: JSON.stringify({ type: 'error', message: 'provider failed' }) });
    for (let attempt = 0; attempt < 20 && test.controller.getSnapshot().state !== 'error'; attempt += 1) {
      await Promise.resolve();
    }

    expect(test.controller.getSnapshot()).toEqual(expect.objectContaining({
      state: 'error', error: expect.objectContaining({ message: 'provider failed' }),
    }));
    await test.controller.end();
    expect(test.controller.getSnapshot().state).toBe('ended');
  });

  it('covers direct provider guards, duplicate work, and successful failure cleanup', async () => {
    const test = fixture();
    await test.controller.start();
    const internal = test.controller as any;
    internal.responseAwaitingInterviewState = true;
    await internal.applyEvent({ type: 'assistant.response.started' });
    expect(test.transport.sendProviderEvent).toHaveBeenCalledWith(expect.objectContaining({ type: 'response.cancel' }));
    internal.closingRequested = true;
    await internal.applyEvent({ type: 'user.speech.started' });
    await internal.applyEvent({ type: 'unknown.event' });

    const operation = jest.fn(async () => undefined);
    await internal.processTranscriptOnce(0, { itemId: 'duplicate' }, operation);
    await internal.processTranscriptOnce(0, { itemId: 'duplicate' }, operation);
    expect(operation).toHaveBeenCalledTimes(1);

    internal.state = 'starting';
    internal.backend = { sessionId: 'session-1' };
    await internal.handleStartFailure('startup failed');
    expect(test.sessionApi.end).toHaveBeenCalledWith('session-1');
    expect(test.controller.getSnapshot()).toEqual(expect.objectContaining({
      state: 'error', reportStatus: 'PROCESSING', error: expect.objectContaining({ message: 'startup failed' }),
    }));

    internal.state = 'active';
    internal.endRequested = false;
    await internal.handleFailure('transport failed');
    expect(test.controller.getSnapshot().state).toBe('ended');
  });
});
