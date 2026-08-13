import test from 'node:test'
import assert from 'node:assert/strict'

import { injectUmamiScript, resolveUmamiConfig } from '../src/analytics/umamiConfig.js'

const validEnv = {
  VITE_UMAMI_ENABLED: 'true',
  VITE_UMAMI_SCRIPT_URL: 'https://cloud.umami.is/script.js',
  VITE_UMAMI_WEBSITE_ID: '3ae2dee9-d585-43a9-93f3-fcafcd14b258',
  VITE_UMAMI_DOMAINS: 'unispeaking.qnsdk.com',
}

test('disables Umami when required production variables are missing', () => {
  assert.deepEqual(resolveUmamiConfig({ VITE_UMAMI_ENABLED: 'true' }), { enabled: false })
})

test('accepts a complete HTTPS Umami Cloud configuration', () => {
  assert.deepEqual(resolveUmamiConfig(validEnv), {
    enabled: true,
    scriptUrl: 'https://cloud.umami.is/script.js',
    websiteId: '3ae2dee9-d585-43a9-93f3-fcafcd14b258',
    domains: 'unispeaking.qnsdk.com',
  })
})

test('rejects non-HTTPS tracker URLs', () => {
  assert.deepEqual(resolveUmamiConfig({ ...validEnv, VITE_UMAMI_SCRIPT_URL: 'http://example.com/script.js' }), { enabled: false })
})

test('injects one deferred tracker with website and domain restrictions', () => {
  const html = '<html><head><title>UniSpeaking</title></head><body></body></html>'
  const once = injectUmamiScript(html, validEnv)
  const twice = injectUmamiScript(once, validEnv)

  assert.match(once, /<script defer src="https:\/\/cloud\.umami\.is\/script\.js"/)
  assert.match(once, /data-website-id="3ae2dee9-d585-43a9-93f3-fcafcd14b258"/)
  assert.match(once, /data-domains="unispeaking\.qnsdk\.com"/)
  assert.match(once, /data-auto-track="false"/)
  assert.equal((twice.match(/data-umami-tracker/g) || []).length, 1)
})

test('leaves HTML unchanged when analytics is disabled', () => {
  const html = '<html><head></head><body></body></html>'
  assert.equal(injectUmamiScript(html, {}), html)
})
