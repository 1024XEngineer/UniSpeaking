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

  it('normalizes invalid identities and uses dependency fallbacks', () => {
    const fetch = jest.fn<Promise<Response>, [RequestInfo | URL, RequestInit?]>(
      async () => { throw new Error('offline'); },
    );
    const client = new AnalyticsClient(
      { enabled: true, endpoint: '/events', websiteId: 'site', hostname: 'host' },
      { fetch },
    );
    client.setDistinctId('');
    client.trackPageView();
    client.setDistinctId('x'.repeat(51));
    client.trackLearningAsset({}, 'REPORT');
    client.setDistinctId(undefined);

    expect(payload({ init: fetch.mock.calls[0][1] }).id).toBeUndefined();
    expect(payload({ init: fetch.mock.calls[0][1] })).toMatchObject({
      language: 'zh-CN', screen: 'mobile', url: '/',
    });
    expect(fetch.mock.calls[0][1]?.headers).toMatchObject({
      'User-Agent': 'UniSpeaking-Mobile/1.0',
    });
  });

  it('filters invalid contexts and unsafe event values', () => {
    const { client, calls } = createClient();
    client.trackModeSelection({ pageCode: 'missing-mode' });
    client.trackModeSelection({ mode: 'INVALID' as any, pageCode: 'invalid' });
    client.trackLearningAsset({ mode: 'SCENE', pageCode: 'x'.repeat(81) }, '');

    expect(calls).toHaveLength(1);
    expect(payload(calls[0])).toMatchObject({
      name: 'learning_asset_view',
      data: { mode: 'SCENE' },
    });
  });

  it('records failed and abandoned training while guarding duplicate terminal actions', () => {
    let current = 0;
    const calls: { init?: RequestInit }[] = [];
    const client = new AnalyticsClient(
      { enabled: true, endpoint: '/events', websiteId: 'site', hostname: 'host' },
      {
        fetch: jest.fn(async (_input, init) => {
          calls.push({ init });
          return new Response(null, { status: 200 });
        }),
        now: () => current,
      },
    );

    const invalid = client.training();
    invalid.attempt();
    invalid.started();
    invalid.fail();
    invalid.complete();
    invalid.abandon();

    const failed = client.training({ mode: 'INTERVIEW', pageCode: 'interview' });
    failed.fail('PROVIDER_ERROR');
    failed.fail();
    failed.started();

    const abandoned = client.training({ mode: 'IELTS', pageCode: 'ielts' });
    abandoned.attempt();
    abandoned.started();
    abandoned.started();
    current = 3_600;
    client.setAppVisible(false);
    client.setAppVisible(true);
    abandoned.abandon();
    abandoned.complete();
    abandoned.abandon();

    expect(calls.map(payload).map((item) => item.name)).toEqual([
      'training_start_failed',
      'training_start_attempt',
      'training_started',
      'training_abandoned',
    ]);
    expect(payload(calls[3]).data).toMatchObject({
      reason: 'USER_EXIT', effective_duration_seconds: 4,
    });
  });

  it('does not fail or abandon after a training timer has started', () => {
    const { client, calls } = createClient();
    const tracker = client.training({ mode: 'SCENE' });
    tracker.started();
    tracker.fail('LATE_FAILURE');
    tracker.attempt();
    tracker.complete();
    tracker.complete();
    expect(calls.map(payload).map((item) => item.name)).toEqual([
      'training_started', 'training_start_attempt', 'training_completed',
    ]);
  });
});
