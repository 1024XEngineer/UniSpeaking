import type { components } from '../../api/generated'

export type LoginRequest = components['schemas']['LoginRequest']
export type AdminMe = components['schemas']['AdminMe']
type ApiErrorEnvelope = components['schemas']['ApiError']

const API_BASE = (import.meta.env.VITE_BACKEND_URL || '').replace(/\/$/, '')

export function buildAdminApiUrl(path: string, base = API_BASE): string {
  return `${String(base || '').replace(/\/$/, '')}${path}`
}

export class ApiClientError extends Error {
  public readonly code: string
  public readonly requestId?: string

  constructor(
    code: string,
    message: string,
    requestId?: string,
  ) {
    super(message)
    this.name = 'ApiClientError'
    this.code = code
    this.requestId = requestId
  }
}

async function toApiError(response: Response): Promise<ApiClientError> {
  try {
    const body = await response.json() as ApiErrorEnvelope
    return new ApiClientError(body.error.code, body.error.message, body.request_id)
  } catch {
    return new ApiClientError('NETWORK_RESPONSE_INVALID', '服务暂时不可用，请稍后重试')
  }
}

export async function loginAdministrator(request: LoginRequest): Promise<void> {
  const response = await fetch(buildAdminApiUrl('/api/admin/auth/login'), {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })
  if (!response.ok) {
    throw await toApiError(response)
  }
}

export async function getCurrentAdministrator(): Promise<AdminMe | null> {
  const response = await fetch(buildAdminApiUrl('/api/admin/auth/me'), {
    credentials: 'include',
  })
  if (response.status === 401) {
    return null
  }
  if (!response.ok) {
    throw await toApiError(response)
  }
  return response.json() as Promise<AdminMe>
}

export async function logoutAdministrator(): Promise<void> {
  const response = await fetch(buildAdminApiUrl('/api/admin/auth/logout'), {
    method: 'POST',
    credentials: 'include',
  })
  if (!response.ok && response.status !== 401) {
    throw await toApiError(response)
  }
}
