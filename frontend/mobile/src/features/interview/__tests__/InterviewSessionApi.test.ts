import type { ApiRequestOptions } from '@/infrastructure/http/ApiClient';

import { File } from 'expo-file-system';

import {
  createInterviewWavFile,
  InterviewSessionApi,
} from '../InterviewSessionApi';

function createClient() {
  return {
    request: jest.fn<Promise<unknown>, [string, ApiRequestOptions?]>(
      async () => ({ status: 'PROCESSING' }),
    ),
  };
}

describe('InterviewSessionApi', () => {
  it('starts against interview-scenes with an encoded scene id and exact JSON DTO', async () => {
    const client = createClient();
    const api = new InterviewSessionApi(client, 'interview/scene 1');

    await api.startSession({
      offerSdp: 'offer-sdp',
      provider: 'QWEN',
      model: 'llmqiniu/gpt-5.6-luna',
      voice: 'Harvey',
      translationEnabled: true,
    });

    expect(client.request).toHaveBeenCalledWith(
      '/api/interview-scenes/interview%2Fscene%201/sessions',
      {
        method: 'POST',
        body: JSON.stringify({
          offerSdp: 'offer-sdp',
          provider: 'QWEN',
          model: 'llmqiniu/gpt-5.6-luna',
          voice: 'Harvey',
          translationEnabled: true,
        }),
        timeoutMs: 20_000,
      },
    );
  });

  it('submits transcript as multipart and appends an Expo WAV File when supplied', async () => {
    const client = createClient();
    const api = new InterviewSessionApi(client, 'scene/1');

    await api.submitTurn('session 1', 3, 'I led the migration.', 'file:///turn-3.wav');

    const [path, options] = client.request.mock.calls[0];
    expect(path).toBe(
      '/api/interview-scenes/scene%2F1/sessions/session%201/turns/3',
    );
    expect(options).toEqual(
      expect.objectContaining({ method: 'POST', timeoutMs: 30_000 }),
    );
    const form = options?.body as FormData;
    expect(form.get('transcript')).toBe('I led the migration.');
    expect(form.get('audio')).toBeTruthy();
    expect(createInterviewWavFile('file:///turn-3.wav')).toBeInstanceOf(File);
  });

  it('omits the audio part when no WAV is available', async () => {
    const client = createClient();
    const api = new InterviewSessionApi(client, 'scene/1');

    await api.submitTurn('session 1', 1, 'Hello');

    const form = client.request.mock.calls[0][1]?.body as FormData;
    expect(form.get('transcript')).toBe('Hello');
    expect(form.get('audio')).toBeNull();
  });

  it('uses the interview end/report/retry endpoints and report lifecycle timeouts', async () => {
    const client = createClient();
    const api = new InterviewSessionApi(client, 'scene/1');

    await api.end('session 1');
    await api.getReport('session 1');
    await api.retryReport('session 1');
    await api.listAssets();

    expect(client.request.mock.calls).toEqual([
      [
        '/api/interview-scenes/scene%2F1/sessions/session%201/end',
        { method: 'POST', timeoutMs: 25_000 },
      ],
      [
        '/api/interview-scenes/scene%2F1/sessions/session%201/report',
        { timeoutMs: 15_000 },
      ],
      [
        '/api/interview-scenes/scene%2F1/sessions/session%201/report/retry',
        { method: 'POST', timeoutMs: 30_000 },
      ],
      ['/api/interview-scenes/assets', { timeoutMs: 15_000 }],
    ]);
  });

  it('models all three report states as a discriminated union', () => {
    const processing: import('../InterviewSessionApi').InterviewReportResponse = {
      sessionId: 's', sceneId: 'c', status: 'PROCESSING', report: null, failureReason: null,
    };
    const completed: import('../InterviewSessionApi').InterviewReportResponse = {
      sessionId: 's', sceneId: 'c', status: 'COMPLETED',
      report: {
        sessionId: 's', sceneId: 'c', overallScore: 88, summary: 'Good', dimensions: [],
        completedAt: '2026-08-12T00:00:00Z',
      },
      failureReason: null,
    };
    const failed: import('../InterviewSessionApi').InterviewReportResponse = {
      sessionId: 's', sceneId: 'c', status: 'FAILED', report: null, failureReason: 'timeout',
    };

    expect([processing.status, completed.report.overallScore, failed.failureReason]).toEqual([
      'PROCESSING', 88, 'timeout',
    ]);
  });
});
