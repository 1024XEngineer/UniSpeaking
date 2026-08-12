import { InterviewSessionController } from '../InterviewSessionController';

function fixture() {
  let listener: ((event: any) => void) | null = null;
  const calls: string[] = [];
  const transport = {
    subscribe: jest.fn((next: (event: any) => void) => { listener = next; return () => { listener = null; }; }),
    prepare: jest.fn(async () => { calls.push('webrtc.prepare'); }),
    createOffer: jest.fn(async () => { calls.push('webrtc.offer'); return 'offer'; }),
    applyAnswer: jest.fn(async () => { calls.push('webrtc.answer'); }),
    waitForDataChannel: jest.fn(async () => { calls.push('webrtc.channel'); }),
    sendProviderEvent: jest.fn((event: any) => { calls.push(`provider:${event.type}`); }),
    setAudioEnabled: jest.fn(),
    close: jest.fn(async () => { calls.push('webrtc.close'); }),
    emit: (event: any) => listener?.(event),
  };
  const recorder = {
    start: jest.fn(async () => { calls.push('recorder.start'); }),
    setInputEnabled: jest.fn(), speechStarted: jest.fn(), speechStopped: jest.fn(),
    takeTurn: jest.fn(async (turnNo: number) => ({ uri: `turn-${turnNo}.wav`, name: `turn-${turnNo}.wav`, size: 16_044, durationMs: 500 })),
    appendAssistantAudio: jest.fn(), finishAssistantAudio: jest.fn(), saveSessionRecording: jest.fn(() => 'file:///full.wav'),
    discard: jest.fn(), close: jest.fn(async () => { calls.push('recorder.close'); }),
  };
  const sessionApi = {
    startSession: jest.fn(async () => ({ sessionId: 'session-1', answerSdp: 'answer', voiceId: 'voice', systemPrompt: 'prompt' } as any)),
    submitTurn: jest.fn(async () => ({ state: { shouldEnd: true, completedTopicCount: 1, coveredTopicCount: 1, currentTopic: 'done', controlInstruction: 'next' }, reportStatus: 'PROCESSING' as const })),
    end: jest.fn(async () => ({ sessionId: 'session-1', reportStatus: 'PROCESSING' as const })),
  };
  const sessionSocket = {
    connect: jest.fn(async () => undefined),
    bindProviderSession: jest.fn(async () => undefined),
    persistMessage: jest.fn(async () => undefined),
    close: jest.fn(),
  };
  const controller = new InterviewSessionController(
    { recorder, transport, sessionApi, sessionSocket, createEventId: () => 'event-1' },
    { sceneId: 'scene-1', voice: 'voice', model: 'qwen3.5-omni-flash-realtime' },
  );
  return { controller, transport, recorder, sessionApi, sessionSocket, calls };
}

async function provider(test: ReturnType<typeof fixture>, event: unknown) {
  await test.controller.handleProviderMessage(JSON.stringify(event));
}

describe('InterviewSessionController', () => {
  it('starts continuous recording before WebRTC and opens the mic only after opening response.done', async () => {
    const test = fixture();
    await test.controller.start();
    expect(test.calls.slice(0, 4)).toEqual(['recorder.start', 'webrtc.prepare', 'webrtc.offer', 'webrtc.answer']);
    await provider(test, { type: 'session.created', session: { id: 'provider-1' } });
    await provider(test, { type: 'session.updated' });
    expect(test.transport.sendProviderEvent).toHaveBeenLastCalledWith(expect.objectContaining({ type: 'response.create' }));
    expect(test.sessionSocket.bindProviderSession).toHaveBeenCalledWith('provider-1');
    expect(test.transport.setAudioEnabled).toHaveBeenLastCalledWith(false);
    await provider(test, { type: 'response.done', response: { status: 'completed' } });
    expect(test.transport.setAudioEnabled).toHaveBeenLastCalledWith(true);
    expect(test.controller.getSnapshot().state).toBe('active');
  });

  it('uses a pause-tolerant VAD configuration for interview answers', async () => {
    const test = fixture();
    await test.controller.start();
    await provider(test, { type: 'session.created' });
    const update = test.transport.sendProviderEvent.mock.calls.find(([event]) => event.type === 'session.update')?.[0];
    expect(update.session.turn_detection).toEqual(expect.objectContaining({
      silence_duration_ms: 3_000,
      threshold: 0.8,
      prefix_padding_ms: 1_000,
      interrupt_response: true,
      create_response: false,
    }));
  });

  it('persists ACK before submitting the turn WAV, and deduplicates a repeated provider item', async () => {
    const test = fixture();
    await test.controller.start();
    await provider(test, { type: 'session.created' });
    await provider(test, { type: 'session.updated' });
    await provider(test, { type: 'response.done', response: { status: 'completed' } });
    await provider(test, { type: 'input_audio_buffer.speech_started', item_id: 'item-1' });
    await provider(test, { type: 'input_audio_buffer.speech_stopped', item_id: 'item-1' });
    await provider(test, { type: 'conversation.item.input_audio_transcription.completed', item_id: 'item-1', transcript: 'answer' });
    await provider(test, { type: 'conversation.item.input_audio_transcription.completed', item_id: 'item-1', transcript: 'answer' });
    expect(test.sessionSocket.persistMessage).toHaveBeenCalledTimes(1);
    expect(test.sessionApi.submitTurn).toHaveBeenCalledWith('session-1', 1, 'answer', 'turn-1.wav');
    expect(test.recorder.discard).toHaveBeenCalledTimes(1);
  });

  it('does not advance the interview for a likely cut-off transcript fragment', async () => {
    const test = fixture();
    await test.controller.start();
    await provider(test, { type: 'session.created' });
    await provider(test, { type: 'session.updated' });
    await provider(test, { type: 'response.done', response: { status: 'completed' } });
    await provider(test, { type: 'input_audio_buffer.speech_started', item_id: 'short-1' });
    await provider(test, { type: 'input_audio_buffer.speech_stopped', item_id: 'short-1' });
    await provider(test, { type: 'conversation.item.input_audio_transcription.completed', item_id: 'short-1', transcript: 'I led' });
    expect(test.sessionSocket.persistMessage).not.toHaveBeenCalled();
    expect(test.sessionApi.submitTurn).not.toHaveBeenCalled();
    expect(test.transport.sendProviderEvent).toHaveBeenLastCalledWith(expect.objectContaining({
      type: 'response.create',
      response: expect.objectContaining({ instructions: expect.stringContaining('cut off') }),
    }));
  });

  it('mutes once shouldEnd is returned, sends one closing response, and ends after response.done', async () => {
    const test = fixture();
    await test.controller.start();
    await provider(test, { type: 'session.created' });
    await provider(test, { type: 'session.updated' });
    await provider(test, { type: 'response.done', response: { status: 'completed' } });
    await provider(test, { type: 'input_audio_buffer.speech_started', item_id: 'item-1' });
    await provider(test, { type: 'input_audio_buffer.speech_stopped', item_id: 'item-1' });
    await provider(test, { type: 'conversation.item.input_audio_transcription.completed', item_id: 'item-1', transcript: 'answer' });
    await provider(test, { type: 'response.done', response: { status: 'completed' } });
    expect(test.transport.setAudioEnabled).toHaveBeenLastCalledWith(false);
    expect(test.sessionApi.end).toHaveBeenCalledTimes(1);
    expect(test.controller.getSnapshot().state).toBe('ended');
  });

  it('makes end idempotent and cleans a failed start', async () => {
    const test = fixture();
    test.transport.createOffer.mockRejectedValue(new Error('offer failed'));
    await expect(test.controller.start()).rejects.toThrow('offer failed');
    expect(test.recorder.close).toHaveBeenCalledTimes(1);
    expect(test.transport.close).toHaveBeenCalledTimes(1);

    const second = fixture();
    await second.controller.start();
    await Promise.all([second.controller.end(), second.controller.end()]);
    expect(second.sessionApi.end).toHaveBeenCalledTimes(1);
    expect(second.recorder.close).toHaveBeenCalledTimes(1);
    expect(second.transport.close).toHaveBeenCalledTimes(1);
  });

  it('cancels an in-flight start without leaving a backend session active', async () => {
    const test = fixture();
    let resolveStart!: (value: any) => void;
    test.sessionApi.startSession.mockImplementationOnce(() => new Promise((resolve) => {
      resolveStart = resolve;
    }));

    const starting = test.controller.start();
    while (!resolveStart) await Promise.resolve();
    const ending = test.controller.end();
    resolveStart({ sessionId: 'session-1', answerSdp: 'answer', voiceId: 'voice', systemPrompt: 'prompt' });

    await expect(starting).rejects.toThrow('面试启动已取消');
    await ending;
    expect(test.sessionApi.end).toHaveBeenCalledWith('session-1');
    expect(test.transport.applyAnswer).not.toHaveBeenCalled();
    expect(test.controller.getSnapshot().state).toBe('ended');
  });

  it('ends the backend even when an in-flight turn fails after the user ends', async () => {
    const test = fixture();
    let rejectTurn!: (cause: Error) => void;
    test.sessionApi.submitTurn.mockImplementationOnce(() => new Promise((_, reject) => {
      rejectTurn = reject;
    }));
    await test.controller.start();
    await provider(test, { type: 'session.created' });
    await provider(test, { type: 'session.updated' });
    await provider(test, { type: 'response.done', response: { status: 'completed' } });
    await provider(test, { type: 'input_audio_buffer.speech_started', item_id: 'item-1' });
    await provider(test, { type: 'input_audio_buffer.speech_stopped', item_id: 'item-1' });

    const turn = test.controller.handleProviderMessage(JSON.stringify({
      type: 'conversation.item.input_audio_transcription.completed',
      item_id: 'item-1',
      transcript: 'answer',
    }));
    while (!rejectTurn) await Promise.resolve();
    const ending = test.controller.end();
    rejectTurn(new Error('turn failed'));

    await expect(turn).rejects.toThrow('turn failed');
    await ending;
    expect(test.sessionApi.end).toHaveBeenCalledTimes(1);
    expect(test.transport.sendProviderEvent).toHaveBeenCalledTimes(2);
    expect(test.controller.getSnapshot().state).toBe('ended');
  });

  it('allows a duplicate provider item to retry after persistence fails', async () => {
    const test = fixture();
    test.sessionSocket.persistMessage
      .mockRejectedValueOnce(new Error('ack failed'))
      .mockResolvedValueOnce(undefined);
    await test.controller.start();
    await provider(test, { type: 'session.created' });
    await provider(test, { type: 'session.updated' });
    await provider(test, { type: 'response.done', response: { status: 'completed' } });
    const event = {
      type: 'conversation.item.input_audio_transcription.completed',
      item_id: 'item-retry',
      transcript: 'answer',
    };

    await expect(test.controller.handleProviderMessage(JSON.stringify(event))).rejects.toThrow('ack failed');
    await test.controller.handleProviderMessage(JSON.stringify(event));

    expect(test.sessionSocket.persistMessage).toHaveBeenCalledTimes(2);
    expect(test.sessionApi.submitTurn).toHaveBeenCalledTimes(1);
  });

  it('ends the backend when the realtime provider fails', async () => {
    const test = fixture();
    await test.controller.start();
    await provider(test, { type: 'session.created' });
    test.transport.emit({ type: 'connection.failed', message: 'peer failed' });

    for (let attempt = 0; attempt < 10 && test.sessionApi.end.mock.calls.length === 0; attempt += 1) {
      await Promise.resolve();
    }
    expect(test.sessionApi.end).toHaveBeenCalledTimes(1);
    for (let attempt = 0; attempt < 10 && test.controller.getSnapshot().state !== 'ended'; attempt += 1) {
      await Promise.resolve();
    }
    expect(test.recorder.close).toHaveBeenCalledTimes(1);
    expect(test.controller.getSnapshot().state).toBe('ended');
    expect(test.controller.getSnapshot().error?.message).toBe('peer failed');
  });
});
