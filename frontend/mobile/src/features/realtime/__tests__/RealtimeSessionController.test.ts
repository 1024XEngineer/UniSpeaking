import {
  RealtimeSessionController,
  type RealtimeSessionDependencies,
  type RealtimeTransportEvent,
} from '../RealtimeSessionController';

function createDependencies(): RealtimeSessionDependencies & {
  transport: RealtimeSessionDependencies['transport'] & {
    emit(event: RealtimeTransportEvent): void;
    prepare: jest.Mock;
    createOffer: jest.Mock;
    applyAnswer: jest.Mock;
    waitForDataChannel: jest.Mock;
    sendProviderEvent: jest.Mock;
    setAudioEnabled: jest.Mock;
    close: jest.Mock;
  };
  sessionApi: RealtimeSessionDependencies['sessionApi'] & {
    start: jest.Mock;
  };
  sessionSocket: RealtimeSessionDependencies['sessionSocket'] & {
    connect: jest.Mock;
    persistMessage: jest.Mock;
    end: jest.Mock;
    close: jest.Mock;
  };
} {
  let listener: ((event: RealtimeTransportEvent) => void) | null = null;
  const transport = {
    subscribe: jest.fn((nextListener: (event: RealtimeTransportEvent) => void) => {
      listener = nextListener;
      return () => {
        listener = null;
      };
    }),
    prepare: jest.fn(async () => undefined),
    createOffer: jest.fn(async () => 'offer-sdp'),
    applyAnswer: jest.fn(async () => undefined),
    waitForDataChannel: jest.fn(async () => undefined),
    sendProviderEvent: jest.fn(),
    setAudioEnabled: jest.fn(),
    close: jest.fn(),
    emit(event: RealtimeTransportEvent) {
      listener?.(event);
    },
  };
  return {
    transport,
    sessionApi: {
      start: jest.fn(async () => ({
        sessionId: 'session-1',
        answerSdp: 'answer-sdp',
        voiceId: 'Harvey',
        systemPrompt: 'You are the configured UniSpeaking teacher.',
      })),
    },
    sessionSocket: {
      connect: jest.fn(async () => undefined),
      persistMessage: jest.fn(async () => undefined),
      end: jest.fn(async () => undefined),
      close: jest.fn(),
    },
    now: () => new Date('2026-08-05T06:00:00.000Z'),
    createEventId: () => 'event-1',
  };
}

describe('RealtimeSessionController', () => {
  it('exchanges SDP through Java and waits for provider configuration before listening', async () => {
    const dependencies = createDependencies();
    const controller = new RealtimeSessionController(dependencies, {
      mode: 'free_chat',
      voice: 'Harvey',
      model: 'qwen3.5-omni-flash-realtime',
      speechSpeed: 'NATURAL',
    });

    await controller.start();

    expect(dependencies.sessionApi.start).toHaveBeenCalledWith({
      sceneId: null,
      offerSdp: 'offer-sdp',
      provider: 'QWEN',
      model: 'qwen3.5-omni-flash-realtime',
      voice: 'Harvey',
      translationEnabled: true,
    });
    expect(dependencies.transport.applyAnswer).toHaveBeenCalledWith('answer-sdp');
    expect(dependencies.sessionSocket.connect).toHaveBeenCalledWith('session-1');
    expect(controller.getSnapshot().state).toBe('connecting');

    await controller.handleProviderMessage(JSON.stringify({ type: 'session.created' }));
    expect(dependencies.transport.sendProviderEvent).toHaveBeenCalledWith(
      expect.objectContaining({
        type: 'session.update',
        session: expect.objectContaining({
          voice: 'Harvey',
          instructions: expect.stringContaining('configured UniSpeaking teacher'),
        }),
      }),
    );

    await controller.handleProviderMessage(JSON.stringify({ type: 'session.updated' }));
    expect(controller.getSnapshot().state).toBe('ready');
    expect(dependencies.transport.setAudioEnabled).toHaveBeenLastCalledWith(true);
    expect(dependencies.transport.sendProviderEvent).toHaveBeenCalledWith(
      expect.objectContaining({ type: 'response.create' }),
    );
  });

  it('publishes and persists completed user and assistant transcripts', async () => {
    const dependencies = createDependencies();
    const controller = new RealtimeSessionController(dependencies, {
      mode: 'free_chat',
      voice: 'Harvey',
      model: 'qwen3.5-omni-flash-realtime',
      speechSpeed: 'NATURAL',
    });
    await controller.start();

    await controller.handleProviderMessage(
      JSON.stringify({
        type: 'conversation.item.input_audio_transcription.completed',
        item_id: 'user-1',
        transcript: 'I would like a latte.',
      }),
    );
    await controller.handleProviderMessage(
      JSON.stringify({
        type: 'response.audio_transcript.done',
        item_id: 'assistant-1',
        transcript: 'Would you like oat milk?',
      }),
    );

    expect(controller.getSnapshot()).toEqual(
      expect.objectContaining({
        userTranscript: 'I would like a latte.',
        assistantTranscript: 'Would you like oat milk?',
      }),
    );
    expect(dependencies.sessionSocket.persistMessage).toHaveBeenNthCalledWith(1, {
      owner: 1,
      content: 'I would like a latte.',
      providerMessageId: 'user-1',
    });
    expect(dependencies.sessionSocket.persistMessage).toHaveBeenNthCalledWith(2, {
      owner: 0,
      content: 'Would you like oat milk?',
      providerMessageId: 'assistant-1',
    });
  });

  it('publishes live learner subtitles and clears the previous AI turn when speech starts', async () => {
    const dependencies = createDependencies();
    const controller = new RealtimeSessionController(dependencies, {
      mode: 'free_chat',
      voice: 'Harvey',
      model: 'qwen3.5-omni-flash-realtime',
      speechSpeed: 'NATURAL',
    });
    await controller.start();
    await controller.handleProviderMessage(JSON.stringify({ type: 'session.updated' }));
    await controller.handleProviderMessage(JSON.stringify({ type: 'response.created' }));
    await controller.handleProviderMessage(
      JSON.stringify({
        type: 'response.audio_transcript.done',
        item_id: 'assistant-previous',
        transcript: 'What would you like to order?',
      }),
    );
    await controller.handleProviderMessage(
      JSON.stringify({ type: 'response.done', response: { status: 'completed' } }),
    );

    await controller.handleProviderMessage(
      JSON.stringify({ type: 'input_audio_buffer.speech_started' }),
    );
    expect(controller.getSnapshot()).toEqual(
      expect.objectContaining({
        state: 'user_speaking',
        userTranscript: '',
        assistantTranscript: '',
      }),
    );

    await controller.handleProviderMessage(
      JSON.stringify({
        type: 'conversation.item.input_audio_transcription.delta',
        item_id: 'user-live',
        delta: 'I would ',
      }),
    );
    expect(controller.getSnapshot().userTranscript).toBe('I would ');

    await controller.handleProviderMessage(
      JSON.stringify({
        type: 'conversation.item.input_audio_transcription.text',
        item_id: 'user-live',
        text: 'I would like',
        stash: ' a latte',
      }),
    );
    expect(controller.getSnapshot().userTranscript).toBe('I would like a latte');
    expect(dependencies.sessionSocket.persistMessage).toHaveBeenCalledTimes(1);
  });

  it('mutes the local track and sends a response cancellation when interrupted', async () => {
    const dependencies = createDependencies();
    const controller = new RealtimeSessionController(dependencies, {
      mode: 'free_chat',
      voice: 'Harvey',
      model: 'qwen3.5-omni-flash-realtime',
      speechSpeed: 'NATURAL',
    });
    await controller.start();
    await controller.handleProviderMessage(JSON.stringify({ type: 'session.updated' }));
    controller.setMuted(true);
    await controller.handleProviderMessage(JSON.stringify({ type: 'response.created' }));
    controller.interrupt();

    expect(dependencies.transport.setAudioEnabled).toHaveBeenLastCalledWith(false);
    expect(controller.getSnapshot().muted).toBe(true);
    expect(dependencies.transport.sendProviderEvent).toHaveBeenCalledWith({
      event_id: 'event-1',
      type: 'response.cancel',
    });
  });

  it('ends once and cleans all native resources even when called twice', async () => {
    const dependencies = createDependencies();
    const controller = new RealtimeSessionController(dependencies, {
      mode: 'free_chat',
      voice: 'Harvey',
      model: 'qwen3.5-omni-flash-realtime',
      speechSpeed: 'NATURAL',
    });
    await controller.start();

    await Promise.all([controller.end(), controller.end()]);

    expect(dependencies.sessionSocket.end).toHaveBeenCalledWith(
      '2026-08-05T06:00:00.000Z',
    );
    expect(dependencies.sessionSocket.end).toHaveBeenCalledTimes(1);
    expect(dependencies.transport.close).toHaveBeenCalledTimes(1);
    expect(dependencies.sessionSocket.close).toHaveBeenCalledTimes(1);
    expect(controller.getSnapshot().state).toBe('ended');
  });

  it('classifies offer creation failure and closes partial resources', async () => {
    const dependencies = createDependencies();
    dependencies.transport.createOffer.mockRejectedValue(new Error('offer failed'));
    const controller = new RealtimeSessionController(dependencies, {
      mode: 'free_chat',
      voice: 'Harvey',
      model: 'qwen3.5-omni-flash-realtime',
      speechSpeed: 'NATURAL',
    });

    await expect(controller.start()).rejects.toThrow('offer failed');

    expect(dependencies.transport.close).toHaveBeenCalledTimes(1);
    expect(controller.getSnapshot()).toEqual(
      expect.objectContaining({
        state: 'error',
        error: expect.objectContaining({ code: 'OFFER_CREATION_FAILED' }),
      }),
    );
  });

  it('coordinates each scene transcript and applies the backend control instruction once', async () => {
    const dependencies = createDependencies();
    const sceneDialogue = {
      advanceState: jest.fn(async () => ({
        sceneId: 'scene-1',
        sessionId: 'session-1',
        stage: 'CORE_TASK',
        effectiveUserTurns: 1,
        maximumUserTurns: 6,
        outcomes: [],
        completed: false,
        completionReason: null,
        controlInstruction: 'Ask the learner to confirm the final price.',
        warning: null,
      })),
      evaluateTurn: jest.fn(async () => ({ score: 86 })),
      complete: jest.fn(async () => null),
    };
    dependencies.sceneDialogue = sceneDialogue;
    const controller = new RealtimeSessionController(dependencies, {
      mode: 'scene',
      sceneId: 'scene-1',
      voice: 'Harvey',
      model: 'qwen3.5-omni-flash-realtime',
      speechSpeed: 'NATURAL',
    });
    await controller.start();
    await controller.handleProviderMessage(JSON.stringify({ type: 'session.updated' }));

    await controller.handleProviderMessage(
      JSON.stringify({
        type: 'conversation.item.input_audio_transcription.completed',
        item_id: 'user-turn-1',
        transcript: 'How much is the total?',
      }),
    );

    expect(sceneDialogue.advanceState).toHaveBeenCalledWith(
      'session-1',
      1,
      'How much is the total?',
    );
    expect(sceneDialogue.evaluateTurn).toHaveBeenCalledWith(
      'session-1',
      1,
      'How much is the total?',
    );
    expect(dependencies.transport.sendProviderEvent).toHaveBeenCalledWith(
      expect.objectContaining({
        type: 'session.update',
        session: expect.objectContaining({
          instructions: expect.stringContaining('confirm the final price'),
        }),
      }),
    );
    expect(dependencies.transport.sendProviderEvent).toHaveBeenCalledWith(
      expect.objectContaining({ type: 'response.create' }),
    );
    expect(controller.getSnapshot().sceneState).toEqual(
      expect.objectContaining({ effectiveUserTurns: 1, completed: false }),
    );
  });
});
