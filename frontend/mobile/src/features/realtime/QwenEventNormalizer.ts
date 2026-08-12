import type { RealtimeDomainEvent } from './types';

type JsonRecord = Record<string, unknown>;

function isRecord(value: unknown): value is JsonRecord {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function nonEmptyString(value: unknown) {
  return typeof value === 'string' && value.trim().length > 0 ? value : undefined;
}

function providerItemId(raw: JsonRecord) {
  const item = isRecord(raw.item) ? raw.item : undefined;
  return (
    nonEmptyString(raw.item_id) ??
    nonEmptyString(item?.id) ??
    nonEmptyString(raw.event_id)
  );
}

function assistantOutput(raw: JsonRecord) {
  const response = isRecord(raw.response) ? raw.response : undefined;
  const output = Array.isArray(response?.output) ? response.output : [];
  const item = output.find(
    (candidate) =>
      isRecord(candidate) &&
      candidate.role === 'assistant' &&
      Array.isArray(candidate.content),
  );
  if (!isRecord(item) || !Array.isArray(item.content)) return null;
  const text = item.content
    .map((part) => {
      if (!isRecord(part)) return '';
      return nonEmptyString(part.transcript) ?? nonEmptyString(part.text) ?? '';
    })
    .join('')
    .trim();
  if (!text) return null;
  return {
    itemId:
      nonEmptyString(item.id) ??
      nonEmptyString(response?.id) ??
      nonEmptyString(raw.event_id),
    text,
  };
}

export function normalizeQwenEvent(raw: unknown): RealtimeDomainEvent[] {
  if (!isRecord(raw)) return [];
  const type = nonEmptyString(raw.type);

  switch (type) {
    case 'session.created':
      return [{
        type: 'session.created',
        providerSessionId: isRecord(raw.session)
          ? nonEmptyString(raw.session.id)
          : undefined,
      }];
    case 'session.updated':
      return [{ type: 'session.updated' }];
    case 'input_audio_buffer.speech_started':
      return [{ type: 'user.speech.started' }];
    case 'input_audio_buffer.speech_stopped':
      return [{ type: 'user.speech.stopped' }];
    case 'conversation.item.input_audio_transcription.delta': {
      const preview = `${nonEmptyString(raw.text) ?? ''}${nonEmptyString(raw.stash) ?? ''}`;
      const previewText = nonEmptyString(preview);
      if (previewText) {
        return [
          {
            type: 'user.transcript.preview',
            itemId: providerItemId(raw),
            text: previewText,
          },
        ];
      }
      const delta = nonEmptyString(raw.delta);
      if (!delta) return [];
      return [
        {
          type: 'user.transcript.delta',
          itemId: providerItemId(raw),
          text: delta,
        },
      ];
    }
    case 'conversation.item.input_audio_transcription.text': {
      const text = `${nonEmptyString(raw.text) ?? ''}${nonEmptyString(raw.stash) ?? ''}`;
      const preview = nonEmptyString(text) ?? nonEmptyString(raw.delta);
      if (!preview) return [];
      return [
        {
          type: 'user.transcript.preview',
          itemId: providerItemId(raw),
          text: preview,
        },
      ];
    }
    case 'conversation.item.input_audio_transcription.completed': {
      const text = nonEmptyString(raw.transcript) ?? nonEmptyString(raw.text);
      if (!text) return [];
      return [
        {
          type: 'user.transcript.completed',
          itemId: providerItemId(raw),
          text,
        },
      ];
    }
    case 'response.created':
      return [{ type: 'assistant.response.started' }];
    case 'response.audio_transcript.delta':
    case 'response.text.delta': {
      const text = nonEmptyString(raw.delta);
      return text ? [{ type: 'assistant.transcript.delta', text }] : [];
    }
    case 'response.audio_transcript.done':
    case 'response.text.done': {
      const text = nonEmptyString(raw.transcript) ?? nonEmptyString(raw.text);
      if (!text) return [];
      return [
        {
          type: 'assistant.transcript.completed',
          itemId:
            nonEmptyString(raw.item_id) ??
            nonEmptyString(raw.response_id) ??
            nonEmptyString(raw.event_id),
          text,
        },
      ];
    }
    case 'response.done': {
      const response = isRecord(raw.response) ? raw.response : {};
      const completed: RealtimeDomainEvent[] = [];
      const output = assistantOutput(raw);
      if (output) {
        completed.push({ type: 'assistant.transcript.completed', ...output });
      }
      completed.push({
        type: 'assistant.response.completed',
        cancelled: response.status === 'cancelled',
      });
      return completed;
    }
    case 'error': {
      const error = isRecord(raw.error) ? raw.error : {};
      return [
        {
          type: 'provider.error',
          code: nonEmptyString(error.code),
          message:
            nonEmptyString(error.message) ??
            nonEmptyString(raw.message) ??
            'Realtime provider returned an error.',
        },
      ];
    }
    default:
      return [];
  }
}
