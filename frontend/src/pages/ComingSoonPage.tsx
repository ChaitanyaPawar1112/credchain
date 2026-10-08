import { Link, useLocation } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { HOME_BY_ROLE } from '../auth/roles'
import { Icon } from '../components/Icon'
import { NAV_BY_ROLE } from '../layouts/navigation'

/** Pages listed in the sidebar that a later Phase 6 step builds. */
export function ComingSoonPage() {
  const { user } = useAuth()
  const { pathname } = useLocation()
  const item = user ? NAV_BY_ROLE[user.role].find((i) => i.to === pathname) : undefined
  return (
    <div className="animate-fade-up mx-auto max-w-2xl">
      <div className="rounded-3xl bg-white px-8 py-14 text-center shadow-sm ring-1 ring-slate-200">
        <span className="mx-auto flex h-16 w-16 items-center justify-center rounded-2xl bg-gradient-to-br from-navy-600 to-navy-800 text-gold-300 shadow-lg">
          <Icon name={item?.icon ?? 'sparkle'} className="h-8 w-8" />
        </span>
        <h1 className="mt-6 text-2xl font-bold text-slate-900">{item?.label ?? 'Coming soon'}</h1>
        {item?.description && <p className="mx-auto mt-2 max-w-md text-slate-600">{item.description}</p>}
        <p className="mt-6 inline-flex items-center gap-2 rounded-full bg-gold-50 px-3 py-1 text-sm font-medium text-gold-600 ring-1 ring-gold-100">
          <Icon name="clock" className="h-4 w-4" />
          This page is built in step {item?.soon ?? 'later in Phase 6'}
        </p>
        {user && (
          <div className="mt-8">
            <Link to={HOME_BY_ROLE[user.role]} className="text-sm font-semibold text-navy-700 hover:underline">
              Back to overview
            </Link>
          </div>
        )}
      </div>
    </div>
  )
}
