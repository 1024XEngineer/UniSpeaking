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
    takeTurn: jest.fn(async (turnNo: number) => ({ uri: `turn-${turnNo}.wav`, name: `turn-${turnNo}.wav`, size: 1, durationMs: 1 })),
    discard: jest.fn(), close: jest.fn(async () => { calls.push('recorder.close'); }),
  };
  const sessionApi = {
    startSession: jest.fn(async () => ({ sessionId: 'session-1', answerSdp: 'answer', voiceId: 'voice', systemPrompt: 'prompt' } as any)),
    submitTurn: jest.fn(async () => ({ state: { shouldEnd: true, completedTopicCount: 1, coveredTopicCount: 1, currentTopic: 'done', controlInstruction: 'next' }, reportStatus: 'PROCESSING' as const })),
    end: jest.fn(async () => ({ sessionId: 'session-1', reportStatus: 'PROCESSING' as const })),
  };
  const sessionSocket = { connect: jest.fn(async () => undefined), persistMessage: jest.fn(async () => undefined), close: jest.fn() };
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
    await provider(test, { type: 'session.created' });
    await provider(test, { type: 'session.updated' });
    expect(test.transport.sendProviderEvent).toHaveBeenLastCalledWith(expect.objectContaining({ type: 'response.create' }));
    expect(test.transport.setAudioEnabled).toHaveBeenLastCalledWith(false);
    await provider(test, { type: 'response.done', response: { status: 'completed' } });
    expect(test.transport.setAudioEnabled).toHaveBeenLastCalledWith(true);
    expect(test.controller.getSnapshot().state).toBe('active');
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
});
