const routes = [
  { prefix: '/interview/assets', value: { pageCode: 'interview-assets', mode: 'INTERVIEW', assetType: 'REPORT' } },
  { prefix: '/interview', value: { pageCode: 'interview-training', mode: 'INTERVIEW' } },
  { prefix: '/ielts/assets', value: { pageCode: 'ielts-assets', mode: 'IELTS', assetType: 'REPORT' } },
  { prefix: '/ielts', value: { pageCode: 'ielts-training', mode: 'IELTS' } },
  { prefix: '/assets', value: { pageCode: 'scene-assets', mode: 'SCENE', assetType: 'REPORT' } },
  { prefix: '/scenes', value: { pageCode: 'scene-training', mode: 'SCENE' } },
  { prefix: '/conversation', value: { pageCode: 'conversation', mode: 'FREE_CHAT' } },
]

export function pageForPath(pathname = '/') {
  return routes.find(({ prefix }) => pathname === prefix || pathname.startsWith(`${prefix}/`))?.value
    || { pageCode: 'other' }
}

export function normalizeTrackedPath(pathname = '/') {
  const path = `/${String(pathname).split(/[?#]/, 1)[0].split('/').filter(Boolean).join('/')}`

  if (/^\/conversation\/[^/]+$/.test(path)) return '/conversation/session'
  if (/^\/scenes\/[^/]+\/session\/[^/]+\/result$/.test(path)) return '/scenes/session/result'
  if (/^\/scenes\/[^/]+\/session\/[^/]+$/.test(path)) return '/scenes/session'
  if (/^\/scenes\/[^/]+\/(word|phrase|sentence|assets)$/.test(path)) return `/scenes/${path.split('/').at(-1)}`
  if (/^\/interview\/scenes\/[^/]+\/session\/[^/]+\/report$/.test(path)) return '/interview/session/report'
  if (/^\/interview\/scenes\/[^/]+\/session$/.test(path)) return '/interview/session'
  if (/^\/ielts\/(part1|part2|part3)\/[^/]+\/(setup|session|analysis|report)$/.test(path)) {
    const [, , part, , screen] = path.split('/')
    return `/ielts/${part}/selection/${screen}`
  }
  if (/^\/ielts\/(part1|part2|part3)\/[^/]+$/.test(path)) {
    const [, , part] = path.split('/')
    return `/ielts/${part}/selection`
  }
  if (/^\/help\/(category|article)\/[^/]+$/.test(path)) return `/help/${path.split('/')[2]}`

  return path || '/'
}
