const HTTPS_URL = /^https:\/\//i
const WEBSITE_ID = /^[a-zA-Z0-9-]+$/
const SAFE_DOMAINS = /^[a-zA-Z0-9.,-]+$/

const escapeAttribute = (value) => String(value)
  .replaceAll('&', '&amp;')
  .replaceAll('"', '&quot;')
  .replaceAll('<', '&lt;')
  .replaceAll('>', '&gt;')

export function resolveUmamiConfig(env = {}) {
  const enabled = env.VITE_UMAMI_ENABLED === 'true'
  const scriptUrl = String(env.VITE_UMAMI_SCRIPT_URL || '').trim()
  const websiteId = String(env.VITE_UMAMI_WEBSITE_ID || '').trim()
  const domains = String(env.VITE_UMAMI_DOMAINS || '').replaceAll(' ', '').trim()

  if (!enabled || !HTTPS_URL.test(scriptUrl) || !WEBSITE_ID.test(websiteId) || !SAFE_DOMAINS.test(domains)) {
    return { enabled: false }
  }

  return { enabled: true, scriptUrl, websiteId, domains }
}

export function injectUmamiScript(html, env = {}) {
  const config = resolveUmamiConfig(env)
  if (!config.enabled || html.includes('data-umami-tracker')) return html

  const script = `<script defer src="${escapeAttribute(config.scriptUrl)}" data-website-id="${escapeAttribute(config.websiteId)}" data-domains="${escapeAttribute(config.domains)}" data-auto-track="false" data-umami-tracker></script>`
  return html.replace('</head>', `    ${script}\n  </head>`)
}
