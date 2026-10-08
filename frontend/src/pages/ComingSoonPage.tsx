import { useLocation } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { Alert } from '../components/Alert'
import { NAV_BY_ROLE } from '../layouts/navigation'

/** Pages listed in the sidebar that a later Phase 6 step builds. */
export function ComingSoonPage() {
  const { user } = useAuth()
  const { pathname } = useLocation()
  const item = user ? NAV_BY_ROLE[user.role].find((i) => i.to === pathname) : undefined
  return (
    <div className="mx-auto max-w-3xl">
      <h1 className="text-2xl font-bold text-slate-900">{item?.label ?? 'Coming soon'}</h1>
      <div className="mt-6">
        <Alert tone="info">This page is built in step {item?.soon ?? 'later in Phase 6'}.</Alert>
      </div>
    </div>
  )
}
