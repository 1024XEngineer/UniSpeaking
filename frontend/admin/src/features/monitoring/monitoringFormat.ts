const formatNumber = (value: number, digits = 1) => Number.isFinite(value)
  ? value.toLocaleString('zh-CN', { maximumFractionDigits: digits })
  : '-'

export const formatDuration = (seconds: number) => seconds < 1
  ? `${formatNumber(seconds * 1000)} ms`
  : `${formatNumber(seconds, 2)} s`
