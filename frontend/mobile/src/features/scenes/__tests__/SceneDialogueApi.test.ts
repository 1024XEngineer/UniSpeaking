import type { ApiRequestOptions } from '@/infrastructure/http/ApiClient';

import { SceneDialogueApi } from '../SceneDialogueApi';

function createClient() {
  return {
    request: jest.fn<Promise<unknown>, [string, ApiRequestOptions?]>(
      async () => ({ completed: false }),
    ),
  };
}

describe('SceneDialogueApi', () => {
  it('advances state and evaluates each learner turn on encoded scene/session paths', async () => {
    const client = createClient();
    const api = new SceneDialogueApi(client, 'scene/1');

    await api.advanceState('session 1', 2, 'I need a window seat.');
    await api.evaluateTurn('session 1', 2, 'I need a window seat.', 'file:///turn.wav');

    expect(client.request.mock.calls[0]).toEqual([
      '/api/custom-scenes/scene%2F1/sessions/session%201/turns/2/state',
      {
        method: 'POST',
        body: JSON.stringify({ transcript: 'I need a window seat.' }),
      },
    ]);
    const [evaluationPath, evaluationOptions] = client.request.mock.calls[1];
    expect(evaluationPath).toBe(
      '/api/custom-scenes/scene%2F1/sessions/session%201/turns/2/evaluation',
    );
    expect(evaluationOptions).toEqual(
      expect.objectContaining({ method: 'POST', body: expect.any(FormData) }),
    );
    expect((evaluationOptions?.body as FormData).get('transcript')).toBe(
      'I need a window seat.',
    );
    expect((evaluationOptions?.body as FormData).get('audio')).toBeTruthy();
  });

  it('completes a dialogue with the stop time and can recover its report', async () => {
    const client = createClient();
    const api = new SceneDialogueApi(client, 'scene/1');

    await api.complete('session 1', '2026-08-05T10:00:00.000Z');
    await api.getReport('session 1');

    expect(client.request.mock.calls).toEqual([
      [
        '/api/custom-scenes/scene%2F1/sessions/session%201/complete',
        {
          method: 'POST',
          body: JSON.stringify({ stopTime: '2026-08-05T10:00:00.000Z' }),
          timeoutMs: 90_000,
        },
      ],
      [
        '/api/custom-scenes/scene%2F1/sessions/session%201/evaluation',
        undefined,
      ],
    ]);
  });
});
