import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'
import type { Role } from '../api/types'
import { FullPageSpinner } from '../components/Spinner'
import { useAuth } from './AuthContext'
import { HOME_BY_ROLE } from './roles'

/**
 * Shows the page only to logged-in users with one of the given roles.
 * Logged out -> login page (and back here afterwards). Wrong role -> their own dashboard.
 * A user who must change their temporary password is sent to that screen first.
 */
export function RequireAuth({ roles, children }: { roles?: Role[]; children: ReactNode }) {
  const { user, loading } = useAuth()
  const location = useLocation()

  if (loading) {
    return <FullPageSpinner />
  }
  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  }
  if (user.mustChangePassword && location.pathname !== '/change-password') {
    return <Navigate to="/change-password" replace />
  }
  if (roles && !roles.includes(user.role)) {
    return <Navigate to={HOME_BY_ROLE[user.role]} replace />
  }
  return <>{children}</>
}
