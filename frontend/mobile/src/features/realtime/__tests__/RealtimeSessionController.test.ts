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
      ieltsId: null,
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

  it('keeps completed subtitles in dialogue order while publishing the live learner turn', async () => {
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
        transcriptHistory: [
          expect.objectContaining({
            owner: 0,
            content: 'What would you like to order?',
          }),
        ],
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

    await controller.handleProviderMessage(
      JSON.stringify({
        type: 'conversation.item.input_audio_transcription.completed',
        item_id: 'user-live',
        transcript: 'I would like a latte.',
      }),
    );
    await controller.handleProviderMessage(JSON.stringify({ type: 'response.created' }));
    await controller.handleProviderMessage(
      JSON.stringify({
        type: 'response.audio_transcript.done',
        item_id: 'assistant-next',
        transcript: 'A latte is a great choice.',
      }),
    );

    expect(controller.getSnapshot().transcriptHistory.map(({ owner, content }) => ({ owner, content }))).toEqual([
      { owner: 0, content: 'What would you like to order?' },
      { owner: 1, content: 'I would like a latte.' },
      { owner: 0, content: 'A latte is a great choice.' },
    ]);
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
    const turnAudioCapture = {
      start: jest.fn(async () => undefined),
      stop: jest.fn(() => true),
      take: jest.fn(async () => 'file:///scene-turn.wav'),
    };
    dependencies.turnAudioCapture = turnAudioCapture;
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
    await controller.handleProviderMessage(JSON.stringify({ type: 'response.created' }));
    await controller.handleProviderMessage(
      JSON.stringify({ type: 'response.done', response: { status: 'completed' } }),
    );
    await controller.handleProviderMessage(
      JSON.stringify({ type: 'input_audio_buffer.speech_started' }),
    );
    await controller.handleProviderMessage(
      JSON.stringify({ type: 'input_audio_buffer.speech_stopped' }),
    );

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
    expect(turnAudioCapture.start).toHaveBeenCalledTimes(1);
    expect(turnAudioCapture.stop).toHaveBeenCalledTimes(1);
    expect(turnAudioCapture.take).toHaveBeenCalledTimes(1);
    expect(sceneDialogue.evaluateTurn).toHaveBeenCalledWith(
      'session-1',
      1,
      'How much is the total?',
      'file:///scene-turn.wav',
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

  it('queues a scene turn until the active assistant response completes', async () => {
    const dependencies = createDependencies();
    const sceneDialogue: NonNullable<RealtimeSessionDependencies['sceneDialogue']> = {
      advanceState: jest.fn(async () => ({
        sceneId: 'scene-1',
        sessionId: 'session-1',
        stage: 'CORE_TASK',
        effectiveUserTurns: 1,
        maximumUserTurns: 6,
        outcomes: [],
        completed: false,
        completionReason: null,
        controlInstruction: 'Continue from the restored scene state.',
        warning: null,
      })),
      evaluateTurn: jest.fn(async () => null),
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
    await controller.handleProviderMessage(JSON.stringify({ type: 'response.created' }));

    await controller.handleProviderMessage(
      JSON.stringify({
        type: 'conversation.item.input_audio_transcription.completed',
        item_id: 'repractice-turn-1',
        transcript: 'I would like to check in.',
      }),
    );

    expect(
      dependencies.transport.sendProviderEvent.mock.calls.filter(
        ([event]) => event.type === 'response.create',
      ),
    ).toHaveLength(1);
    expect(dependencies.transport.sendProviderEvent).not.toHaveBeenCalledWith(
      expect.objectContaining({
        type: 'session.update',
        session: expect.objectContaining({
          instructions: expect.stringContaining('restored scene state'),
        }),
      }),
    );

    await controller.handleProviderMessage(
      JSON.stringify({ type: 'response.done', response: { status: 'completed' } }),
    );

    expect(dependencies.transport.sendProviderEvent).toHaveBeenCalledWith(
      expect.objectContaining({
        type: 'session.update',
        session: expect.objectContaining({
          instructions: expect.stringContaining('restored scene state'),
        }),
      }),
    );
    expect(
      dependencies.transport.sendProviderEvent.mock.calls.filter(
        ([event]) => event.type === 'response.create',
      ),
    ).toHaveLength(2);
  });

  it('does not advance the scene state twice for a repeated provider transcript', async () => {
    const dependencies = createDependencies();
    const sceneDialogue: NonNullable<RealtimeSessionDependencies['sceneDialogue']> = {
      advanceState: jest.fn(async () => ({
        sceneId: 'scene-1',
        sessionId: 'session-1',
        stage: 'CORE_TASK',
        effectiveUserTurns: 1,
        maximumUserTurns: 6,
        outcomes: [],
        completed: false,
        completionReason: null,
        controlInstruction: 'Ask one follow-up question.',
        warning: null,
      })),
      evaluateTurn: jest.fn(async () => null),
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
    const transcript = JSON.stringify({
      type: 'conversation.item.input_audio_transcription.completed',
      item_id: 'same-turn',
      transcript: 'Here is my passport.',
    });

    await controller.handleProviderMessage(transcript);
    await controller.handleProviderMessage(transcript);

    expect(sceneDialogue.advanceState).toHaveBeenCalledTimes(1);
    expect(sceneDialogue.evaluateTurn).toHaveBeenCalledTimes(1);
    expect(dependencies.sessionSocket.persistMessage).toHaveBeenCalledTimes(1);
  });

  it('recovers from an active-response provider race without failing the session', async () => {
    const dependencies = createDependencies();
    const sceneDialogue: NonNullable<RealtimeSessionDependencies['sceneDialogue']> = {
      advanceState: jest.fn(async () => ({
        sceneId: 'scene-1',
        sessionId: 'session-1',
        stage: 'CORE_TASK',
        effectiveUserTurns: 1,
        maximumUserTurns: 6,
        outcomes: [],
        completed: false,
        completionReason: null,
        controlInstruction: 'Continue the dialogue.',
        warning: null,
      })),
      evaluateTurn: jest.fn(async () => null),
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
    await controller.handleProviderMessage(
      JSON.stringify({
        type: 'conversation.item.input_audio_transcription.completed',
        item_id: 'race-turn',
        transcript: 'Can I see the room?',
      }),
    );
    expect(
      dependencies.transport.sendProviderEvent.mock.calls.filter(
        ([event]) => event.type === 'response.create',
      ),
    ).toHaveLength(1);

    await controller.handleProviderMessage(
      JSON.stringify({
        type: 'error',
        error: { message: 'Conversation already has an active response' },
      }),
    );

    expect(controller.getSnapshot().state).not.toBe('error');
    expect(controller.getSnapshot().error).toBeNull();

    await controller.handleProviderMessage(
      JSON.stringify({ type: 'response.done', response: { status: 'completed' } }),
    );
    expect(
      dependencies.transport.sendProviderEvent.mock.calls.filter(
        ([event]) => event.type === 'response.create',
      ),
    ).toHaveLength(2);
  });

  it('waits for the final scene response before ending and exposing evaluation', async () => {
    const dependencies = createDependencies();
    const completion = {
      sceneId: 'scene-1',
      sessionId: 'session-1',
      stopTime: '2026-08-05T06:00:00.000Z',
      evaluation: {
        accuracyScore: 88,
        fluencyScore: 88,
        grammarScore: 88,
        vocabularyScore: 88,
        naturalnessScore: 88,
        finalScore: 88,
        summary: 'Completed successfully.',
        strengths: [],
        improvements: [],
      },
    };
    const sceneDialogue: NonNullable<RealtimeSessionDependencies['sceneDialogue']> = {
      advanceState: jest.fn(async () => ({
        sceneId: 'scene-1',
        sessionId: 'session-1',
        stage: 'COMPLETED',
        effectiveUserTurns: 3,
        maximumUserTurns: 6,
        outcomes: [],
        completed: true,
        completionReason: 'OUTCOME_REACHED',
        controlInstruction: 'Give one short closing sentence.',
        warning: null,
      })),
      evaluateTurn: jest.fn(async () => null),
      complete: jest.fn(async () => completion),
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
    await controller.handleProviderMessage(
      JSON.stringify({
        type: 'conversation.item.input_audio_transcription.completed',
        item_id: 'final-turn',
        transcript: 'Thank you, goodbye.',
      }),
    );

    expect(sceneDialogue.complete).not.toHaveBeenCalled();
    await controller.handleProviderMessage(JSON.stringify({ type: 'response.created' }));
    expect(sceneDialogue.complete).not.toHaveBeenCalled();

    await controller.handleProviderMessage(
      JSON.stringify({ type: 'response.done', response: { status: 'completed' } }),
    );

    expect(sceneDialogue.complete).toHaveBeenCalledTimes(1);
    expect(controller.getSnapshot()).toEqual(
      expect.objectContaining({ state: 'ended', completion }),
    );
  });

  it('coordinates each ielts transcript and applies the backend control instruction once', async () => {
    const dependencies = createDependencies();
    const ieltsDialogue: NonNullable<RealtimeSessionDependencies['ieltsDialogue']> = {
      advanceState: jest.fn(async () => ({
        sceneId: 'ielts-1',
        sessionId: 'session-1',
        part: 'PART_1' as const,
        openingCompleted: true,
        answeredQuestions: 1,
        totalQuestions: 4,
        completed: false,
        controlInstruction: 'Ask the next Part 1 question exactly as written.',
      })),
      evaluateTurn: jest.fn(async () => ({ score: 7 })),
      advancePart2State: jest.fn(async () => ({
        sceneId: 'ielts-1',
        sessionId: 'session-1',
        phase: 'LONG_TURN',
        completed: false,
        controlInstruction: 'Begin the long turn now.',
      })),
      getDialogueState: jest.fn(async () => ({
        sceneId: 'ielts-1',
        sessionId: 'session-1',
        part: 'PART_1' as const,
        openingCompleted: true,
        answeredQuestions: 0,
        totalQuestions: 4,
        completed: false,
        controlInstruction: 'Ask the first Part 1 question exactly as written.',
      })),
      getPart2State: jest.fn(),
    };
    dependencies.ieltsDialogue = ieltsDialogue;
    dependencies.sessionApi.start.mockResolvedValue({
      sessionId: 'session-1',
      answerSdp: 'answer-sdp',
      voiceId: 'Harvey',
      systemPrompt: 'You are an IELTS examiner.',
      currentStage: 'PART_1',
    });
    const controller = new RealtimeSessionController(dependencies, {
      mode: 'ielts',
      ieltsId: 'ielts-1',
      ieltsPart: 'PART_1',
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
        transcript: 'I live in Shanghai.',
      }),
    );

    expect(ieltsDialogue.advanceState).toHaveBeenCalledWith('session-1', 1, false);
    expect(ieltsDialogue.evaluateTurn).toHaveBeenCalledWith(
      'session-1',
      1,
      'I live in Shanghai.',
      null,
    );
    expect(dependencies.transport.sendProviderEvent).toHaveBeenCalledWith(
      expect.objectContaining({
        type: 'session.update',
        session: expect.objectContaining({
          instructions: expect.stringContaining('next Part 1 question'),
        }),
      }),
    );
    expect(dependencies.transport.sendProviderEvent).toHaveBeenCalledWith(
      expect.objectContaining({ type: 'response.create' }),
    );
    expect(controller.getSnapshot().ieltsDialogueState).toEqual(
      expect.objectContaining({ answeredQuestions: 1, completed: false }),
    );
  });

  it('advances part2 state through the public transition API', async () => {
    const dependencies = createDependencies();
    const ieltsDialogue: NonNullable<RealtimeSessionDependencies['ieltsDialogue']> = {
      advanceState: jest.fn(),
      evaluateTurn: jest.fn(),
      advancePart2State: jest.fn(async () => ({
        sceneId: 'ielts-1',
        sessionId: 'session-1',
        phase: 'LONG_TURN',
        completed: false,
        controlInstruction: 'Please begin speaking now.',
      })),
      getDialogueState: jest.fn(),
      getPart2State: jest.fn(async () => ({
        sceneId: 'ielts-1',
        sessionId: 'session-1',
        phase: 'PREPARATION',
        completed: false,
        controlInstruction: 'Prepare for Part 2.',
      })),
    };
    dependencies.ieltsDialogue = ieltsDialogue;
    dependencies.sessionApi.start.mockResolvedValue({
      sessionId: 'session-1',
      answerSdp: 'answer-sdp',
      voiceId: 'Harvey',
      systemPrompt: 'You are an IELTS examiner.',
      currentStage: 'PART_2',
    });
    const controller = new RealtimeSessionController(dependencies, {
      mode: 'ielts',
      ieltsId: 'ielts-1',
      ieltsPart: 'PART_2',
      voice: 'Harvey',
      model: 'qwen3.5-omni-flash-realtime',
      speechSpeed: 'NATURAL',
    });
    await controller.start();

    await controller.transitionPart2('PREPARATION_COMPLETE');

    expect(ieltsDialogue.advancePart2State).toHaveBeenCalledWith(
      'session-1',
      'PREPARATION_COMPLETE',
    );
    expect(controller.getSnapshot().ieltsPart2State).toEqual(
      expect.objectContaining({ phase: 'LONG_TURN' }),
    );
  });

  it('uploads Part 2 turn audio so the report can include pronunciation', async () => {
    const dependencies = createDependencies();
    const turnAudioCapture = {
      start: jest.fn(async () => undefined),
      stop: jest.fn(() => true),
      take: jest.fn(async () => 'file:///ielts-part2.wav'),
    };
    dependencies.turnAudioCapture = turnAudioCapture;
    const ieltsDialogue: NonNullable<RealtimeSessionDependencies['ieltsDialogue']> = {
      advanceState: jest.fn(),
      evaluateTurn: jest.fn(async () => ({ pronunciationScore: 82 })),
      advancePart2State: jest.fn(),
      getDialogueState: jest.fn(),
      getPart2State: jest.fn(async () => ({
        sceneId: 'ielts-1',
        sessionId: 'session-1',
        phase: 'LONG_TURN',
        completed: false,
        controlInstruction: 'Continue the long turn.',
      })),
    };
    dependencies.ieltsDialogue = ieltsDialogue;
    dependencies.sessionApi.start.mockResolvedValue({
      sessionId: 'session-1',
      answerSdp: 'answer-sdp',
      voiceId: 'Harvey',
      systemPrompt: 'You are an IELTS examiner.',
      currentStage: 'PART_2',
    });
    const controller = new RealtimeSessionController(dependencies, {
      mode: 'ielts',
      ieltsId: 'ielts-1',
      ieltsPart: 'PART_2',
      voice: 'Harvey',
      model: 'qwen3.5-omni-flash-realtime',
      speechSpeed: 'NATURAL',
    });
    await controller.start();
    await controller.handleProviderMessage(
      JSON.stringify({ type: 'input_audio_buffer.speech_started' }),
    );
    await controller.handleProviderMessage(
      JSON.stringify({ type: 'input_audio_buffer.speech_stopped' }),
    );

    await controller.handleProviderMessage(
      JSON.stringify({
        type: 'conversation.item.input_audio_transcription.completed',
        item_id: 'part2-answer-1',
        transcript: 'I would like to describe a memorable journey I took last year.',
      }),
    );

    expect(turnAudioCapture.take).toHaveBeenCalledTimes(1);
    expect(ieltsDialogue.evaluateTurn).toHaveBeenCalledWith(
      'session-1',
      1,
      'I would like to describe a memorable journey I took last year.',
      'file:///ielts-part2.wav',
    );
  });

  it('waits for the pending Part 2 pronunciation evaluation before ending', async () => {
    const dependencies = createDependencies();
    let finishEvaluation!: () => void;
    const evaluation = new Promise<void>((resolve) => {
      finishEvaluation = resolve;
    });
    dependencies.turnAudioCapture = {
      start: jest.fn(async () => undefined),
      stop: jest.fn(() => true),
      take: jest.fn(async () => 'file:///ielts-part2-final.wav'),
    };
    dependencies.ieltsDialogue = {
      advanceState: jest.fn(),
      evaluateTurn: jest.fn(() => evaluation),
      advancePart2State: jest.fn(),
      getDialogueState: jest.fn(),
      getPart2State: jest.fn(async () => ({
        sceneId: 'ielts-1',
        sessionId: 'session-1',
        phase: 'LONG_TURN',
        completed: false,
        controlInstruction: 'Continue the long turn.',
      })),
    };
    dependencies.sessionApi.start.mockResolvedValue({
      sessionId: 'session-1',
      answerSdp: 'answer-sdp',
      voiceId: 'Harvey',
      systemPrompt: 'You are an IELTS examiner.',
      currentStage: 'PART_2',
    });
    const controller = new RealtimeSessionController(dependencies, {
      mode: 'ielts',
      ieltsId: 'ielts-1',
      ieltsPart: 'PART_2',
      voice: 'Harvey',
      model: 'qwen3.5-omni-flash-realtime',
      speechSpeed: 'NATURAL',
    });
    await controller.start();
    const transcriptOperation = controller.handleProviderMessage(
      JSON.stringify({
        type: 'conversation.item.input_audio_transcription.completed',
        item_id: 'part2-final-answer',
        transcript: 'That is why this experience remains important to me.',
      }),
    );
    await Promise.resolve();
    const endOperation = controller.end();

    expect(dependencies.sessionSocket.end).not.toHaveBeenCalled();
    finishEvaluation();
    await transcriptOperation;
    await endOperation;

    expect(dependencies.sessionSocket.end).toHaveBeenCalledTimes(1);
  });

  it('restores ielts dialogue state after session start', async () => {
    const dependencies = createDependencies();
    const ieltsDialogue: NonNullable<RealtimeSessionDependencies['ieltsDialogue']> = {
      advanceState: jest.fn(),
      evaluateTurn: jest.fn(),
      advancePart2State: jest.fn(),
      getDialogueState: jest.fn(async () => ({
        sceneId: 'ielts-1',
        sessionId: 'session-1',
        part: 'PART_3' as const,
        openingCompleted: true,
        answeredQuestions: 2,
        totalQuestions: 5,
        completed: false,
        controlInstruction: 'Ask question three exactly as written.',
      })),
      getPart2State: jest.fn(),
    };
    dependencies.ieltsDialogue = ieltsDialogue;
    dependencies.sessionApi.start.mockResolvedValue({
      sessionId: 'session-1',
      answerSdp: 'answer-sdp',
      voiceId: 'Harvey',
      systemPrompt: 'You are an IELTS examiner.',
      currentStage: 'PART_3',
    });
    const controller = new RealtimeSessionController(dependencies, {
      mode: 'ielts',
      ieltsId: 'ielts-1',
      ieltsPart: 'PART_3',
      voice: 'Harvey',
      model: 'qwen3.5-omni-flash-realtime',
      speechSpeed: 'NATURAL',
    });

    await controller.start();

    expect(ieltsDialogue.getDialogueState).toHaveBeenCalledWith('session-1');
    expect(controller.getSnapshot()).toEqual(
      expect.objectContaining({
        ieltsDialogueState: expect.objectContaining({ answeredQuestions: 2 }),
        ieltsStateRestored: true,
      }),
    );
  });
});
