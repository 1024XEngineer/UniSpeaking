import { normalizeQwenEvent } from '../QwenEventNormalizer';

describe('normalizeQwenEvent', () => {
  it('normalizes session and speech lifecycle events', () => {
    expect(normalizeQwenEvent({ type: 'session.created', session: { id: 'provider-1' } })).toEqual([
      { type: 'session.created', providerSessionId: 'provider-1' },
    ]);
    expect(normalizeQwenEvent({ type: 'session.updated' })).toEqual([
      { type: 'session.updated' },
    ]);
    expect(normalizeQwenEvent({ type: 'input_audio_buffer.speech_started' })).toEqual([
      { type: 'user.speech.started' },
    ]);
    expect(normalizeQwenEvent({ type: 'input_audio_buffer.speech_stopped' })).toEqual([
      { type: 'user.speech.stopped' },
    ]);
  });

  it('normalizes the completed user transcript with its provider item id', () => {
    expect(
      normalizeQwenEvent({
        type: 'conversation.item.input_audio_transcription.completed',
        item_id: 'user-item-1',
        transcript: 'I would like a latte.',
      }),
    ).toEqual([
      {
        type: 'user.transcript.completed',
        itemId: 'user-item-1',
        text: 'I would like a latte.',
      },
    ]);
  });

  it('normalizes live user transcript delta and text preview events', () => {
    expect(
      normalizeQwenEvent({
        type: 'conversation.item.input_audio_transcription.delta',
        item_id: 'user-item-live',
        delta: 'I would ',
      }),
    ).toEqual([
      {
        type: 'user.transcript.delta',
        itemId: 'user-item-live',
        text: 'I would ',
      },
    ]);
    expect(
      normalizeQwenEvent({
        type: 'conversation.item.input_audio_transcription.delta',
        item_id: 'user-item-live',
        text: 'I would like',
        stash: ' a latte',
      }),
    ).toEqual([
      {
        type: 'user.transcript.preview',
        itemId: 'user-item-live',
        text: 'I would like a latte',
      },
    ]);
    expect(
      normalizeQwenEvent({
        type: 'conversation.item.input_audio_transcription.text',
        item_id: 'user-item-live',
        text: 'I would like',
        stash: ' a latte',
      }),
    ).toEqual([
      {
        type: 'user.transcript.preview',
        itemId: 'user-item-live',
        text: 'I would like a latte',
      },
    ]);
  });

  it('normalizes assistant transcript deltas and direct completion events', () => {
    expect(
      normalizeQwenEvent({ type: 'response.audio_transcript.delta', delta: 'Good ' }),
    ).toEqual([{ type: 'assistant.transcript.delta', text: 'Good ' }]);
    expect(
      normalizeQwenEvent({
        type: 'response.audio_transcript.done',
        item_id: 'assistant-item-1',
        transcript: 'Good morning!',
      }),
    ).toEqual([
      {
        type: 'assistant.transcript.completed',
        itemId: 'assistant-item-1',
        text: 'Good morning!',
      },
    ]);
  });

  it('extracts an assistant completion nested in response.done output', () => {
    expect(
      normalizeQwenEvent({
        type: 'response.done',
        response: {
          id: 'response-1',
          status: 'completed',
          output: [
            {
              id: 'assistant-item-2',
              role: 'assistant',
              content: [{ transcript: 'What would you like to drink?' }],
            },
          ],
        },
      }),
    ).toEqual([
      {
        type: 'assistant.transcript.completed',
        itemId: 'assistant-item-2',
        text: 'What would you like to drink?',
      },
      { type: 'assistant.response.completed', cancelled: false },
    ]);
  });

  it('normalizes provider errors and ignores malformed payloads', () => {
    expect(
      normalizeQwenEvent({
        type: 'error',
        error: { code: 'provider_busy', message: 'Please retry later.' },
      }),
    ).toEqual([
      {
        type: 'provider.error',
        code: 'provider_busy',
        message: 'Please retry later.',
      },
    ]);
    expect(normalizeQwenEvent('not-an-object')).toEqual([]);
    expect(normalizeQwenEvent({ type: 'unknown.event' })).toEqual([]);
  });
});
