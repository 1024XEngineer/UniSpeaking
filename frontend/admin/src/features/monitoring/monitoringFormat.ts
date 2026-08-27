const formatNumber = (value: number, digits = 1) => Number.isFinite(value)
  ? value.toLocaleString('zh-CN', { maximumFractionDigits: digits })
  : '-'

export const formatDuration = (seconds: number) => seconds < 1
  ? `${formatNumber(seconds * 1000)} ms`
  : `${formatNumber(seconds, 2)} s`

export const formatMilliseconds = (milliseconds: number | null | undefined) => {
  if (milliseconds == null || !Number.isFinite(milliseconds)) return '—'
  return milliseconds < 1000
    ? `${formatNumber(milliseconds)} ms`
    : `${formatNumber(milliseconds / 1000, 2)} s`
}

export const improvementRate = (baseline: number, current: number) => baseline > 0
  ? ((baseline - current) / baseline) * 100
  : null
