# Umami Cloud Web Analytics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add privacy-scoped Umami Cloud page and learning-behavior analytics to the latest UniSpeaking Web frontend deployed at `unispeaking.qnsdk.com`.

**Architecture:** Vite injects the Umami script only when production analytics variables are valid. Business components call a platform-neutral analytics client that filters event properties and delegates to `window.umami.track`; page tracking remains owned by the Umami SPA tracker. Existing realtime and authentication flows remain authoritative and analytics failures are ignored.

**Tech Stack:** React 19, Vite 6, browser JavaScript, Node test runner, Umami Cloud tracker, Docker Compose.

---

### Task 1: Umami script configuration

**Files:**
- Create: `frontend/web/src/analytics/umamiConfig.js`
- Create: `frontend/web/scripts/check-umami-config.mjs`
- Modify: `frontend/web/index.html`
- Modify: `frontend/web/vite.config.mjs`
- Modify: `frontend/web/package.json`

- [ ] **Step 1: Write failing configuration tests**

Test that analytics is disabled without all required variables, enabled with a valid HTTPS script URL and Website ID, emits the production domain restriction, and rejects non-HTTPS production script URLs.

- [ ] **Step 2: Verify RED**

Run: `node --test scripts/check-umami-config.mjs`

Expected: failure because `umamiConfig.js` does not exist.

- [ ] **Step 3: Implement the configuration helper and HTML transform**

Expose `resolveUmamiConfig(env)` and a Vite HTML transform that adds this script only for a valid enabled configuration:

```html
<script defer src="https://cloud.umami.is/script.js"
  data-website-id="..."
  data-domains="unispeaking.qnsdk.com"></script>
```

Do not hardcode login credentials or API tokens.

- [ ] **Step 4: Verify GREEN**

Run: `npm run test:analytics`

Expected: all Umami configuration tests pass.

### Task 2: Platform-neutral event adapter

**Files:**
- Create: `frontend/web/src/analytics/activityTimer.js`
- Create: `frontend/web/src/analytics/analyticsClient.js`
- Create: `frontend/web/src/analytics/pageCatalog.js`
- Create: `frontend/web/scripts/check-analytics-client.mjs`

- [ ] **Step 1: Write failing behavior tests**

Cover disabled analytics, missing tracker, sensitive-property removal, allowed primitive properties, the four mode names, activity-aware duration, successful start/complete, failed start, and abandoned training.

- [ ] **Step 2: Verify RED**

Run: `node --test scripts/check-analytics-client.mjs`

Expected: failure because the analytics modules do not exist.

- [ ] **Step 3: Implement the minimal adapter**

Provide:

```js
analytics.trackModeSelection(context, source)
analytics.trackLearningAsset(context, assetType)
analytics.training(context).attempt()
analytics.training(context).started()
analytics.training(context).fail(reason)
analytics.training(context).complete()
analytics.training(context).abandon(reason)
```

Only whitelist `mode`, `page_code`, `source`, `reason`, `asset_type`, and
`effective_duration_seconds`. Do not transmit heartbeat events, user IDs, scene IDs, session IDs, text, audio, credentials, or arbitrary properties.

- [ ] **Step 4: Verify GREEN**

Run: `npm run test:analytics`

Expected: all configuration and adapter tests pass.

### Task 3: Latest-main business lifecycle instrumentation

**Files:**
- Modify: `frontend/web/src/controller/App.jsx`
- Modify: `frontend/web/src/component/ielts/IeltsModule.jsx`
- Modify: `frontend/web/src/component/interview/InterviewModule.jsx`
- Modify: `frontend/web/scripts/check-analytics-wiring.mjs`

- [ ] **Step 1: Write failing source-contract tests**

Assert that the current mainline free-chat, scene, IELTS, and interview flows call the common adapter at real attempt, started, completed, failed, and abandoned lifecycle points; assert that mode navigation and learning assets are instrumented.

- [ ] **Step 2: Verify RED**

Run: `node --test scripts/check-analytics-wiring.mjs`

Expected: failure because the latest mainline components have no analytics calls.

- [ ] **Step 3: Instrument current lifecycle points**

Add analytics calls around existing seven-cloud realtime and interview callbacks without changing request payloads, state transitions, error behavior, or cleanup ordering. Track duration locally but emit only terminal events.

- [ ] **Step 4: Verify GREEN and existing contracts**

Run:

```bash
npm run test:analytics
npm run test:auth
npm run check:feedback-invitation
npm run check:routes
npm run check:realtime-events
```

Expected: every command exits 0.

### Task 4: Production configuration and complete verification

**Files:**
- Modify: `deploy/docker-compose.prod.yml`
- Modify: `deploy/env/.env.prod.example`
- Modify: `docs/deployment-production.md`

- [ ] **Step 1: Add production build arguments**

Pass the four `VITE_UMAMI_*` values into the frontend image build. Keep analytics disabled by default in the example configuration and document the exact production values for `unispeaking.qnsdk.com`.

- [ ] **Step 2: Run clean production build**

Run:

```bash
npm ci
VITE_UMAMI_ENABLED=true \
VITE_UMAMI_SCRIPT_URL=https://cloud.umami.is/script.js \
VITE_UMAMI_WEBSITE_ID=3ae2dee9-d585-43a9-93f3-fcafcd14b258 \
VITE_UMAMI_DOMAINS=unispeaking.qnsdk.com \
npm run build
```

Expected: build exits 0 and generated `dist/index.html` contains the tracker, Website ID, and domain restriction exactly once.

- [ ] **Step 3: Run browser preview acceptance**

Start the production preview locally. Verify that analytics-disabled local preview performs no Umami requests; verify an enabled production build loads the tracker, calls `window.umami.track` for test interactions, and preserves navigation and training UI behavior. Capture screenshots for user acceptance.

- [ ] **Step 4: Run final repository checks**

Run `git diff --check`, the complete Web test commands, the production build, and a sensitive-field scan over generated analytics code.

- [ ] **Step 5: Commit after successful verification**

Create one local commit containing the design, plan, implementation, tests, and deployment documentation. Do not push, create a PR, or deploy the server.
