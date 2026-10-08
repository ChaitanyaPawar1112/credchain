import { Link } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { ROLE_LABEL } from '../auth/roles'
import { NAV_BY_ROLE } from '../layouts/navigation'

/** Landing page of every dashboard: a greeting and shortcuts to the role's pages. */
export function DashboardHome() {
  const { user } = useAuth()
  if (!user) {
    return null
  }
  const shortcuts = NAV_BY_ROLE[user.role].slice(1)

  return (
    <div className="mx-auto max-w-5xl">
      <p className="text-sm font-medium text-gold-500">{ROLE_LABEL[user.role]}</p>
      <h1 className="mt-1 text-2xl font-bold text-slate-900">Welcome, {user.fullName}</h1>
      <p className="mt-1 text-sm text-slate-600">{user.email}</p>

      <div className="mt-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {shortcuts.map((item) => (
          <Link key={item.to} to={item.to}
                className="group rounded-xl bg-white p-5 shadow-sm ring-1 ring-slate-200 transition hover:ring-navy-600">
            <div className="flex items-center justify-between">
              <h2 className="font-semibold text-slate-900 group-hover:text-navy-700">{item.label}</h2>
              {item.soon && <span className="text-xs text-slate-400">step {item.soon}</span>}
            </div>
          </Link>
        ))}
      </div>

      <p className="mt-8 text-sm text-slate-500">
        <Link to="/change-password" className="font-medium text-navy-700 hover:underline">Change password</Link>
      </p>
    </div>
  )
}
