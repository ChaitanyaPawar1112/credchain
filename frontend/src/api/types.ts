/** Shapes returned by the Spring Boot API (see the backend DTOs). */

export type Role = 'SUPER_ADMIN' | 'INSTITUTION_ADMIN' | 'STUDENT' | 'VERIFIER'

export interface UserSummary {
  id: string
  email: string
  fullName: string
  role: Role
  institutionId: string | null
  /** true right after an admin created the account: the user must set a new password first. */
  mustChangePassword: boolean
}

export interface AuthResponse {
  tokenType: 'Bearer'
  accessToken: string
  accessTokenExpiresAt: string
  refreshToken: string
  refreshTokenExpiresAt: string
  user: UserSummary
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  last: boolean
}

/** RFC 9457 Problem Details as sent by GlobalExceptionHandler. */
export interface ProblemDetail {
  status: number
  title?: string
  detail?: string
  code?: string
  /** Field name -> message, for 400 VALIDATION_FAILED. */
  errors?: Record<string, string>
}
