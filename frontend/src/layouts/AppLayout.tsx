import { useState } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { ROLE_LABEL } from '../auth/roles'
import { Avatar } from '../components/Avatar'
import { Icon } from '../components/Icon'
import { Logo } from '../components/Logo'
import { NAV_BY_ROLE } from './navigation'

/** Logged-in area: sidebar with the role's pages (a slide-in menu on phones). */
export function AppLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [menuOpen, setMenuOpen] = useState(false)
  if (!user) {
    return null   // RequireAuth wraps this layout, so this only happens for a moment during logout
  }

  const handleLogout = async () => {
    await logout()
    navigate('/login', { replace: true })
  }

  const sidebar = (
    <div className="bg-brand flex h-full flex-col text-white">
      <div className="px-5 py-6">
        <Logo light />
      </div>
      <p className="px-6 pb-2 text-[11px] font-semibold uppercase tracking-wider text-navy-300">Menu</p>
      <nav className="flex-1 space-y-1 px-3" aria-label="Main">
        {NAV_BY_ROLE[user.role].map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end
            onClick={() => setMenuOpen(false)}
            className={({ isActive }) =>
              `group relative flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium transition ${
                isActive ? 'bg-white/10 text-white ring-1 ring-white/10' : 'text-navy-100 hover:bg-white/5 hover:text-white'}`}
          >
            {({ isActive }) => (
              <>
                {isActive && <span className="absolute inset-y-2 left-0 w-1 rounded-r-full bg-gold-400" />}
                <Icon name={item.icon} className={`h-5 w-5 ${isActive ? 'text-gold-300' : 'text-navy-300 group-hover:text-navy-100'}`} />
                <span className="flex-1">{item.label}</span>
                {item.soon && (
                  <span className="rounded-md bg-gold-400/15 px-1.5 py-0.5 text-[10px] font-semibold uppercase text-gold-300">soon</span>
                )}
              </>
            )}
          </NavLink>
        ))}
      </nav>
      <div className="m-3 rounded-2xl bg-white/5 p-4 ring-1 ring-white/10">
        <div className="flex items-center gap-3">
          <Avatar name={user.fullName} />
          <div className="min-w-0">
            <p className="truncate text-sm font-semibold">{user.fullName}</p>
            <p className="truncate text-xs text-navy-200">{user.email}</p>
          </div>
        </div>
        <p className="mt-3 inline-block rounded-full bg-gold-400/15 px-2 py-0.5 text-[11px] font-semibold text-gold-300">
          {ROLE_LABEL[user.role]}
        </p>
        <button onClick={handleLogout}
                className="mt-3 flex w-full items-center justify-center gap-2 rounded-xl bg-white/10 px-3 py-2 text-sm
                           font-medium transition hover:bg-white/20">
          <Icon name="logout" className="h-4 w-4" /> Log out
        </button>
      </div>
    </div>
  )

  return (
    <div className="flex h-full">
      <aside className="hidden w-72 shrink-0 md:block">{sidebar}</aside>

      {menuOpen && (
        <div className="fixed inset-0 z-40 md:hidden" role="dialog" aria-modal="true">
          <div className="absolute inset-0 bg-navy-950/60 backdrop-blur-sm" onClick={() => setMenuOpen(false)} />
          <aside className="relative h-full w-72 shadow-2xl">{sidebar}</aside>
        </div>
      )}

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex items-center gap-3 border-b border-slate-200 bg-white/80 px-4 py-3 backdrop-blur md:hidden">
          <button onClick={() => setMenuOpen(true)} className="rounded-lg p-2 text-slate-700 hover:bg-slate-100"
                  aria-label="Open menu">
            <Icon name="menu" className="h-6 w-6" />
          </button>
          <Logo />
        </header>
        <main className="flex-1 overflow-y-auto px-4 py-6 md:px-10 md:py-10">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
