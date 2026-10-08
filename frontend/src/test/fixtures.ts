import type { AuthResponse, Role, UserSummary } from '../api/types'

export function user(role: Role, overrides: Partial<UserSummary> = {}): UserSummary {
  return {
    id: '00000000-0000-0000-0000-000000000001',
    email: 'asha@example.com',
    fullName: 'Asha Patil',
    role,
    institutionId: null,
    mustChangePassword: false,
    ...overrides,
  }
}

export function session(role: Role, refreshToken = 'refresh-1', accessToken = 'access-1'): AuthResponse {
  return {
    tokenType: 'Bearer',
    accessToken,
    accessTokenExpiresAt: '2026-10-08T10:15:00Z',
    refreshToken,
    refreshTokenExpiresAt: '2026-10-15T10:00:00Z',
    user: user(role),
  }
}

export function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}
