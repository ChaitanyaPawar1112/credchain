import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { json, session } from '../test/fixtures'
import { ApiError, REFRESH_TOKEN_KEY, apiRequest, setAccessToken, storeSession } from './client'

const fetchMock = vi.fn<typeof fetch>()

beforeEach(() => {
  vi.stubGlobal('fetch', fetchMock)
  fetchMock.mockReset()
})

afterEach(() => {
  vi.unstubAllGlobals()
  storeSession(null)
})

function authHeader(call: number): string | undefined {
  const init = fetchMock.mock.calls[call][1] as RequestInit
  return (init.headers as Record<string, string>).Authorization
}

describe('apiRequest', () => {
  it('sends the access token and parses JSON', async () => {
    storeSession(session('STUDENT'))
    fetchMock.mockResolvedValueOnce(json({ ok: true }))

    await expect(apiRequest('/api/v1/me/certificates')).resolves.toEqual({ ok: true })
    expect(authHeader(0)).toBe('Bearer access-1')
  })

  it('on 401 refreshes once, stores the rotated refresh token and retries', async () => {
    storeSession(session('STUDENT'))
    setAccessToken('expired')
    fetchMock
      .mockResolvedValueOnce(json({ status: 401, code: 'INVALID_TOKEN' }, 401))
      .mockResolvedValueOnce(json(session('STUDENT', 'refresh-2', 'access-2')))
      .mockResolvedValueOnce(json({ ok: true }))

    await expect(apiRequest('/api/v1/auth/me')).resolves.toEqual({ ok: true })
    expect(fetchMock.mock.calls[1][0]).toContain('/api/v1/auth/refresh')
    expect(authHeader(2)).toBe('Bearer access-2')
    expect(localStorage.getItem(REFRESH_TOKEN_KEY)).toBe('refresh-2')
  })

  it('two requests failing at once share one refresh (a reused refresh token logs everyone out)', async () => {
    storeSession(session('STUDENT'))
    setAccessToken('expired')
    let refreshCalls = 0
    fetchMock.mockImplementation(async (input, init) => {
      const url = String(input)
      if (url.endsWith('/auth/refresh')) {
        refreshCalls++
        return json(session('STUDENT', 'refresh-2', 'access-2'))
      }
      const auth = (init?.headers as Record<string, string>).Authorization
      return auth === 'Bearer access-2' ? json({ ok: true }) : json({ status: 401 }, 401)
    })

    await Promise.all([apiRequest('/a'), apiRequest('/b')])
    expect(refreshCalls).toBe(1)
  })

  it('when the refresh is rejected the session is cleared and the error surfaces', async () => {
    storeSession(session('STUDENT'))
    fetchMock
      .mockResolvedValueOnce(json({ status: 401 }, 401))
      .mockResolvedValueOnce(json({ status: 401, code: 'INVALID_TOKEN' }, 401))

    await expect(apiRequest('/api/v1/auth/me')).rejects.toMatchObject({ status: 401 })
    expect(localStorage.getItem(REFRESH_TOKEN_KEY)).toBeNull()
  })

  it('turns Problem Details into ApiError with code and field errors', async () => {
    fetchMock.mockResolvedValueOnce(json({
      status: 400, code: 'VALIDATION_FAILED', detail: 'One or more fields are invalid',
      errors: { email: 'must be a well-formed email address' },
    }, 400))

    const error = await apiRequest('/api/v1/auth/register', { method: 'POST', body: {}, auth: false })
      .catch((e: unknown) => e)
    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ code: 'VALIDATION_FAILED', fieldErrors: { email: 'must be a well-formed email address' } })
  })

  it('a server that cannot be reached gives a clear message', async () => {
    fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'))
    await expect(apiRequest('/x', { auth: false })).rejects.toMatchObject({
      status: 0, message: 'Cannot reach the CredChain server. Is the backend running?',
    })
  })
})
