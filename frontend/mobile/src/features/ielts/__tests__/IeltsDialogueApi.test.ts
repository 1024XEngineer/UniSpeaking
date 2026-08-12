import { IeltsDialogueApi } from '../IeltsDialogueApi';

describe('IeltsDialogueApi', () => {
  it('advances dialogue state for a turn', async () => {
    const client = { request: jest.fn(async () => ({ completed: false })) };
    const api = new IeltsDialogueApi(client, 'ielts-1');

    await api.advanceState('session-1', 2, false);

    expect(client.request).toHaveBeenCalledWith(
      '/api/ielts/ielts-1/sessions/session-1/turns/2/state',
      { method: 'POST' },
    );
  });

  it('advances dialogue state with timeout flag', async () => {
    const client = { request: jest.fn(async () => ({ completed: true })) };
    const api = new IeltsDialogueApi(client, 'ielts-1');

    await api.advanceState('session-1', 3, true);

    expect(client.request).toHaveBeenCalledWith(
      '/api/ielts/ielts-1/sessions/session-1/turns/3/state?timedOut=true',
      { method: 'POST' },
    );
  });

  it('posts part2 state transitions', async () => {
    const client = { request: jest.fn(async () => ({ phase: 'LONG_TURN' })) };
    const api = new IeltsDialogueApi(client, 'ielts-1');

    await api.advancePart2State('session-1', 'PREPARATION_COMPLETE');

    expect(client.request).toHaveBeenCalledWith(
      '/api/ielts/ielts-1/sessions/session-1/part2/state',
      {
        method: 'POST',
        body: JSON.stringify({ event: 'PREPARATION_COMPLETE' }),
      },
    );
  });

  it('evaluates a learner turn with transcript only', async () => {
    const request = jest.fn<Promise<unknown>, [string, { method?: string; body?: FormData }?]>(
      async () => ({ score: 7 }),
    );
    const client = { request };
    const api = new IeltsDialogueApi(client, 'ielts-1');

    await api.evaluateTurn('session-1', 1, 'My hometown is Shanghai.');

    expect(client.request).toHaveBeenCalledWith(
      '/api/ielts/ielts-1/sessions/session-1/turns/1/evaluation',
      expect.objectContaining({ method: 'POST' }),
    );
    const body = request.mock.calls[0][1]?.body as FormData;
    expect(body.get('transcript')).toBe('My hometown is Shanghai.');
  });

  it('loads dialogue and part2 state for recovery', async () => {
    const client = { request: jest.fn(async () => ({ phase: 'LONG_TURN' })) };
    const api = new IeltsDialogueApi(client, 'ielts-1');

    await api.getDialogueState('session-1');
    await api.getPart2State('session-1');

    expect(client.request).toHaveBeenNthCalledWith(
      1,
      '/api/ielts/ielts-1/sessions/session-1/state',
    );
    expect(client.request).toHaveBeenNthCalledWith(
      2,
      '/api/ielts/ielts-1/sessions/session-1/part2/state',
    );
  });
});
