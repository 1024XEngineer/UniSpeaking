export function createActivityTimer({ now = () => Date.now() } = {}) {
  let elapsedMs = 0
  let activeSince = null
  let started = false
  let paused = false
  let visible = true

  const settle = () => {
    if (activeSince !== null) {
      elapsedMs += Math.max(0, now() - activeSince)
      activeSince = null
    }
    if (started && !paused && visible) activeSince = now()
    return Math.round(elapsedMs / 1000)
  }

  return {
    start() {
      if (started) return
      started = true
      if (visible) activeSince = now()
    },
    pause() {
      if (!started || paused) return Math.round(elapsedMs / 1000)
      settle()
      paused = true
      activeSince = null
      return Math.round(elapsedMs / 1000)
    },
    resume() {
      if (!started || !paused) return
      paused = false
      if (visible) activeSince = now()
    },
    setVisible(nextVisible) {
      const next = Boolean(nextVisible)
      if (next === visible) return
      settle()
      visible = next
      activeSince = started && !paused && visible ? now() : null
    },
    settle,
    stop() {
      settle()
      started = false
      activeSince = null
      return Math.round(elapsedMs / 1000)
    },
    isStarted: () => started,
  }
}
