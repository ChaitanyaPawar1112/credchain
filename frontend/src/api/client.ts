import type { AuthResponse, ProblemDetail } from './types'

/**
 * Small fetch wrapper for the CredChain API.
 * - Adds "Authorization: Bearer <access token>" when logged in.
 * - On a 401 it refreshes the tokens once and retries the request.
 * - Errors become ApiError with the backend's code (e.g. INVALID_CREDENTIALS) and field errors.
 *
 * The access token lives only in memory; the refresh token is kept in localStorage so a reload stays logged in.
 */

export const API_BASE_URL: string = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'
export const REFRESH_TOKEN_KEY = 'credchain.refreshToken'

export class ApiError extends Error {
  readonly status: number
  readonly code: string | undefined
  readonly fieldErrors: Record<string, string>

  constructor(status: number, problem: ProblemDetail | null, fallback: string) {
    super(problem?.detail ?? fallback)
    this.name = 'ApiError'
    this.status = status
    this.code = problem?.code
    this.fieldErrors = problem?.errors ?? {}
  }
}

let accessToken: string | null = null
let onSessionChange: ((session: AuthResponse | null) => void) | null = null
let refreshInFlight: Promise<AuthResponse | null> | null = null

export function setAccessToken(token: string | null): void {
  accessToken = token
}

/** The AuthProvider listens here, so a refresh or an expired session updates the screen. */
export function onSessionChanged(listener: ((session: AuthResponse | null) => void) | null): void {
  onSessionChange = listener
}

export function storeSession(session: AuthResponse | null): void {
  accessToken = session?.accessToken ?? null
  try {
    if (session) {
      localStorage.setItem(REFRESH_TOKEN_KEY, session.refreshToken)
    } else {
      localStorage.removeItem(REFRESH_TOKEN_KEY)
    }
  } catch {
    // storage blocked (private mode): the session simply won't survive a reload
  }
  onSessionChange?.(session)
}

export function storedRefreshToken(): string | null {
  try {
    return localStorage.getItem(REFRESH_TOKEN_KEY)
  } catch {
    return null
  }
}

/**
 * Gets new tokens with the stored refresh token. Only one refresh runs at a time, also across browser tabs:
 * the backend logs out every session when an old refresh token is used twice.
 */
export function refreshSession(): Promise<AuthResponse | null> {
  if (!refreshInFlight) {
    refreshInFlight = withCrossTabLock(doRefresh).finally(() => {
      refreshInFlight = null
    })
  }
  return refreshInFlight
}

async function doRefresh(): Promise<AuthResponse | null> {
  const refreshToken = storedRefreshToken()   // read inside the lock: another tab may have just rotated it
  if (!refreshToken) {
    return null
  }
  const response = await fetch(`${API_BASE_URL}/api/v1/auth/refresh`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken }),
  })
  if (!response.ok) {
    if (response.status === 401 || response.status === 400) {
      storeSession(null)
    }
    return null
  }
  const session = (await response.json()) as AuthResponse
  storeSession(session)
  return session
}

function withCrossTabLock<T>(task: () => Promise<T>): Promise<T> {
  const locks = typeof navigator !== 'undefined' ? navigator.locks : undefined
  return locks ? (locks.request('credchain-refresh', task) as Promise<T>) : task()
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  /** Sent as JSON, or as-is when it is FormData (file uploads). */
  body?: unknown
  /** false for login/register: no token, no refresh-and-retry. */
  auth?: boolean
  signal?: AbortSignal
  /** Accept header; set it for file downloads (PDF, CSV). */
  accept?: string
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const response = await send(path, options)
  if (response.status === 204) {
    return undefined as T
  }
  const type = response.headers.get('Content-Type') ?? ''
  return (type.includes('json') ? await response.json() : await response.blob()) as T
}

async function send(path: string, options: RequestOptions, retried = false): Promise<Response> {
  const { method = 'GET', body, auth = true, signal, accept = 'application/json' } = options
  const headers: Record<string, string> = { Accept: accept }
  let payload: BodyInit | undefined
  if (body instanceof FormData) {
    payload = body
  } else if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
    payload = JSON.stringify(body)
  }
  if (auth && accessToken) {
    headers.Authorization = `Bearer ${accessToken}`
  }

  let response: Response
  try {
    response = await fetch(`${API_BASE_URL}${path}`, { method, headers, body: payload, signal })
  } catch (e) {
    if (e instanceof DOMException && e.name === 'AbortError') {
      throw e
    }
    throw new ApiError(0, null, 'Cannot reach the CredChain server. Is the backend running?')
  }

  if (response.status === 401 && auth && !retried && storedRefreshToken()) {
    const session = await refreshSession()
    if (session) {
      return send(path, options, true)
    }
  }
  if (!response.ok) {
    throw new ApiError(response.status, await readProblem(response), fallbackMessage(response.status))
  }
  return response
}

async function readProblem(response: Response): Promise<ProblemDetail | null> {
  try {
    return (await response.json()) as ProblemDetail
  } catch {
    return null
  }
}

function fallbackMessage(status: number): string {
  if (status === 429) return 'Too many requests. Please wait a minute and try again.'
  if (status >= 500) return 'Something went wrong on the server. Please try again later.'
  return `Request failed (${status})`
}
