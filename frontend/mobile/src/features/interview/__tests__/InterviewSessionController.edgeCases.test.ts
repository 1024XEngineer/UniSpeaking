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
  return {
    controller: new InterviewSessionController({ recorder, transport, sessionApi, sessionSocket, createEventId: () => 'event-1' } as any, { sceneId: 'scene', voice: 'voice', model: 'model' }),
    recorder, transport, sessionApi, sessionSocket,
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
});
