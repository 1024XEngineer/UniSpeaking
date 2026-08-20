import test from 'node:test'
import assert from 'node:assert/strict'

import { injectUmamiScript, resolveUmamiConfig } from '../src/analytics/umamiConfig.js'

const validEnv = {
  VITE_UMAMI_ENABLED: 'true',
  VITE_UMAMI_SCRIPT_URL: 'https://unispeaking.qnsdk.com/analytics/script.js',
  VITE_UMAMI_WEBSITE_ID: '395f9c94-9165-49c7-9db1-f2d646a15268',
  VITE_UMAMI_DOMAINS: 'unispeaking.qnsdk.com',
}

test('disables Umami when required production variables are missing', () => {
  assert.deepEqual(resolveUmamiConfig({ VITE_UMAMI_ENABLED: 'true' }), { enabled: false })
})

test('accepts a complete self-hosted Umami configuration', () => {
  assert.deepEqual(resolveUmamiConfig(validEnv), {
    enabled: true,
    scriptUrl: 'https://unispeaking.qnsdk.com/analytics/script.js',
    websiteId: '395f9c94-9165-49c7-9db1-f2d646a15268',
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

  assert.match(once, /<script defer src="https:\/\/unispeaking\.qnsdk\.com\/analytics\/script\.js"/)
  assert.match(once, /data-website-id="395f9c94-9165-49c7-9db1-f2d646a15268"/)
  assert.match(once, /data-domains="unispeaking\.qnsdk\.com"/)
  assert.match(once, /data-auto-track="false"/)
  assert.equal((twice.match(/data-umami-tracker/g) || []).length, 1)
})

test('leaves HTML unchanged when analytics is disabled', () => {
  const html = '<html><head></head><body></body></html>'
  assert.equal(injectUmamiScript(html, {}), html)
})
