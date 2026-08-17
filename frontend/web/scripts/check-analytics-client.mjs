import test from 'node:test'
import assert from 'node:assert/strict'

import { createActivityTimer } from '../src/analytics/activityTimer.js'
import { createAnalyticsClient } from '../src/analytics/analyticsClient.js'
import { pageForPath } from '../src/analytics/pageCatalog.js'

function recorder() {
  const events = []
  const identities = []
  return {
    events,
    identities,
    tracker: {
      identify: (id) => identities.push(id),
      track: (payload) => events.push(payload({
        hostname: 'unispeaking.qnsdk.com',
        title: 'Initial title',
        url: '/initial/private-path',
      })),
    },
  }
}

function eventTargetRecorder() {
  const listeners = new Map()
  return {
    addEventListener: (name, listener) => listeners.set(name, listener),
    dispatch: (name) => listeners.get(name)?.(),
  }
}

test('silently ignores events when analytics or the Umami tracker is unavailable', () => {
  assert.doesNotThrow(() => createAnalyticsClient({ enabled: false }).trackModeSelection({ mode: 'SCENE' }))
  assert.doesNotThrow(() => createAnalyticsClient({ enabled: true, tracker: () => null }).trackModeSelection({ mode: 'SCENE' }))
})

test('queues the initial page view until the Umami tracker is ready', () => {
  const calls = []
  const eventTarget = eventTargetRecorder()
  let tracker = null
  const client = createAnalyticsClient({
    enabled: true,
    tracker: () => tracker,
    eventTarget,
    schedule: () => undefined,
  })

  client.trackPageView('/conversation/private-session')
  client.trackModeSelection({ mode: 'FREE_CHAT', pageCode: 'conversation' }, 'sidebar')
  assert.deepEqual(calls, [])

  tracker = { track: (...args) => calls.push(args) }
  eventTarget.dispatch('load')

  assert.equal(calls.length, 2)
  assert.equal(typeof calls[0][0], 'function')
  assert.equal(typeof calls[1][0], 'function')
  assert.deepEqual(calls[1][0]({ url: '/private' }), {
    url: '/conversation/session',
    id: undefined,
    name: 'mode_selected',
    data: { mode: 'FREE_CHAT', page_code: 'conversation', source: 'sidebar' },
    title: 'UniSpeaking',
  })
})

test('tracks only approved primitive mode-selection properties', () => {
  const { events, tracker } = recorder()
  const client = createAnalyticsClient({ enabled: true, tracker: () => tracker })

  client.trackModeSelection({
    mode: 'INTERVIEW',
    pageCode: 'interview-training',
    email: 'person@example.com',
    sessionId: 'secret-session',
  }, 'main_navigation')

  assert.deepEqual(events, [{
    hostname: 'unispeaking.qnsdk.com',
    id: undefined,
    name: 'mode_selected',
    data: { mode: 'INTERVIEW', page_code: 'interview-training', source: 'main_navigation' },
    title: 'UniSpeaking',
    url: '/',
  }])
})

test('rejects unknown training modes', () => {
  const { events, tracker } = recorder()
  const client = createAnalyticsClient({ enabled: true, tracker: () => tracker })
  client.trackModeSelection({ mode: 'LEARNING_ASSET', pageCode: 'assets' })
  assert.deepEqual(events, [])
})

test('activity timer excludes paused and hidden time', () => {
  let current = 0
  const timer = createActivityTimer({ now: () => current })
  timer.start()
  current = 4_000
  timer.pause()
  current = 14_000
  timer.resume()
  current = 19_000
  timer.setVisible(false)
  current = 29_000
  timer.setVisible(true)
  current = 32_000
  assert.equal(timer.stop(), 12)
})

test('emits terminal training duration without heartbeat or identifiers', () => {
  let current = 0
  const { events, tracker } = recorder()
  const training = createAnalyticsClient({ enabled: true, tracker: () => tracker, now: () => current })
    .training({ mode: 'FREE_CHAT', pageCode: 'conversation', userId: 'private-user' })

  training.attempt({ token: 'private-token' })
  training.started({ sessionId: 'private-session' })
  current = 7_400
  training.heartbeat()
  training.complete({ transcript: 'private-transcript' })

  assert.deepEqual(events.map(({ name, data }) => ({ name, data })), [
    { name: 'training_start_attempt', data: { mode: 'FREE_CHAT', page_code: 'conversation' } },
    { name: 'training_started', data: { mode: 'FREE_CHAT', page_code: 'conversation' } },
    { name: 'training_completed', data: { mode: 'FREE_CHAT', page_code: 'conversation', effective_duration_seconds: 7 } },
  ])
})

test('distinguishes failed starts from abandoned active training', () => {
  let current = 0
  const { events, tracker } = recorder()
  const client = createAnalyticsClient({ enabled: true, tracker: () => tracker, now: () => current })
  const failed = client.training({ mode: 'IELTS', pageCode: 'ielts-training' })
  failed.attempt()
  failed.fail('REALTIME_ERROR')

  const abandoned = client.training({ mode: 'SCENE', pageCode: 'scene-training' })
  abandoned.attempt()
  abandoned.started()
  current = 3_600
  abandoned.abandon('USER_EXIT')

  assert.deepEqual(events.map(({ name, data }) => ({ name, data })), [
    { name: 'training_start_attempt', data: { mode: 'IELTS', page_code: 'ielts-training' } },
    { name: 'training_start_failed', data: { mode: 'IELTS', page_code: 'ielts-training', reason: 'REALTIME_ERROR' } },
    { name: 'training_start_attempt', data: { mode: 'SCENE', page_code: 'scene-training' } },
    { name: 'training_started', data: { mode: 'SCENE', page_code: 'scene-training' } },
    { name: 'training_abandoned', data: { mode: 'SCENE', page_code: 'scene-training', reason: 'USER_EXIT', effective_duration_seconds: 4 } },
  ])
})

test('tracks learning assets separately from training modes', () => {
  const { events, tracker } = recorder()
  const client = createAnalyticsClient({ enabled: true, tracker: () => tracker })
  client.trackLearningAsset({ pageCode: 'interview-assets', mode: 'INTERVIEW' }, 'REPORT')
  assert.deepEqual(events.map(({ name, data }) => ({ name, data })), [{
    name: 'learning_asset_view',
    data: { mode: 'INTERVIEW', page_code: 'interview-assets', asset_type: 'REPORT' },
  }])
})

test('uses and clears the authenticated user UUID as the Umami Distinct ID', () => {
  const { events, identities, tracker } = recorder()
  const client = createAnalyticsClient({ enabled: true, tracker: () => tracker })

  client.setDistinctId('c8ca76c6-ea4b-46e8-aaf1-848d074d54ec')
  client.trackModeSelection({ mode: 'SCENE', pageCode: 'scene-training' })
  client.setDistinctId(null)
  client.trackModeSelection({ mode: 'FREE_CHAT', pageCode: 'conversation' })

  assert.deepEqual(identities, ['c8ca76c6-ea4b-46e8-aaf1-848d074d54ec', ''])
  assert.equal(events[0].id, 'c8ca76c6-ea4b-46e8-aaf1-848d074d54ec')
  assert.equal(events[1].id, undefined)
})

test('attributes custom events to the latest normalized page URL', () => {
  const { events, tracker } = recorder()
  const client = createAnalyticsClient({ enabled: true, tracker: () => tracker })

  client.trackPageView('/scenes/private-scene/session/private-session')
  client.trackModeSelection({ mode: 'SCENE', pageCode: 'scene-training' })

  assert.equal(events[1].url, '/scenes/session')
  assert.equal(events[1].name, 'mode_selected')
})

test('reports page views with normalized paths and without route identifiers or query strings', () => {
  const calls = []
  const client = createAnalyticsClient({
    enabled: true,
    tracker: () => ({ track: (payload) => calls.push(payload) }),
  })

  client.trackPageView('/conversation/private-session?token=private')
  client.trackPageView('/scenes/private-scene/session/private-session/result')
  client.trackPageView('/interview/scenes/private-scene/session/private-session/report')
  client.trackPageView('/ielts/part2/private-topic/session?answer=private')

  assert.deepEqual(calls.map((createPayload) => createPayload({
    hostname: 'unispeaking.qnsdk.com',
    language: 'zh-CN',
    referrer: '',
    screen: '1440x900',
    title: 'Private title',
    url: '/private/path?token=private',
    website: 'public-website-id',
  })).map(({ id: _id, ...payload }) => payload), [
    {
      hostname: 'unispeaking.qnsdk.com',
      language: 'zh-CN',
      referrer: '',
      screen: '1440x900',
      title: 'UniSpeaking',
      url: '/conversation/session',
      website: 'public-website-id',
    },
    {
      hostname: 'unispeaking.qnsdk.com',
      language: 'zh-CN',
      referrer: '',
      screen: '1440x900',
      title: 'UniSpeaking',
      url: '/scenes/session/result',
      website: 'public-website-id',
    },
    {
      hostname: 'unispeaking.qnsdk.com',
      language: 'zh-CN',
      referrer: '',
      screen: '1440x900',
      title: 'UniSpeaking',
      url: '/interview/session/report',
      website: 'public-website-id',
    },
    {
      hostname: 'unispeaking.qnsdk.com',
      language: 'zh-CN',
      referrer: '',
      screen: '1440x900',
      title: 'UniSpeaking',
      url: '/ielts/part2/selection/session',
      website: 'public-website-id',
    },
  ])
})

test('maps production routes to stable page and mode codes', () => {
  assert.deepEqual(pageForPath('/conversation'), { pageCode: 'conversation', mode: 'FREE_CHAT' })
  assert.deepEqual(pageForPath('/scenes'), { pageCode: 'scene-training', mode: 'SCENE' })
  assert.deepEqual(pageForPath('/ielts/session'), { pageCode: 'ielts-training', mode: 'IELTS' })
  assert.deepEqual(pageForPath('/interview/assets'), { pageCode: 'interview-assets', mode: 'INTERVIEW', assetType: 'REPORT' })
})
