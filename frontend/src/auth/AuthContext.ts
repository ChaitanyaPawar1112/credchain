import { createContext, useContext } from 'react'
import type { RegisterInput } from '../api/auth'
import type { UserSummary } from '../api/types'

export interface AuthState {
  /** null = logged out. */
  user: UserSummary | null
  /** true while the app checks a saved session on start-up. */
  loading: boolean
  login: (email: string, password: string) => Promise<UserSummary>
  register: (input: RegisterInput) => Promise<UserSummary>
  logout: () => Promise<void>
  /** Changes the password; the backend then logs out every session, so the user signs in again. */
  changePassword: (currentPassword: string, newPassword: string) => Promise<void>
}

export const AuthContext = createContext<AuthState | null>(null)

export function useAuth(): AuthState {
  const auth = useContext(AuthContext)
  if (!auth) {
    throw new Error('useAuth must be used inside <AuthProvider>')
  }
  return auth
}
