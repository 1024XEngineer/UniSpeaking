import { AnalyticsClient } from '../AnalyticsClient';

function createClient() {
  const calls: { input: RequestInfo | URL; init?: RequestInit }[] = [];
  const client = new AnalyticsClient(
    {
      enabled: true,
      endpoint: 'https://cloud.umami.is/api/send',
      websiteId: '3ae2dee9-d585-43a9-93f3-fcafcd14b258',
      hostname: 'unispeaking.qnsdk.com',
    },
    {
      fetch: jest.fn(async (input, init) => {
        calls.push({ input, init });
        return new Response(null, { status: 200 });
      }),
      language: () => 'zh-CN',
      screen: () => '390x844',
      userAgent: () => 'UniSpeaking-Mobile/1.0 (ios)',
    },
  );
  return { client, calls };
}

function payload(call: { init?: RequestInit }) {
  return JSON.parse(String(call.init?.body)).payload;
}

describe('AnalyticsClient', () => {
  it('sends Umami events with the shared Website ID and Distinct ID', () => {
    const { client, calls } = createClient();

    client.setDistinctId('c8ca76c6-ea4b-46e8-aaf1-848d074d54ec');
    client.trackPageView('/scenes/private-scene/training?stage=speak');
    client.trackModeSelection({ mode: 'SCENE', pageCode: 'scene-training' });

    expect(calls).toHaveLength(2);
    expect(payload(calls[0])).toMatchObject({
      website: '3ae2dee9-d585-43a9-93f3-fcafcd14b258',
      hostname: 'unispeaking.qnsdk.com',
      id: 'c8ca76c6-ea4b-46e8-aaf1-848d074d54ec',
      url: '/scenes/session',
    });
    expect(payload(calls[1])).toMatchObject({
      name: 'mode_selected',
      data: { mode: 'SCENE', page_code: 'scene-training', source: 'navigation' },
      url: '/scenes/session',
    });
    expect(calls[1].init?.headers).toMatchObject({
      'Content-Type': 'application/json',
      'User-Agent': 'UniSpeaking-Mobile/1.0 (ios)',
    });
  });

  it('records training lifecycle duration without private identifiers in event data', () => {
    let current = 0;
    const calls: { init?: RequestInit }[] = [];
    const client = new AnalyticsClient(
      {
        enabled: true,
        endpoint: 'https://cloud.umami.is/api/send',
        websiteId: 'website-id',
        hostname: 'unispeaking.qnsdk.com',
      },
      {
        fetch: jest.fn(async (_input, init) => {
          calls.push({ init });
          return new Response(null, { status: 200 });
        }),
        now: () => current,
      },
    );

    const training = client.training({ mode: 'FREE_CHAT', pageCode: 'conversation' });
    training.attempt();
    training.started();
    current = 7_400;
    training.complete();

    expect(calls.map(payload).map(({ name, data }) => ({ name, data }))).toEqual([
      { name: 'training_start_attempt', data: { mode: 'FREE_CHAT', page_code: 'conversation' } },
      { name: 'training_started', data: { mode: 'FREE_CHAT', page_code: 'conversation' } },
      { name: 'training_completed', data: { mode: 'FREE_CHAT', page_code: 'conversation', effective_duration_seconds: 7 } },
    ]);
  });

  it('silently ignores analytics when disabled', () => {
    const fetch = jest.fn();
    const client = new AnalyticsClient(
      { enabled: false, endpoint: 'https://cloud.umami.is/api/send', websiteId: '', hostname: 'unispeaking.qnsdk.com' },
      { fetch },
    );

    client.trackPageView('/conversation');
    client.trackModeSelection({ mode: 'FREE_CHAT', pageCode: 'conversation' });

    expect(fetch).not.toHaveBeenCalled();
  });
});
