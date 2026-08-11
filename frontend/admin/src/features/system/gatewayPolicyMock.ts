export type KeyPoolState = 'healthy' | 'cooldown' | 'exhausted'

export interface KeyPoolEntry {
  id: string
  maskedFingerprint: string
  state: KeyPoolState
  weight: number
  dispatched: number
}

export interface KeyPoolSnapshot {
  strategy: 'round-robin'
  entries: KeyPoolEntry[]
}

export interface KeyDispatchResult {
  keyId: string
  reason: string
  dispatchedAt: string
}

export interface ModelRoutingSnapshot {
  primary: string
  fallbacks: string[]
  active: string
  mode: 'automatic'
}

export interface ModelFailoverResult {
  from: string
  to: string
  reason: string
  switchedAt: string
}

const initialKeys: KeyPoolEntry[] = [
  { id: 'key_mock_cn_01', maskedFingerprint: '•••• 9F2A', state: 'healthy', weight: 1, dispatched: 0 },
  { id: 'key_mock_cn_02', maskedFingerprint: '•••• 7C81', state: 'healthy', weight: 1, dispatched: 0 },
  { id: 'key_mock_cn_03', maskedFingerprint: '•••• 4D60', state: 'cooldown', weight: 1, dispatched: 0 },
]

const initialRouting: ModelRoutingSnapshot = {
  primary: 'qwen3.5-omni-flash-realtime',
  fallbacks: ['qwen3-omni-flash-realtime', 'qwen2.5-omni-realtime'],
  active: 'qwen3.5-omni-flash-realtime',
  mode: 'automatic',
}

const now = () => new Date().toISOString()

export function createMockKeyPool() {
  let cursor = -1
  let snapshot: KeyPoolSnapshot = {
    strategy: 'round-robin',
    entries: initialKeys.map((entry) => ({ ...entry })),
  }

  return {
    snapshot: () => snapshot,
    dispatch: (): KeyDispatchResult | null => {
      const eligible = snapshot.entries.filter((entry) => entry.state === 'healthy')
      if (eligible.length === 0) return null
      cursor = (cursor + 1) % eligible.length
      const selected = eligible[cursor]
      snapshot = {
        ...snapshot,
        entries: snapshot.entries.map((entry) => entry.id === selected.id
          ? { ...entry, dispatched: entry.dispatched + 1 }
          : entry),
      }
      return { keyId: selected.id, reason: 'round-robin 模拟分发', dispatchedAt: now() }
    },
  }
}

export function createMockModelRouter() {
  let snapshot: ModelRoutingSnapshot = { ...initialRouting, fallbacks: [...initialRouting.fallbacks] }

  return {
    snapshot: () => snapshot,
    failover: (): ModelFailoverResult => {
      const from = snapshot.active
      const fallbackIndex = snapshot.fallbacks.indexOf(from)
      const nextIndex = fallbackIndex < 0 ? 0 : (fallbackIndex + 1) % snapshot.fallbacks.length
      const to = snapshot.fallbacks[nextIndex]
      snapshot = { ...snapshot, active: to }
      return { from, to, reason: '模拟上游超时，自动切换备用模型', switchedAt: now() }
    },
  }
}
