import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { authApi, type RegisterInput } from '../api/auth'
import { onSessionChanged, refreshSession, storeSession, storedRefreshToken } from '../api/client'
import type { UserSummary } from '../api/types'
import { AuthContext, type AuthState } from './AuthContext'

/** Keeps track of who is logged in. A saved session is restored on start-up with the refresh token. */
export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [user, setUser] = useState<UserSummary | null>(null)
  const [loading, setLoading] = useState(() => storedRefreshToken() !== null)

  useEffect(() => {
    onSessionChanged((session) => setUser(session?.user ?? null))
    if (storedRefreshToken()) {
      refreshSession().finally(() => setLoading(false))
    }
    return () => onSessionChanged(null)
  }, [])

  const login = useCallback(async (email: string, password: string) => {
    const session = await authApi.login(email, password)
    queryClient.clear()
    storeSession(session)
    return session.user
  }, [queryClient])

  const register = useCallback(async (input: RegisterInput) => {
    const session = await authApi.register(input)
    queryClient.clear()
    storeSession(session)
    return session.user
  }, [queryClient])

  const logout = useCallback(async () => {
    const refreshToken = storedRefreshToken()
    storeSession(null)
    queryClient.clear()
    if (refreshToken) {
      await authApi.logout(refreshToken).catch(() => undefined)   // already logged out locally either way
    }
  }, [queryClient])

  const changePassword = useCallback(async (currentPassword: string, newPassword: string) => {
    await authApi.changePassword(currentPassword, newPassword)
    storeSession(null)
    queryClient.clear()
  }, [queryClient])

  const value = useMemo<AuthState>(
    () => ({ user, loading, login, register, logout, changePassword }),
    [user, loading, login, register, logout, changePassword],
  )
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
