import { Link } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { ROLE_LABEL } from '../auth/roles'
import { Avatar } from '../components/Avatar'
import { Icon } from '../components/Icon'
import { NAV_BY_ROLE } from '../layouts/navigation'
import { AdminStats } from './admin/AdminStats'
import { InstitutionStats } from './institution/InstitutionStats'

function greeting(hour: number) {
  if (hour < 12) return 'Good morning'
  if (hour < 17) return 'Good afternoon'
  return 'Good evening'
}

/** Landing page of every dashboard: a greeting and shortcuts to the role's pages. */
export function DashboardHome() {
  const { user } = useAuth()
  if (!user) {
    return null
  }
  const shortcuts = NAV_BY_ROLE[user.role].slice(1)
  const firstName = user.fullName.split(/\s+/)[0]

  return (
    <div className="animate-fade-up mx-auto max-w-6xl">
      <section className="bg-brand relative overflow-hidden rounded-3xl px-6 py-8 text-white shadow-xl sm:px-10 sm:py-10">
        <div className="flex flex-col gap-5 sm:flex-row sm:items-center">
          <Avatar name={user.fullName} size="lg" />
          <div>
            <span className="inline-block rounded-full bg-gold-400/15 px-2.5 py-0.5 text-xs font-semibold text-gold-300 ring-1 ring-gold-400/30">
              {ROLE_LABEL[user.role]}
            </span>
            <h1 className="mt-2 text-2xl font-extrabold tracking-tight sm:text-3xl">
              {greeting(new Date().getHours())}, {firstName}
            </h1>
            <p className="mt-1 text-sm text-navy-100">{user.email}</p>
          </div>
        </div>
      </section>

      {user.role === 'SUPER_ADMIN' && <AdminStats />}
      {user.role === 'INSTITUTION_ADMIN' && <InstitutionStats />}

      <div className="mt-10 flex items-end justify-between">
        <h2 className="text-lg font-bold text-slate-900">What you can do</h2>
      </div>
      <div className="mt-4 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
        {shortcuts.map((item) => (
          <Link key={item.to} to={item.to}
                className="group flex flex-col rounded-2xl bg-white p-6 shadow-sm ring-1 ring-slate-200 transition
                           hover:-translate-y-0.5 hover:shadow-lg hover:ring-navy-200">
            <div className="flex items-start justify-between">
              <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-navy-50 text-navy-700 transition
                               group-hover:bg-navy-700 group-hover:text-gold-300">
                <Icon name={item.icon} className="h-5 w-5" />
              </span>
              {item.soon && (
                <span className="rounded-full bg-gold-50 px-2 py-0.5 text-[11px] font-semibold text-gold-600 ring-1 ring-gold-100">
                  Coming in {item.soon}
                </span>
              )}
            </div>
            <h3 className="mt-4 font-bold text-slate-900">{item.label}</h3>
            {item.description && <p className="mt-1 flex-1 text-sm leading-relaxed text-slate-600">{item.description}</p>}
            <span className="mt-4 inline-flex items-center gap-1 text-sm font-semibold text-navy-700 transition-all group-hover:gap-2">
              Open <Icon name="arrowRight" className="h-4 w-4" />
            </span>
          </Link>
        ))}
      </div>

      <section className="mt-8 flex flex-col items-start gap-4 rounded-2xl bg-white p-6 ring-1 ring-slate-200 sm:flex-row sm:items-center">
        <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-emerald-50 text-emerald-600">
          <Icon name="lock" className="h-5 w-5" />
        </span>
        <div className="flex-1">
          <h3 className="font-bold text-slate-900">Account security</h3>
          <p className="text-sm text-slate-600">Changing your password logs you out on every device.</p>
        </div>
        <Link to="/change-password"
              className="rounded-xl bg-white px-4 py-2 text-sm font-semibold text-slate-800 ring-1 ring-slate-300 transition hover:bg-slate-50">
          Change password
        </Link>
      </section>
    </div>
  )
}
