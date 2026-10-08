import { useState } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { ROLE_LABEL } from '../auth/roles'
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
    <div className="flex h-full flex-col bg-navy-900 text-white">
      <div className="px-5 py-5">
        <Logo light />
      </div>
      <nav className="flex-1 space-y-1 px-3" aria-label="Main">
        {NAV_BY_ROLE[user.role].map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end
            onClick={() => setMenuOpen(false)}
            className={({ isActive }) =>
              `flex items-center justify-between rounded-lg px-3 py-2 text-sm font-medium transition ${
                isActive ? 'bg-navy-700 text-white' : 'text-navy-100 hover:bg-navy-800 hover:text-white'}`}
          >
            {item.label}
            {item.soon && (
              <span className="rounded bg-navy-800 px-1.5 py-0.5 text-[10px] font-semibold uppercase text-gold-400">soon</span>
            )}
          </NavLink>
        ))}
      </nav>
      <div className="border-t border-navy-800 px-5 py-4">
        <p className="truncate text-sm font-semibold">{user.fullName}</p>
        <p className="truncate text-xs text-navy-100">{user.email}</p>
        <p className="mt-1 text-xs text-gold-400">{ROLE_LABEL[user.role]}</p>
        <button onClick={handleLogout}
                className="mt-3 w-full rounded-lg bg-navy-800 px-3 py-2 text-sm font-medium hover:bg-navy-700">
          Log out
        </button>
      </div>
    </div>
  )

  return (
    <div className="flex h-full">
      <aside className="hidden w-64 shrink-0 md:block">{sidebar}</aside>

      {menuOpen && (
        <div className="fixed inset-0 z-40 md:hidden" role="dialog" aria-modal="true">
          <div className="absolute inset-0 bg-black/40" onClick={() => setMenuOpen(false)} />
          <aside className="relative h-full w-64">{sidebar}</aside>
        </div>
      )}

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex items-center gap-3 border-b border-slate-200 bg-white px-4 py-3 md:hidden">
          <button onClick={() => setMenuOpen(true)} className="rounded-md p-2 text-slate-700 hover:bg-slate-100"
                  aria-label="Open menu">
            <svg viewBox="0 0 24 24" className="h-6 w-6" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M4 6h16M4 12h16M4 18h16" strokeLinecap="round" />
            </svg>
          </button>
          <Logo />
        </header>
        <main className="flex-1 overflow-y-auto px-4 py-6 md:px-8 md:py-8">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
