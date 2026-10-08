import { apiRequest } from './client'
import type { AuthResponse, Role, UserSummary } from './types'

export interface RegisterInput {
  email: string
  password: string
  fullName: string
  phone?: string
  role: Extract<Role, 'STUDENT' | 'VERIFIER'>
}

export const authApi = {
  login: (email: string, password: string) =>
    apiRequest<AuthResponse>('/api/v1/auth/login', { method: 'POST', body: { email, password }, auth: false }),

  register: (input: RegisterInput) =>
    apiRequest<AuthResponse>('/api/v1/auth/register', {
      method: 'POST',
      body: { ...input, phone: input.phone?.trim() || undefined },
      auth: false,
    }),

  logout: (refreshToken: string) =>
    apiRequest<void>('/api/v1/auth/logout', { method: 'POST', body: { refreshToken }, auth: false }),

  me: () => apiRequest<UserSummary>('/api/v1/auth/me'),

  /** Logs out every session on success (the backend revokes all refresh tokens). */
  changePassword: (currentPassword: string, newPassword: string) =>
    apiRequest<void>('/api/v1/auth/change-password', { method: 'POST', body: { currentPassword, newPassword } }),
}
