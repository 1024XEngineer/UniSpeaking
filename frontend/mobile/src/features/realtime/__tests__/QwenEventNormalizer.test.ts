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

  it('covers missing fields and every provider item id fallback', () => {
    expect(normalizeQwenEvent(null)).toEqual([]);
    expect(normalizeQwenEvent([])).toEqual([]);
    expect(normalizeQwenEvent({})).toEqual([]);
    expect(normalizeQwenEvent({ type: 'session.created', session: 'invalid' })).toEqual([
      { type: 'session.created', providerSessionId: undefined },
    ]);
    expect(normalizeQwenEvent({
      type: 'conversation.item.input_audio_transcription.completed',
      item: { id: 'nested-item' },
      text: 'Nested id',
    })).toEqual([{ type: 'user.transcript.completed', itemId: 'nested-item', text: 'Nested id' }]);
    expect(normalizeQwenEvent({
      type: 'conversation.item.input_audio_transcription.completed',
      event_id: 'event-item',
      text: 'Event id',
    })).toEqual([{ type: 'user.transcript.completed', itemId: 'event-item', text: 'Event id' }]);
    expect(normalizeQwenEvent({
      type: 'conversation.item.input_audio_transcription.completed',
      transcript: 'No id',
    })).toEqual([{ type: 'user.transcript.completed', itemId: undefined, text: 'No id' }]);
  });

  it('ignores empty transcript variants and accepts each preview fallback', () => {
    expect(normalizeQwenEvent({ type: 'conversation.item.input_audio_transcription.completed' })).toEqual([]);
    expect(normalizeQwenEvent({ type: 'conversation.item.input_audio_transcription.delta' })).toEqual([]);
    expect(normalizeQwenEvent({
      type: 'conversation.item.input_audio_transcription.text',
      delta: 'fallback preview',
    })).toEqual([{ type: 'user.transcript.preview', itemId: undefined, text: 'fallback preview' }]);
    expect(normalizeQwenEvent({ type: 'conversation.item.input_audio_transcription.text' })).toEqual([]);
    expect(normalizeQwenEvent({ type: 'response.audio.delta' })).toEqual([]);
    expect(normalizeQwenEvent({ type: 'response.text.delta' })).toEqual([]);
    expect(normalizeQwenEvent({ type: 'response.text.done' })).toEqual([]);
  });

  it('normalizes response lifecycle, audio and all completion id fallbacks', () => {
    expect(normalizeQwenEvent({ type: 'response.created' })).toEqual([
      { type: 'assistant.response.started' },
    ]);
    expect(normalizeQwenEvent({ type: 'response.audio.delta', delta: 'AQI=' })).toEqual([
      { type: 'assistant.audio.delta', audio: 'AQI=' },
    ]);
    expect(normalizeQwenEvent({ type: 'response.text.delta', delta: 'hello' })).toEqual([
      { type: 'assistant.transcript.delta', text: 'hello' },
    ]);
    expect(normalizeQwenEvent({
      type: 'response.text.done', response_id: 'response-id', text: 'Done',
    })).toEqual([{ type: 'assistant.transcript.completed', itemId: 'response-id', text: 'Done' }]);
    expect(normalizeQwenEvent({
      type: 'response.text.done', event_id: 'event-id', text: 'Done again',
    })).toEqual([{ type: 'assistant.transcript.completed', itemId: 'event-id', text: 'Done again' }]);
    expect(normalizeQwenEvent({ type: 'response.done', response: { status: 'cancelled' } })).toEqual([
      { type: 'assistant.response.completed', cancelled: true },
    ]);
  });

  it('extracts response output text fallbacks and ignores malformed output parts', () => {
    expect(normalizeQwenEvent({
      type: 'response.done',
      event_id: 'outer-event',
      response: {
        id: 'response-fallback',
        output: [
          null,
          { role: 'user', content: [{ text: 'ignored' }] },
          { role: 'assistant', content: [null, { text: 'Text ' }, { transcript: 'transcript' }] },
        ],
      },
    })).toEqual([
      { type: 'assistant.transcript.completed', itemId: 'response-fallback', text: 'Text transcript' },
      { type: 'assistant.response.completed', cancelled: false },
    ]);
    expect(normalizeQwenEvent({
      type: 'response.done',
      event_id: 'outer-event',
      response: { output: [{ role: 'assistant', content: [{ text: 'From event' }] }] },
    })[0]).toEqual({ type: 'assistant.transcript.completed', itemId: 'outer-event', text: 'From event' });
    expect(normalizeQwenEvent({
      type: 'response.done', response: { output: [{ role: 'assistant', content: [] }] },
    })).toEqual([{ type: 'assistant.response.completed', cancelled: false }]);
    expect(normalizeQwenEvent({ type: 'response.done', response: 'invalid' })).toEqual([
      { type: 'assistant.response.completed', cancelled: false },
    ]);
  });

  it('uses provider error message fallbacks', () => {
    expect(normalizeQwenEvent({ type: 'error', error: 'invalid', message: 'outer message' })).toEqual([
      { type: 'provider.error', code: undefined, message: 'outer message' },
    ]);
    expect(normalizeQwenEvent({ type: 'error' })).toEqual([
      { type: 'provider.error', code: undefined, message: 'Realtime provider returned an error.' },
    ]);
  });
});
