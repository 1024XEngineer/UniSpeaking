import { createActivityTimer } from './activityTimer.js'
import { normalizeTrackedPath } from './pageCatalog.js'

const TRAINING_MODES = new Set(['SCENE', 'FREE_CHAT', 'INTERVIEW', 'IELTS'])
const SAFE_VALUES = new Set(['mode', 'page_code', 'source', 'reason', 'asset_type', 'effective_duration_seconds'])

function safeData(input = {}) {
  return Object.fromEntries(Object.entries(input).filter(([key, value]) => {
    if (!SAFE_VALUES.has(key)) return false
    if (typeof value === 'string') return value.length > 0 && value.length <= 80
    return typeof value === 'number' && Number.isFinite(value) || typeof value === 'boolean'
  }))
}

function contextData(context = {}) {
  return safeData({ mode: context.mode, page_code: context.pageCode })
}

export function createAnalyticsClient(options = {}) {
  const enabled = options.enabled ?? false
  const tracker = options.tracker || (() => globalThis.window?.umami)
  const eventTarget = options.eventTarget || globalThis.window
  const now = options.now || (() => Date.now())
  const pending = []

  function dispatch(send) {
    if (!enabled) return
    try {
      const instance = tracker()
      if (typeof instance?.track === 'function') {
        send(instance)
        return
      }
      if (pending.length < 50) pending.push(send)
    } catch {
      // Analytics must never block product behavior.
    }
  }

  function flushPending() {
    let instance
    try {
      instance = tracker()
    } catch {
      return
    }
    if (typeof instance?.track !== 'function') return
    pending.splice(0).forEach((send) => {
      try { send(instance) } catch { /* One event must not block the remaining queue. */ }
    })
  }

  if (enabled && typeof eventTarget?.addEventListener === 'function') {
    eventTarget.addEventListener('umami:loaded', flushPending, { once: true })
  }

  function emit(name, data = {}) {
    dispatch((instance) => instance.track(name, safeData(data)))
  }

  function validTrainingContext(context = {}) {
    return TRAINING_MODES.has(context.mode)
  }

  function training(context = {}) {
    const timer = createActivityTimer({ now })
    let ended = false
    const base = contextData(context)

    return {
      attempt() {
        if (!ended && validTrainingContext(context)) emit('training_start_attempt', base)
      },
      started() {
        if (ended || timer.isStarted() || !validTrainingContext(context)) return
        timer.start()
        emit('training_started', base)
      },
      fail(reason = 'REALTIME_ERROR') {
        if (ended || timer.isStarted() || !validTrainingContext(context)) return
        emit('training_start_failed', { ...base, reason })
        ended = true
      },
      pause() { if (!ended) timer.pause() },
      resume() { if (!ended) timer.resume() },
      setVisible(visible) { if (!ended) timer.setVisible(visible) },
      heartbeat() { if (!ended) timer.settle() },
      complete() {
        if (ended || !timer.isStarted()) return
        emit('training_completed', { ...base, effective_duration_seconds: timer.stop() })
        ended = true
      },
      abandon(reason = 'USER_EXIT') {
        if (ended || !timer.isStarted()) return
        emit('training_abandoned', { ...base, reason, effective_duration_seconds: timer.stop() })
        ended = true
      },
      isStarted: () => timer.isStarted(),
    }
  }

  return {
    training,
    trackPageView(pathname = '/') {
      const url = normalizeTrackedPath(pathname)
      dispatch((instance) => instance.track((properties) => ({ ...properties, url, title: 'UniSpeaking' })))
    },
    trackModeSelection(context = {}, source = 'navigation') {
      if (validTrainingContext(context)) emit('mode_selected', { ...contextData(context), source })
    },
    trackLearningAsset(context = {}, assetType = 'REPORT') {
      emit('learning_asset_view', { ...contextData(context), asset_type: assetType })
    },
  }
}

export const analytics = createAnalyticsClient({
  enabled: import.meta.env?.VITE_UMAMI_ENABLED === 'true',
})
