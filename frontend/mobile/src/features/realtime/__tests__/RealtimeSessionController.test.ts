import {
  RealtimeSessionController,
  type RealtimeSessionDependencies,
  type RealtimeTransportEvent,
} from '../RealtimeSessionController';
import type { ScenarioDialogueState } from '@/features/scenes/SceneDialogueApi';

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

async function releaseSceneInput(controller: RealtimeSessionController) {
  jest.useFakeTimers();
  await controller.handleProviderMessage(JSON.stringify({ type: 'session.updated' }));
  await controller.handleProviderMessage(JSON.stringify({ type: 'response.created' }));
  await controller.handleProviderMessage(
    JSON.stringify({ type: 'response.done', response: { status: 'completed' } }),
  );
  jest.advanceTimersByTime(1_200);
  await flushMicrotasks();
  jest.useRealTimers();
}

async function flushMicrotasks() {
  for (let index = 0; index < 10; index += 1) {
    await Promise.resolve();
  }
}

describe('RealtimeSessionController', () => {
  it('ignores malformed provider payloads and classifies transport failures', async () => {
    const dependencies = createDependencies();
    const controller = new RealtimeSessionController(dependencies, {
      mode: 'free_chat', voice: 'Harvey', model: 'qwen3.5-omni-flash-realtime', speechSpeed: 'NATURAL',
    });

    await controller.handleProviderMessage('{not-json');
    expect(controller.getSnapshot().error).toBeNull();
    dependencies.transport.emit({ type: 'ice.failed', message: 'ICE failed' });
    await flushMicrotasks();
    expect(controller.getSnapshot().error).toEqual(expect.objectContaining({ code: 'ICE_FAILED', message: 'ICE failed' }));
    dependencies.transport.emit({ type: 'datachannel.failed' });
    await flushMicrotasks();
    expect(controller.getSnapshot().error).toEqual(expect.objectContaining({ code: 'DATA_CHANNEL_FAILED' }));
  });

  it('handles assistant deltas, provider cancellation races, and ordinary provider errors', async () => {
    const dependencies = createDependencies();
    const controller = new RealtimeSessionController(dependencies, {
      mode: 'scene', sceneId: 'scene-1', voice: 'Harvey', model: 'qwen3.5-omni-flash-realtime', speechSpeed: 'NATURAL',
    });
    await controller.start();
    await controller.handleProviderMessage(JSON.stringify({ type: 'session.updated' }));
    await controller.handleProviderMessage(JSON.stringify({ type: 'response.audio_transcript.delta', delta: 'hello' }));
    expect(controller.getSnapshot().assistantTranscript).toBe('hello');
    await controller.handleProviderMessage(JSON.stringify({
      type: 'error', error: { message: 'response already has an active response' },
    }));
    await controller.handleProviderMessage(JSON.stringify({
      type: 'error', error: { message: 'provider exploded' },
    }));
    expect(controller.getSnapshot().error).toEqual(expect.objectContaining({ code: 'PROVIDER_ERROR' }));
  });

  it('ends an idle session and does not duplicate cleanup', async () => {
    const dependencies = createDependencies();
    const controller = new RealtimeSessionController(dependencies, {
      mode: 'free_chat', voice: 'Harvey', model: 'qwen3.5-omni-flash-realtime', speechSpeed: 'NATURAL',
    });
    await controller.end();
    await controller.end();
    expect(dependencies.transport.close).toHaveBeenCalledTimes(1);
    expect(dependencies.sessionSocket.close).toHaveBeenCalledTimes(1);
  });

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

  it('observes each scene transcript without creating a manual turn response', async () => {
    const dependencies = createDependencies();
    const turnAudioCapture = {
      start: jest.fn(async () => undefined),
      stop: jest.fn(() => true),
      take: jest.fn(async () => 'file:///scene-turn.wav'),
      release: jest.fn(async () => undefined),
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
    await releaseSceneInput(controller);
    expect(turnAudioCapture.start).toHaveBeenCalledTimes(1);
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
    await flushMicrotasks();

    expect(sceneDialogue.advanceState).toHaveBeenCalledWith(
      'session-1',
      1,
      'How much is the total?',
    );
    expect(turnAudioCapture.start).toHaveBeenCalledTimes(1);
    expect(turnAudioCapture.stop).toHaveBeenCalledTimes(2);
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
    expect(
      dependencies.transport.sendProviderEvent.mock.calls.filter(
        ([event]) => event.type === 'response.create',
      ),
    ).toHaveLength(1);
    expect(controller.getSnapshot().sceneState).toEqual(
      expect.objectContaining({ effectiveUserTurns: 1, completed: false }),
    );
  });

  it('does not wait for scene scoring audio before advancing state', async () => {
    const dependencies = createDependencies();
    let finishTakingAudio!: (uri: string) => void;
    dependencies.turnAudioCapture = {
      start: jest.fn(async () => undefined),
      stop: jest.fn(() => true),
      take: jest.fn(() => new Promise<string>((resolve) => {
        finishTakingAudio = resolve;
      })),
      release: jest.fn(async () => undefined),
    };
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
        controlInstruction: 'Continue naturally.',
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
    await releaseSceneInput(controller);

    const completed = controller.handleProviderMessage(
      JSON.stringify({
        type: 'conversation.item.input_audio_transcription.completed',
        item_id: 'fast-response-turn',
        transcript: 'I would like a large latte.',
      }),
    );
    await flushMicrotasks();

    expect(sceneDialogue.advanceState).toHaveBeenCalledTimes(1);
    expect(
      dependencies.transport.sendProviderEvent.mock.calls.filter(
        ([event]) => event.type === 'response.create',
      ),
    ).toHaveLength(1);
    expect(sceneDialogue.evaluateTurn).not.toHaveBeenCalled();

    finishTakingAudio('file:///scene-turn.wav');
    await completed;
    await flushMicrotasks();
    expect(sceneDialogue.evaluateTurn).toHaveBeenCalledWith(
      'session-1',
      1,
      'I would like a large latte.',
      'file:///scene-turn.wav',
    );
  });

  it('waits for a continuation after a trailing scene filler and merges both fragments', async () => {
    const dependencies = createDependencies();
    const turnAudioCapture = {
      start: jest.fn(async () => undefined),
      stop: jest.fn(() => true),
      take: jest.fn(async () => 'file:///fragment.wav'),
      release: jest.fn(async () => undefined),
    };
    dependencies.turnAudioCapture = turnAudioCapture;
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
        controlInstruction: 'Continue naturally.',
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
    await releaseSceneInput(controller);

    jest.useFakeTimers();
    try {
      await controller.handleProviderMessage(
        JSON.stringify({
          type: 'conversation.item.input_audio_transcription.completed',
          item_id: 'filler-fragment',
          transcript: 'I would like a, um.',
        }),
      );
      await flushMicrotasks();

      expect(sceneDialogue.advanceState).not.toHaveBeenCalled();
      expect(
        dependencies.transport.sendProviderEvent.mock.calls.filter(
          ([event]) => event.type === 'response.create',
        ),
      ).toHaveLength(1);
      expect(turnAudioCapture.release).toHaveBeenCalledTimes(1);

      jest.advanceTimersByTime(800);
      await controller.handleProviderMessage(
        JSON.stringify({ type: 'input_audio_buffer.speech_started' }),
      );
      await controller.handleProviderMessage(
        JSON.stringify({ type: 'input_audio_buffer.speech_stopped' }),
      );
      await controller.handleProviderMessage(
        JSON.stringify({
          type: 'conversation.item.input_audio_transcription.completed',
          item_id: 'continued-fragment',
          transcript: 'large latte, please.',
        }),
      );
      await flushMicrotasks();

      expect(sceneDialogue.advanceState).toHaveBeenCalledWith(
        'session-1',
        1,
        'I would like a, um large latte, please.',
      );
      expect(sceneDialogue.evaluateTurn).toHaveBeenCalledWith(
        'session-1',
        1,
        'I would like a, um large latte, please.',
        null,
      );
      expect(dependencies.sessionSocket.persistMessage).toHaveBeenCalledWith(
        expect.objectContaining({
          owner: 1,
          content: 'I would like a, um large latte, please.',
        }),
      );
    } finally {
      jest.useRealTimers();
    }
  });

  it('submits a trailing scene filler when the continuation window expires', async () => {
    const dependencies = createDependencies();
    dependencies.turnAudioCapture = {
      start: jest.fn(async () => undefined),
      stop: jest.fn(() => true),
      take: jest.fn(async () => 'file:///fragment.wav'),
      release: jest.fn(async () => undefined),
    };
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
        controlInstruction: 'Continue naturally.',
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
    await releaseSceneInput(controller);

    jest.useFakeTimers();
    try {
      await controller.handleProviderMessage(
        JSON.stringify({
          type: 'conversation.item.input_audio_transcription.completed',
          item_id: 'filler-timeout',
          transcript: 'Well, um.',
        }),
      );
      jest.advanceTimersByTime(1_200);
      await flushMicrotasks();

      expect(sceneDialogue.advanceState).toHaveBeenCalledWith(
        'session-1',
        1,
        'Well, um.',
      );
      expect(sceneDialogue.evaluateTurn).toHaveBeenCalledWith(
        'session-1',
        1,
        'Well, um.',
        null,
      );
    } finally {
      jest.useRealTimers();
    }
  });

  it('configures scene semantic VAD and transcription for short English answers', async () => {
    const dependencies = createDependencies();
    const controller = new RealtimeSessionController(dependencies, {
      mode: 'scene',
      sceneId: 'scene-1',
      voice: 'Harvey',
      model: 'qwen3.5-omni-flash-realtime',
      speechSpeed: 'NATURAL',
    });
    await controller.start();
    await controller.handleProviderMessage(JSON.stringify({ type: 'session.created' }));

    expect(dependencies.transport.sendProviderEvent).toHaveBeenCalledWith(
      expect.objectContaining({
        type: 'session.update',
        session: expect.objectContaining({
          input_audio_transcription: {
            model: 'qwen3-asr-flash-realtime',
            language: 'en',
          },
          turn_detection: expect.objectContaining({
            type: 'semantic_vad',
            threshold: 0.4,
            prefix_padding_ms: 1_000,
            create_response: true,
            interrupt_response: true,
          }),
        }),
      }),
    );
  });

  it('keeps scene input available while the asynchronous state machine catches up', async () => {
    const dependencies = createDependencies();
    let finishState!: (state: ScenarioDialogueState) => void;
    const sceneDialogue: NonNullable<RealtimeSessionDependencies['sceneDialogue']> = {
      advanceState: jest.fn(() => new Promise((resolve) => {
        finishState = resolve;
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
    await releaseSceneInput(controller);

    const transcript = controller.handleProviderMessage(
      JSON.stringify({
        type: 'conversation.item.input_audio_transcription.completed',
        item_id: 'slow-state-turn',
        transcript: 'I would like a large latte.',
      }),
    );
    await flushMicrotasks();

    expect(sceneDialogue.advanceState).toHaveBeenCalledTimes(1);
    expect(dependencies.transport.setAudioEnabled).toHaveBeenLastCalledWith(true);

    finishState({
      sceneId: 'scene-1',
      sessionId: 'session-1',
      stage: 'CORE_TASK',
      effectiveUserTurns: 1,
      maximumUserTurns: 6,
      outcomes: [],
      completed: false,
      completionReason: null,
      controlInstruction: 'Continue naturally.',
      warning: null,
    });
    await transcript;
  });

  it('arms scene completion only after state analysis reaches the latest user turn', async () => {
    const dependencies = createDependencies();
    let finishFirst!: (state: ScenarioDialogueState) => void;
    let finishSecond!: (state: ScenarioDialogueState) => void;
    const sceneDialogue: NonNullable<RealtimeSessionDependencies['sceneDialogue']> = {
      advanceState: jest
        .fn()
        .mockImplementationOnce(() => new Promise((resolve) => {
          finishFirst = resolve;
        }))
        .mockImplementationOnce(() => new Promise((resolve) => {
          finishSecond = resolve;
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
    await releaseSceneInput(controller);

    await controller.handleProviderMessage(JSON.stringify({
      type: 'conversation.item.input_audio_transcription.completed',
      item_id: 'turn-1',
      transcript: 'I would like a latte.',
    }));
    await controller.handleProviderMessage(JSON.stringify({
      type: 'input_audio_buffer.speech_started',
    }));
    await flushMicrotasks();

    finishFirst({
      sceneId: 'scene-1',
      sessionId: 'session-1',
      stage: 'COMPLETED',
      effectiveUserTurns: 1,
      maximumUserTurns: 6,
      outcomes: [],
      completed: true,
      completionReason: 'OUTCOME_REACHED',
      controlInstruction: 'Close the order.',
      warning: null,
    });
    await flushMicrotasks();

    expect(sceneDialogue.advanceState).toHaveBeenCalledTimes(1);
    expect(
      dependencies.transport.sendProviderEvent.mock.calls.filter(
        ([event]) => event.type === 'response.create',
      ),
    ).toHaveLength(1);
    expect(dependencies.transport.setAudioEnabled).toHaveBeenLastCalledWith(true);

    await controller.handleProviderMessage(JSON.stringify({
      type: 'input_audio_buffer.speech_stopped',
    }));
    await controller.handleProviderMessage(JSON.stringify({
      type: 'conversation.item.input_audio_transcription.completed',
      item_id: 'turn-2',
      transcript: 'Make that a large latte.',
    }));
    await flushMicrotasks();

    expect(sceneDialogue.advanceState).toHaveBeenCalledTimes(2);

    finishSecond({
      sceneId: 'scene-1',
      sessionId: 'session-1',
      stage: 'COMPLETED',
      effectiveUserTurns: 2,
      maximumUserTurns: 6,
      outcomes: [],
      completed: true,
      completionReason: 'OUTCOME_REACHED',
      controlInstruction: 'Close the updated order.',
      warning: null,
    });
    await flushMicrotasks();

    expect(
      dependencies.transport.sendProviderEvent.mock.calls.filter(
        ([event]) => event.type === 'response.create',
      ),
    ).toHaveLength(1);
    expect(dependencies.transport.setAudioEnabled).toHaveBeenLastCalledWith(false);
  });

  it('keeps scene WebRTC input open while delayed scoring capture starts', async () => {
    const dependencies = createDependencies();
    let finishStarting!: () => void;
    dependencies.turnAudioCapture = {
      start: jest.fn(() => new Promise<void>((resolve) => {
        finishStarting = resolve;
      })),
      stop: jest.fn(() => true),
      take: jest.fn(async () => 'file:///scene-turn.wav'),
      release: jest.fn(async () => undefined),
    };
    dependencies.sceneDialogue = {
      advanceState: jest.fn(),
      evaluateTurn: jest.fn(),
      complete: jest.fn(async () => null),
    };
    const controller = new RealtimeSessionController(dependencies, {
      mode: 'scene',
      sceneId: 'scene-1',
      voice: 'Harvey',
      model: 'qwen3.5-omni-flash-realtime',
      speechSpeed: 'NATURAL',
    });
    await controller.start();

    jest.useFakeTimers();
    try {
      await controller.handleProviderMessage(JSON.stringify({ type: 'session.updated' }));
      await controller.handleProviderMessage(JSON.stringify({ type: 'response.created' }));
      await controller.handleProviderMessage(
        JSON.stringify({ type: 'response.done', response: { status: 'completed' } }),
      );
      expect(dependencies.transport.setAudioEnabled).toHaveBeenLastCalledWith(true);

      jest.advanceTimersByTime(1_200);
      await Promise.resolve();

      expect(dependencies.turnAudioCapture.start).toHaveBeenCalledTimes(1);
      expect(dependencies.transport.setAudioEnabled).toHaveBeenLastCalledWith(true);

      finishStarting();
      await flushMicrotasks();

      expect(dependencies.transport.setAudioEnabled).toHaveBeenLastCalledWith(true);
    } finally {
      jest.useRealTimers();
    }
  });

  it('applies scene state while the provider-managed response remains active', async () => {
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
    await releaseSceneInput(controller);
    await controller.handleProviderMessage(JSON.stringify({ type: 'response.created' }));

    await controller.handleProviderMessage(
      JSON.stringify({
        type: 'conversation.item.input_audio_transcription.completed',
        item_id: 'repractice-turn-1',
        transcript: 'I would like to check in.',
      }),
    );
    await flushMicrotasks();

    expect(
      dependencies.transport.sendProviderEvent.mock.calls.filter(
        ([event]) => event.type === 'response.create',
      ),
    ).toHaveLength(1);
    expect(dependencies.transport.sendProviderEvent).toHaveBeenCalledWith(
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
    ).toHaveLength(1);
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
    await releaseSceneInput(controller);
    const transcript = JSON.stringify({
      type: 'conversation.item.input_audio_transcription.completed',
      item_id: 'same-turn',
      transcript: 'Here is my passport.',
    });

    await controller.handleProviderMessage(transcript);
    await controller.handleProviderMessage(transcript);
    await flushMicrotasks();

    expect(sceneDialogue.advanceState).toHaveBeenCalledTimes(1);
    expect(sceneDialogue.evaluateTurn).toHaveBeenCalledTimes(1);
    expect(dependencies.sessionSocket.persistMessage).toHaveBeenCalledTimes(1);
  });

  it('tolerates an active-response provider signal without duplicating the VAD response', async () => {
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
    await releaseSceneInput(controller);
    await controller.handleProviderMessage(
      JSON.stringify({
        type: 'conversation.item.input_audio_transcription.completed',
        item_id: 'race-turn',
        transcript: 'Can I see the room?',
      }),
    );
    await flushMicrotasks();
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
    expect(dependencies.transport.setAudioEnabled).toHaveBeenLastCalledWith(true);

    jest.useFakeTimers();
    await controller.handleProviderMessage(
      JSON.stringify({ type: 'response.done', response: { status: 'completed' } }),
    );
    jest.advanceTimersByTime(1_200);
    await Promise.resolve();
    await Promise.resolve();
    jest.useRealTimers();
    expect(
      dependencies.transport.sendProviderEvent.mock.calls.filter(
        ([event]) => event.type === 'response.create',
      ),
    ).toHaveLength(1);
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
    await releaseSceneInput(controller);
    await controller.handleProviderMessage(
      JSON.stringify({
        type: 'conversation.item.input_audio_transcription.completed',
        item_id: 'final-turn',
        transcript: 'Thank you, goodbye.',
      }),
    );
    await flushMicrotasks();

    expect(
      dependencies.transport.sendProviderEvent.mock.calls.filter(
        ([event]) => event.type === 'response.create',
      ),
    ).toHaveLength(1);
    expect(sceneDialogue.complete).not.toHaveBeenCalled();
    await controller.handleProviderMessage(JSON.stringify({ type: 'response.created' }));
    expect(sceneDialogue.complete).not.toHaveBeenCalled();

    jest.useFakeTimers();
    await controller.handleProviderMessage(
      JSON.stringify({ type: 'response.done', response: { status: 'completed' } }),
    );
    await flushMicrotasks();
    jest.advanceTimersByTime(1_200);
    await Promise.resolve();
    await Promise.resolve();
    jest.useRealTimers();

    expect(sceneDialogue.complete).toHaveBeenCalledTimes(1);
    expect(controller.getSnapshot()).toEqual(
      expect.objectContaining({ state: 'ended', completion }),
    );
  });

  it('does not append a closing response when scene state completes after native reply', async () => {
    const dependencies = createDependencies();
    let finishState!: (state: ScenarioDialogueState) => void;
    const sceneDialogue: NonNullable<RealtimeSessionDependencies['sceneDialogue']> = {
      advanceState: jest.fn(() => new Promise((resolve) => {
        finishState = resolve;
      })),
      evaluateTurn: jest.fn(async () => null),
      complete: jest.fn(async () => ({
        sceneId: 'scene-1',
        sessionId: 'session-1',
        stopTime: '2026-08-20T03:00:00.000Z',
      })),
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
    await releaseSceneInput(controller);

    await controller.handleProviderMessage(JSON.stringify({
      type: 'conversation.item.input_audio_transcription.completed',
      item_id: 'final-native-turn',
      transcript: 'I will take the bus. Thank you.',
    }));
    await flushMicrotasks();
    await controller.handleProviderMessage(JSON.stringify({ type: 'response.created' }));
    await controller.handleProviderMessage(
      JSON.stringify({ type: 'response.done', response: { status: 'completed' } }),
    );

    jest.useFakeTimers();
    try {
      finishState({
        sceneId: 'scene-1',
        sessionId: 'session-1',
        stage: 'COMPLETED',
        effectiveUserTurns: 3,
        maximumUserTurns: 6,
        outcomes: [],
        completed: true,
        completionReason: 'OUTCOME_REACHED',
        controlInstruction: 'Say one natural farewell.',
        warning: null,
      });
      await flushMicrotasks();

      expect(
        dependencies.transport.sendProviderEvent.mock.calls.filter(
          ([event]) => event.type === 'response.create',
        ),
      ).toHaveLength(1);

      jest.advanceTimersByTime(1_200);
      await flushMicrotasks();
      expect(sceneDialogue.complete).toHaveBeenCalledTimes(1);
    } finally {
      jest.useRealTimers();
    }
  });

  it('keeps the scene open when the native reply still asks the learner a question', async () => {
    const dependencies = createDependencies();
    let finishState!: (state: ScenarioDialogueState) => void;
    const completedState: ScenarioDialogueState = {
      sceneId: 'scene-1',
      sessionId: 'session-1',
      stage: 'COMPLETED',
      effectiveUserTurns: 3,
      maximumUserTurns: 6,
      outcomes: [],
      completed: true,
      completionReason: 'OUTCOME_REACHED',
      controlInstruction: 'Confirm the order and close naturally.',
      warning: null,
    };
    let stateCalls = 0;
    const sceneDialogue: NonNullable<RealtimeSessionDependencies['sceneDialogue']> = {
      advanceState: jest.fn(() => {
        stateCalls += 1;
        if (stateCalls > 1) return Promise.resolve(completedState);
        return new Promise<ScenarioDialogueState>((resolve) => {
          finishState = resolve;
        });
      }),
      evaluateTurn: jest.fn(async () => null),
      complete: jest.fn(async () => ({
        sceneId: 'scene-1',
        sessionId: 'session-1',
        stopTime: '2026-08-20T03:00:00.000Z',
      })),
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
    await releaseSceneInput(controller);

    await controller.handleProviderMessage(JSON.stringify({
      type: 'conversation.item.input_audio_transcription.completed',
      item_id: 'sauce-answer',
      transcript: "I don't need any sauce.",
    }));
    await flushMicrotasks();
    await controller.handleProviderMessage(JSON.stringify({ type: 'response.created' }));
    await controller.handleProviderMessage(JSON.stringify({
      type: 'response.audio_transcript.done',
      item_id: 'confirmation-question',
      transcript: 'Understood. No sauce at all. Just the steak and vegetables?',
    }));
    await controller.handleProviderMessage(
      JSON.stringify({ type: 'response.done', response: { status: 'completed' } }),
    );

    jest.useFakeTimers();
    try {
      finishState(completedState);
      await flushMicrotasks();
      jest.advanceTimersByTime(1_200);
      await flushMicrotasks();

      expect(sceneDialogue.complete).not.toHaveBeenCalled();
      expect(controller.getSnapshot()).toEqual(
        expect.objectContaining({ state: 'ready', completion: null }),
      );
      expect(dependencies.transport.setAudioEnabled).toHaveBeenLastCalledWith(true);

      await controller.handleProviderMessage(JSON.stringify({
        type: 'conversation.item.input_audio_transcription.completed',
        item_id: 'order-confirmation',
        transcript: 'Yes, that is correct.',
      }));
      await flushMicrotasks();
      await controller.handleProviderMessage(JSON.stringify({ type: 'response.created' }));
      await controller.handleProviderMessage(JSON.stringify({
        type: 'response.audio_transcript.done',
        item_id: 'natural-farewell',
        transcript: 'Perfect. Your order is confirmed. Enjoy your meal!',
      }));
      await controller.handleProviderMessage(
        JSON.stringify({ type: 'response.done', response: { status: 'completed' } }),
      );
      jest.advanceTimersByTime(1_200);
      await flushMicrotasks();

      expect(sceneDialogue.complete).toHaveBeenCalledTimes(1);
      expect(controller.getSnapshot().state).toBe('ended');
    } finally {
      jest.useRealTimers();
    }
  });

  it('accepts a scene barge-in while the assistant is speaking without scoring mixed audio', async () => {
    const dependencies = createDependencies();
    const turnAudioCapture = {
      start: jest.fn(async () => undefined),
      stop: jest.fn(() => true),
      take: jest.fn(async () => 'file:///mixed-turn.wav'),
      release: jest.fn(async () => undefined),
    };
    dependencies.turnAudioCapture = turnAudioCapture;
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
        controlInstruction: 'Continue the order.',
        warning: null,
      })),
      evaluateTurn: jest.fn(async () => null),
      complete: jest.fn(),
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

    expect(dependencies.transport.setAudioEnabled).toHaveBeenLastCalledWith(true);
    await controller.handleProviderMessage(
      JSON.stringify({ type: 'input_audio_buffer.speech_started' }),
    );
    expect(controller.getSnapshot().state).toBe('user_speaking');
    expect(dependencies.transport.sendProviderEvent).toHaveBeenCalledWith(
      expect.objectContaining({ type: 'response.cancel' }),
    );
    await controller.handleProviderMessage(JSON.stringify({
      type: 'error',
      error: { message: 'Cancellation failed: no active response found' },
    }));
    expect(controller.getSnapshot().state).toBe('user_speaking');
    await controller.handleProviderMessage(
      JSON.stringify({ type: 'response.done', response: { status: 'cancelled' } }),
    );
    await controller.handleProviderMessage(
      JSON.stringify({ type: 'input_audio_buffer.speech_stopped' }),
    );
    await controller.handleProviderMessage(
      JSON.stringify({
        type: 'conversation.item.input_audio_transcription.completed',
        item_id: 'barge-in',
        transcript: 'Large.',
      }),
    );
    await flushMicrotasks();

    expect(sceneDialogue.advanceState).toHaveBeenCalledWith(
      'session-1',
      1,
      'Large.',
    );
    expect(sceneDialogue.evaluateTurn).toHaveBeenCalledWith(
      'session-1',
      1,
      'Large.',
      null,
    );
    expect(turnAudioCapture.start).not.toHaveBeenCalled();
    expect(
      dependencies.transport.sendProviderEvent.mock.calls.filter(
        ([event]) => event.type === 'response.create',
      ),
    ).toHaveLength(1);
  });

  it('treats speech during pending scene capture startup as an unscored barge-in', async () => {
    const dependencies = createDependencies();
    let finishStarting!: () => void;
    const turnAudioCapture = {
      start: jest.fn(() => new Promise<void>((resolve) => {
        finishStarting = resolve;
      })),
      stop: jest.fn(() => true),
      take: jest.fn(async () => 'file:///late-start.wav'),
      release: jest.fn(async () => undefined),
    };
    dependencies.turnAudioCapture = turnAudioCapture;
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
        controlInstruction: 'Continue the order.',
        warning: null,
      })),
      evaluateTurn: jest.fn(async () => null),
      complete: jest.fn(),
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

    jest.useFakeTimers();
    try {
      await controller.handleProviderMessage(JSON.stringify({ type: 'session.updated' }));
      await controller.handleProviderMessage(JSON.stringify({ type: 'response.created' }));
      await controller.handleProviderMessage(
        JSON.stringify({ type: 'response.done', response: { status: 'completed' } }),
      );
      await flushMicrotasks();
      jest.advanceTimersByTime(1_200);
      await Promise.resolve();
      expect(turnAudioCapture.start).toHaveBeenCalledTimes(1);

      const speechStarted = controller.handleProviderMessage(
        JSON.stringify({ type: 'input_audio_buffer.speech_started' }),
      );
      await flushMicrotasks();
      expect(turnAudioCapture.release).not.toHaveBeenCalled();

      finishStarting();
      await speechStarted;
      expect(turnAudioCapture.release).toHaveBeenCalledTimes(1);

      await controller.handleProviderMessage(
        JSON.stringify({ type: 'input_audio_buffer.speech_stopped' }),
      );
      await controller.handleProviderMessage(
        JSON.stringify({
          type: 'conversation.item.input_audio_transcription.completed',
          item_id: 'capture-start-race',
          transcript: 'Large.',
        }),
      );
      await flushMicrotasks();

      expect(sceneDialogue.evaluateTurn).toHaveBeenCalledWith(
        'session-1',
        1,
        'Large.',
        null,
      );
    } finally {
      jest.useRealTimers();
    }
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

  it('opens Part 1 with the examiner introduction before asking question one', async () => {
    const dependencies = createDependencies();
    const ieltsDialogue: NonNullable<RealtimeSessionDependencies['ieltsDialogue']> = {
      advanceState: jest.fn(async () => ({
        sceneId: 'ielts-1',
        sessionId: 'session-1',
        part: 'PART_1' as const,
        openingCompleted: true,
        answeredQuestions: 0,
        totalQuestions: 4,
        completed: false,
        controlInstruction: 'Ask question one exactly as written.',
      })),
      evaluateTurn: jest.fn(async () => null),
      advancePart2State: jest.fn(),
      getDialogueState: jest.fn(async () => ({
        sceneId: 'ielts-1',
        sessionId: 'session-1',
        part: 'PART_1' as const,
        openingCompleted: false,
        answeredQuestions: 0,
        totalQuestions: 4,
        completed: false,
        controlInstruction: 'Ask question one exactly as written.',
      })),
      getPart2State: jest.fn(),
    };
    dependencies.ieltsDialogue = ieltsDialogue;
    dependencies.sessionApi.start.mockResolvedValue({
      sessionId: 'session-1',
      answerSdp: 'answer-sdp',
      voiceId: 'Harvey',
      systemPrompt: 'Introduce yourself and ask the candidate to introduce themselves.',
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
    dependencies.transport.waitForDataChannel.mockImplementationOnce(async () => {
      await controller.handleProviderMessage(JSON.stringify({ type: 'session.created' }));
    });

    await controller.start();
    await controller.handleProviderMessage(JSON.stringify({ type: 'session.updated' }));

    const initialUpdates = dependencies.transport.sendProviderEvent.mock.calls
      .map(([event]) => event)
      .filter((event) => event.type === 'session.update');
    expect(initialUpdates).toHaveLength(1);
    expect(initialUpdates[0]).toEqual(expect.objectContaining({
      session: expect.objectContaining({
        instructions: expect.stringContaining('ask the candidate to introduce themselves'),
      }),
    }));
    expect(initialUpdates[0].session.instructions).not.toContain('question one');

    await controller.handleProviderMessage(
      JSON.stringify({
        type: 'conversation.item.input_audio_transcription.completed',
        item_id: 'candidate-introduction',
        transcript: 'My name is Alex and I am from Shanghai.',
      }),
    );

    expect(ieltsDialogue.advanceState).toHaveBeenCalledWith('session-1', 1, false);
    expect(ieltsDialogue.evaluateTurn).not.toHaveBeenCalled();
    expect(dependencies.transport.sendProviderEvent).toHaveBeenCalledWith(
      expect.objectContaining({
        type: 'session.update',
        session: expect.objectContaining({
          instructions: expect.stringContaining('question one'),
        }),
      }),
    );
  });

  it('publishes an IELTS completion-ready signal after the closing response finishes', async () => {
    const dependencies = createDependencies();
    const ieltsDialogue: NonNullable<RealtimeSessionDependencies['ieltsDialogue']> = {
      advanceState: jest.fn(async () => ({
        sceneId: 'ielts-1',
        sessionId: 'session-1',
        part: 'PART_1' as const,
        openingCompleted: true,
        answeredQuestions: 4,
        totalQuestions: 4,
        completed: true,
        controlInstruction: 'Thank you. That is the end of Part 1.',
      })),
      evaluateTurn: jest.fn(async () => null),
      advancePart2State: jest.fn(),
      getDialogueState: jest.fn(async () => ({
        sceneId: 'ielts-1',
        sessionId: 'session-1',
        part: 'PART_1' as const,
        openingCompleted: true,
        answeredQuestions: 3,
        totalQuestions: 4,
        completed: false,
        controlInstruction: 'Ask the final Part 1 question.',
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
    await controller.handleProviderMessage(JSON.stringify({ type: 'response.created' }));
    await controller.handleProviderMessage(
      JSON.stringify({ type: 'response.done', response: { status: 'completed' } }),
    );
    await controller.handleProviderMessage(
      JSON.stringify({
        type: 'conversation.item.input_audio_transcription.completed',
        item_id: 'final-part-one-answer',
        transcript: 'That is my final answer.',
      }),
    );

    expect(controller.getSnapshot()).toEqual(expect.objectContaining({
      ieltsDialogueCompleted: true,
      ieltsCompletionReady: false,
    }));
    await controller.handleProviderMessage(JSON.stringify({ type: 'response.created' }));
    await controller.handleProviderMessage(
      JSON.stringify({ type: 'response.done', response: { status: 'completed' } }),
    );

    expect(controller.getSnapshot()).toEqual(expect.objectContaining({
      ieltsDialogueCompleted: true,
      ieltsCompletionReady: true,
    }));
    expect(dependencies.sessionSocket.end).not.toHaveBeenCalled();
  });

  it('uses a three-second IELTS Part 1 silence window and asks the next question without waiting for scoring', async () => {
    const dependencies = createDependencies();
    let finishEvaluation!: () => void;
    const pendingEvaluation = new Promise<void>((resolve) => {
      finishEvaluation = resolve;
    });
    const ieltsDialogue: NonNullable<RealtimeSessionDependencies['ieltsDialogue']> = {
      advanceState: jest.fn(async () => ({
        sceneId: 'ielts-1',
        sessionId: 'session-1',
        part: 'PART_1' as const,
        openingCompleted: true,
        answeredQuestions: 1,
        totalQuestions: 4,
        completed: false,
        controlInstruction: 'Ask the next Part 1 question.',
      })),
      evaluateTurn: jest.fn(() => pendingEvaluation),
      advancePart2State: jest.fn(),
      getDialogueState: jest.fn(async () => ({
        sceneId: 'ielts-1',
        sessionId: 'session-1',
        part: 'PART_1' as const,
        openingCompleted: true,
        answeredQuestions: 0,
        totalQuestions: 4,
        completed: false,
        controlInstruction: 'Ask the first Part 1 question.',
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
    await controller.handleProviderMessage(JSON.stringify({ type: 'session.created' }));

    expect(dependencies.transport.sendProviderEvent).toHaveBeenCalledWith(
      expect.objectContaining({
        type: 'session.update',
        session: expect.objectContaining({
          turn_detection: expect.objectContaining({ silence_duration_ms: 3_000 }),
        }),
      }),
    );

    const transcriptOperation = controller.handleProviderMessage(
      JSON.stringify({
        type: 'conversation.item.input_audio_transcription.completed',
        item_id: 'fast-follow-up',
        transcript: 'I work as a software engineer.',
      }),
    );
    await transcriptOperation;

    expect(ieltsDialogue.evaluateTurn).toHaveBeenCalledTimes(1);
    expect(dependencies.transport.sendProviderEvent).toHaveBeenCalledWith(
      expect.objectContaining({ type: 'response.create' }),
    );
    finishEvaluation();
    await Promise.resolve();
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

  it('cancels an old Part 2 response and replaces it with the closing instruction', async () => {
    const dependencies = createDependencies();
    const ieltsDialogue: NonNullable<RealtimeSessionDependencies['ieltsDialogue']> = {
      advanceState: jest.fn(),
      evaluateTurn: jest.fn(),
      advancePart2State: jest.fn(async () => ({
        sceneId: 'ielts-1',
        sessionId: 'session-1',
        phase: 'FINISHED',
        completed: true,
        controlInstruction: 'Thank you. That is the end of Part 2.',
      })),
      getDialogueState: jest.fn(),
      getPart2State: jest.fn(async () => ({
        sceneId: 'ielts-1',
        sessionId: 'session-1',
        phase: 'PREPARATION',
        completed: false,
        controlInstruction: 'You have one minute to prepare.',
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
    await controller.handleProviderMessage(JSON.stringify({ type: 'session.updated' }));
    await controller.handleProviderMessage(JSON.stringify({ type: 'response.created' }));

    await controller.transitionPart2('ANSWER_COMPLETE');

    expect(dependencies.transport.sendProviderEvent).toHaveBeenCalledWith(
      expect.objectContaining({ type: 'response.cancel' }),
    );
    await controller.handleProviderMessage(
      JSON.stringify({ type: 'response.done', response: { status: 'cancelled' } }),
    );
    const responseRequests = dependencies.transport.sendProviderEvent.mock.calls
      .map(([request]) => request)
      .filter((request) => request.type === 'response.create');
    expect(responseRequests.at(-1)).toEqual(expect.objectContaining({
      type: 'response.create',
      response: expect.objectContaining({
        instructions: 'Thank you. That is the end of Part 2.',
      }),
    }));
  });

  it('uploads Part 2 turn audio so the report can include pronunciation', async () => {
    const dependencies = createDependencies();
    const turnAudioCapture = {
      start: jest.fn(async () => undefined),
      stop: jest.fn(() => true),
      take: jest.fn(async () => 'file:///ielts-part2.wav'),
      release: jest.fn(async () => undefined),
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

  it('ends Part 2 without blocking on pronunciation and still exposes its completion promise', async () => {
    const dependencies = createDependencies();
    let finishEvaluation!: () => void;
    const evaluation = new Promise<void>((resolve) => {
      finishEvaluation = resolve;
    });
    dependencies.turnAudioCapture = {
      start: jest.fn(async () => undefined),
      stop: jest.fn(() => true),
      take: jest.fn(async () => 'file:///ielts-part2-final.wav'),
      release: jest.fn(async () => undefined),
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

    await endOperation;
    expect(dependencies.sessionSocket.end).toHaveBeenCalledTimes(1);
    let evaluationsCompleted = false;
    const evaluationOperation = controller.waitForTurnEvaluations().then(() => {
      evaluationsCompleted = true;
    });
    await Promise.resolve();
    expect(evaluationsCompleted).toBe(false);
    finishEvaluation();
    await transcriptOperation;
    await evaluationOperation;

    expect(evaluationsCompleted).toBe(true);
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

  it('publishes initial snapshots, supports unsubscribe, and generates default ids/timestamps', async () => {
    const dependencies = createDependencies();
    delete dependencies.createEventId;
    delete dependencies.now;
    const controller = new RealtimeSessionController(dependencies, {
      mode: 'free_chat', voice: 'Harvey', model: 'model', speechSpeed: 'NATURAL',
    });
    const listener = jest.fn();
    const unsubscribe = controller.subscribe(listener);
    expect(listener).toHaveBeenCalledWith(expect.objectContaining({ state: 'idle' }));
    await controller.start();
    await controller.handleProviderMessage(JSON.stringify({ type: 'session.created' }));
    expect(dependencies.transport.sendProviderEvent).toHaveBeenCalledWith(expect.objectContaining({
      event_id: expect.stringMatching(/^event_/),
    }));
    unsubscribe();
    const count = listener.mock.calls.length;
    controller.setMuted(true);
    expect(listener).toHaveBeenCalledTimes(count);
  });

  it('classifies missing backend SDP/prompt and optional transport session binding', async () => {
    for (const backend of [
      { sessionId: 'session-1', answerSdp: '', voiceId: 'voice', systemPrompt: 'prompt' },
      { sessionId: 'session-1', answerSdp: 'answer', voiceId: 'voice', systemPrompt: '' },
    ]) {
      const dependencies = createDependencies();
      dependencies.transport.bindSession = jest.fn();
      dependencies.sessionApi.start.mockResolvedValueOnce(backend as any);
      const controller = new RealtimeSessionController(dependencies, {
        mode: 'free_chat', voice: 'Harvey', model: 'model', speechSpeed: 'NATURAL',
      });
      await expect(controller.start()).rejects.toThrow(/后端没有返回/);
      expect(controller.getSnapshot().error).toEqual(expect.objectContaining({ code: 'SDP_EXCHANGE_FAILED' }));
    }

    const dependencies = createDependencies();
    dependencies.transport.bindSession = jest.fn();
    const controller = new RealtimeSessionController(dependencies, {
      mode: 'free_chat', voice: 'Harvey', model: 'model', speechSpeed: 'NATURAL',
    });
    await controller.start();
    expect(dependencies.transport.bindSession).toHaveBeenCalledWith('session-1');
  });

  it('guards Part 2 transitions and completes the Part 2 response signal', async () => {
    const invalid = new RealtimeSessionController(createDependencies(), {
      mode: 'ielts', ieltsId: 'ielts-1', ieltsPart: 'PART_1', voice: 'Harvey', model: 'model', speechSpeed: 'NATURAL',
    });
    await expect(invalid.transitionPart2('ANSWER_COMPLETE')).rejects.toThrow('当前会话不是 IELTS Part 2');

    const dependencies = createDependencies();
    dependencies.ieltsDialogue = {
      advanceState: jest.fn(),
      evaluateTurn: jest.fn(),
      advancePart2State: jest.fn(),
      getDialogueState: jest.fn(),
      getPart2State: jest.fn(async () => ({
        sceneId: 'ielts-1', sessionId: 'session-1', phase: 'FINISHED', completed: true,
        controlInstruction: 'Part 2 is complete.',
      })),
    };
    dependencies.sessionApi.start.mockResolvedValue({
      sessionId: 'session-1', answerSdp: 'answer', voiceId: 'Harvey', systemPrompt: 'prompt', currentStage: 'PART_2',
    });
    const controller = new RealtimeSessionController(dependencies, {
      mode: 'ielts', ieltsId: 'ielts-1', ieltsPart: 'PART_2', voice: 'Harvey', model: 'model', speechSpeed: 'NATURAL',
    });
    await controller.start();
    await controller.handleProviderMessage(JSON.stringify({ type: 'response.done', response: { status: 'completed' } }));
    expect(controller.getSnapshot()).toEqual(expect.objectContaining({
      ieltsDialogueCompleted: true, ieltsPart2CompletionReady: true,
    }));
  });

  it('forces Part 3 timeout once and attaches a late transcript to that turn', async () => {
    const dependencies = createDependencies();
    const state = {
      sceneId: 'ielts-1', sessionId: 'session-1', part: 'PART_3' as const,
      openingCompleted: true, answeredQuestions: 1, totalQuestions: 4, completed: false,
      controlInstruction: 'Move to the next question.',
    };
    const ieltsDialogue = {
      advanceState: jest.fn(async () => state),
      evaluateTurn: jest.fn(async () => ({ score: 80 })),
      advancePart2State: jest.fn(),
      getDialogueState: jest.fn(async () => ({ ...state, answeredQuestions: 0 })),
      getPart2State: jest.fn(),
    };
    dependencies.ieltsDialogue = ieltsDialogue;
    dependencies.sessionApi.start.mockResolvedValue({
      sessionId: 'session-1', answerSdp: 'answer', voiceId: 'Harvey', systemPrompt: 'prompt', currentStage: 'PART_3',
    });
    const controller = new RealtimeSessionController(dependencies, {
      mode: 'ielts', ieltsId: 'ielts-1', ieltsPart: 'PART_3', voice: 'Harvey', model: 'model', speechSpeed: 'NATURAL',
    });
    await controller.start();
    await expect(controller.forcePart3Timeout()).resolves.toEqual(state);
    expect(ieltsDialogue.advanceState).toHaveBeenCalledWith('session-1', 1, true);
    await controller.handleProviderMessage(JSON.stringify({
      type: 'conversation.item.input_audio_transcription.completed', item_id: 'late-turn', transcript: 'My late answer.',
    }));
    await controller.waitForTurnEvaluations();
    expect(ieltsDialogue.evaluateTurn).toHaveBeenCalledWith('session-1', 1, 'My late answer.', null);

    ieltsDialogue.advanceState.mockResolvedValueOnce({ ...state, completed: true });
    await controller.forcePart3Timeout();
    await expect(controller.forcePart3Timeout()).resolves.toBeNull();
  });

  it('tolerates IELTS restore/state/evaluation failures and audio capture failures', async () => {
    const dependencies = createDependencies();
    dependencies.turnAudioCapture = {
      start: jest.fn(async () => { throw new Error('capture unavailable'); }),
      stop: jest.fn(() => true),
      take: jest.fn(async () => { throw new Error('take failed'); }),
      release: jest.fn(async () => undefined),
    };
    const ieltsDialogue = {
      advanceState: jest.fn(async () => { throw new Error('advance failed'); }),
      evaluateTurn: jest.fn(async () => { throw new Error('evaluate failed'); }),
      advancePart2State: jest.fn(),
      getDialogueState: jest.fn(async () => { throw new Error('restore failed'); }),
      getPart2State: jest.fn(),
    };
    dependencies.ieltsDialogue = ieltsDialogue;
    dependencies.sessionApi.start.mockResolvedValue({
      sessionId: 'session-1', answerSdp: 'answer', voiceId: 'Harvey', systemPrompt: 'prompt', currentStage: 'PART_1',
    });
    const controller = new RealtimeSessionController(dependencies, {
      mode: 'ielts', ieltsId: 'ielts-1', ieltsPart: 'PART_1', voice: 'Harvey', model: 'model', speechSpeed: 'NATURAL',
    });
    await controller.start();
    await expect(controller.restoreIeltsState()).resolves.toBeNull();
    await controller.handleProviderMessage(JSON.stringify({ type: 'session.updated' }));
    await controller.handleProviderMessage(JSON.stringify({ type: 'response.done', response: { status: 'completed' } }));
    await controller.handleProviderMessage(JSON.stringify({
      type: 'conversation.item.input_audio_transcription.completed', transcript: 'A complete answer.',
    }));
    await controller.waitForTurnEvaluations();
    expect(controller.getSnapshot().error).toBeNull();
  });

  it('covers restoration guards, completed restoration, and provider transport forwarding', async () => {
    const freeDependencies = createDependencies();
    const freeController = new RealtimeSessionController(freeDependencies, {
      mode: 'free_chat', voice: 'Harvey', model: 'model', speechSpeed: 'NATURAL',
    });
    await expect(freeController.restoreIeltsState()).resolves.toBeNull();
    freeDependencies.transport.emit({
      type: 'provider.message',
      data: JSON.stringify({ type: 'session.updated' }),
    });
    await flushMicrotasks();
    freeDependencies.transport.emit({ type: 'peer.failed' } as unknown as RealtimeTransportEvent);
    await flushMicrotasks();
    expect(freeController.getSnapshot().error).toEqual(expect.objectContaining({
      code: 'PEER_CONNECTION_FAILED',
      message: '实时连接失败',
    }));

    const dependencies = createDependencies();
    dependencies.ieltsDialogue = {
      advanceState: jest.fn(), evaluateTurn: jest.fn(), advancePart2State: jest.fn(),
      getPart2State: jest.fn(),
      getDialogueState: jest.fn(async () => ({
        sceneId: 'ielts-1', sessionId: 'session-1', part: 'PART_3' as const,
        openingCompleted: true, answeredQuestions: 3, totalQuestions: 3,
        completed: true, controlInstruction: 'Finish now.',
      })),
    };
    dependencies.sessionApi.start.mockResolvedValue({
      sessionId: 'session-1', answerSdp: 'answer', voiceId: 'Harvey',
      systemPrompt: 'prompt', currentStage: 'PART_3',
    });
    const controller = new RealtimeSessionController(dependencies, {
      mode: 'ielts', ieltsId: 'ielts-1', ieltsPart: 'PART_3', voice: 'Harvey', model: 'model', speechSpeed: 'NATURAL',
    });
    await controller.start();
    expect(controller.getSnapshot()).toEqual(expect.objectContaining({
      ieltsDialogueCompleted: true, ieltsStateRestored: true,
    }));
    await expect(controller.restoreIeltsState()).resolves.toBeNull();
  });

  it('covers transcript de-duplication and persistence retry paths', async () => {
    const dependencies = createDependencies();
    const controller = new RealtimeSessionController(dependencies, {
      mode: 'free_chat', voice: 'Harvey', model: 'model', speechSpeed: 'NATURAL',
    });
    const internal = controller as any;

    internal.captureTranscript(0, '   ');
    internal.captureTranscript(0, 'local answer');
    internal.captureTranscript(0, 'local answer');
    internal.captureTranscript(1, 'first', 'same-id');
    internal.captureTranscript(1, 'updated', 'same-id');
    expect(controller.getSnapshot().transcriptHistory).toEqual([
      expect.objectContaining({ content: 'local answer' }),
      expect.objectContaining({ content: 'updated' }),
    ]);

    await internal.persistTranscript(0, 'once', 'provider-id');
    await internal.persistTranscript(0, 'ignored', 'provider-id');
    expect(dependencies.sessionSocket.persistMessage).toHaveBeenCalledTimes(1);
    dependencies.sessionSocket.persistMessage.mockRejectedValueOnce(new Error('socket failed'));
    await expect(internal.persistTranscript(1, 'retry', 'retry-id')).rejects.toThrow('socket failed');
    dependencies.sessionSocket.persistMessage.mockResolvedValueOnce(undefined);
    await internal.persistTranscript(1, 'retry', 'retry-id');
    dependencies.sessionSocket.persistMessage.mockRejectedValueOnce(new Error('anonymous failed'));
    await expect(internal.persistTranscript(1, 'anonymous')).rejects.toThrow('anonymous failed');
  });

  it('covers audio capture guards, IELTS release branches, and response dispatch failures', async () => {
    const dependencies = createDependencies();
    const controller = new RealtimeSessionController(dependencies, {
      mode: 'free_chat', voice: 'Harvey', model: 'model', speechSpeed: 'NATURAL',
    });
    const internal = controller as any;
    await expect(internal.beginTurnAudioCapture()).resolves.toBeUndefined();
    await expect(internal.takeTurnAudioUri()).resolves.toBeNull();
    await expect(internal.coordinateIeltsTurn('answer')).rejects.toThrow('IELTS 对话服务尚未配置');
    expect(internal.flushPendingResponse()).toBe(false);

    dependencies.transport.sendProviderEvent.mockImplementationOnce(() => {
      throw new Error('send failed');
    });
    expect(() => internal.dispatchResponseRequest({
      sessionUpdate: { type: 'session.update' },
      responseCreate: { type: 'response.create' },
    })).toThrow('send failed');
    expect(internal.responseInFlight).toBe(false);
    dependencies.transport.sendProviderEvent.mockImplementation(() => undefined);
    internal.dispatchResponseRequest({
      sessionUpdate: { type: 'session.update' },
      responseCreate: { type: 'response.create' },
    });
    expect(dependencies.transport.sendProviderEvent).toHaveBeenCalledWith({ type: 'session.update' });

    internal.ieltsActivePart = 'PART_2';
    internal.ieltsDialogueCompleted = false;
    internal.handleIeltsAssistantResponseCompleted();
    expect(controller.getSnapshot().ieltsInputReadyTick).toBe(1);
    internal.ieltsActivePart = null;
    internal.releaseIeltsInput();
    internal.applyRestoredInstruction('instruction');
  });

  it('covers scene configuration guards and timer cleanup paths', async () => {
    jest.useFakeTimers();
    const dependencies = createDependencies();
    const controller = new RealtimeSessionController(dependencies, {
      mode: 'scene', sceneId: 'scene-1', voice: 'Harvey', model: 'model', speechSpeed: 'NATURAL',
    });
    const internal = controller as any;
    expect(() => internal.coordinateSceneTurn('answer')).toThrow('场景对话服务尚未配置');

    internal.scenePendingTranscript = { text: 'well, um', itemId: 'pending' };
    internal.scheduleSceneContinuation();
    internal.sceneCompletionPending = true;
    jest.runOnlyPendingTimers();
    await flushMicrotasks();
    internal.clearSceneContinuationTimer();

    internal.sceneTurnWithoutCapture = true;
    internal.sceneCompletionPending = false;
    internal.scheduleSceneAfterAudioDrain();
    internal.sceneTurnWithoutCapture = false;
    internal.sceneCompletionPending = true;
    internal.scheduleSceneAfterAudioDrain();
    internal.clearSceneAudioDrain();

    internal.machine.state = 'ready';
    internal.responseInFlight = true;
    await internal.enableSceneInputAfterRecordingStarts();
    jest.useRealTimers();
  });

  it('covers remaining reset, provider retry, capture, and scene observation failures', async () => {
    const resetDependencies = createDependencies();
    const resetController = new RealtimeSessionController(resetDependencies, {
      mode: 'free_chat', voice: 'Harvey', model: 'model', speechSpeed: 'NATURAL',
    });
    (resetController as any).machine.state = 'error';
    await resetController.start();
    expect(resetController.getSnapshot().state).toBe('connecting');

    const dependencies = createDependencies();
    dependencies.turnAudioCapture = {
      start: jest.fn(async () => { throw new Error('capture failed'); }),
      stop: jest.fn(() => true), take: jest.fn(async () => null), release: jest.fn(async () => undefined),
    };
    dependencies.sceneDialogue = {
      evaluateTurn: jest.fn(async () => { throw new Error('evaluation failed'); }),
      advanceState: jest.fn(async () => { throw new Error('state failed'); }),
      complete: jest.fn(),
    };
    const controller = new RealtimeSessionController(dependencies, {
      mode: 'scene', sceneId: 'scene-1', voice: 'Harvey', model: 'model', speechSpeed: 'NATURAL',
    });
    await controller.start();
    const internal = controller as any;
    internal.inputEnabled = false;
    await internal.applyProviderEvent({ type: 'user.speech.started' });
    await internal.applyProviderEvent({ type: 'user.speech.stopped' });
    await internal.applyProviderEvent({ type: 'user.transcript.delta', text: 'ignored' });
    await internal.applyProviderEvent({ type: 'user.transcript.preview', text: 'ignored' });

    internal.currentResponseRequest = { responseCreate: { type: 'response.create' } };
    internal.pendingResponseRequest = null;
    await internal.applyProviderEvent({ type: 'provider.error', message: 'conversation already has an active response' });
    expect(internal.pendingResponseRequest).not.toBeNull();

    internal.inputEnabled = true;
    await internal.beginTurnAudioCapture();
    internal.coordinateSceneTurn('observe this turn');
    await flushMicrotasks();
    expect(controller.getSnapshot().error).toBeNull();

    dependencies.sessionSocket.persistMessage.mockRejectedValueOnce(new Error('persist failed'));
    const freeController = new RealtimeSessionController(dependencies, {
      mode: 'free_chat', voice: 'Harvey', model: 'model', speechSpeed: 'NATURAL',
    });
    await expect((freeController as any).handleCompletedUserTranscript({ text: 'hello' }))
      .rejects.toThrow('persist failed');
  });
});
