import { Link, NavLink, Outlet } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { HOME_BY_ROLE } from '../auth/roles'
import { Logo } from '../components/Logo'

/** Pages anyone can see: home, verify, login, register. */
export function PublicLayout() {
  const { user } = useAuth()
  const link = ({ isActive }: { isActive: boolean }) =>
    `whitespace-nowrap rounded-lg px-3 py-2 text-sm font-medium transition ${isActive
      ? 'bg-navy-50 text-navy-700' : 'text-slate-600 hover:bg-slate-100 hover:text-navy-700'}`
  const cta = 'ml-1 whitespace-nowrap rounded-lg bg-navy-700 px-4 py-2 text-sm font-semibold text-white shadow-sm transition hover:bg-navy-800'

  return (
    <div className="flex min-h-full flex-col">
      <header className="sticky top-0 z-30 border-b border-slate-200/80 bg-white/80 backdrop-blur-md">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-4 py-3">
          <Logo />
          <nav className="flex items-center gap-1">
            <NavLink to="/verify" className={(s) => `hidden sm:block ${link(s)}`}>Verify</NavLink>
            {user ? (
              <Link to={HOME_BY_ROLE[user.role]} className={cta}>My dashboard</Link>
            ) : (
              <>
                <NavLink to="/login" className={link}>Log in</NavLink>
                <Link to="/register" className={cta}>Sign up</Link>
              </>
            )}
          </nav>
        </div>
      </header>
      <main className="flex-1">
        <Outlet />
      </main>
      <footer className="border-t border-slate-200 bg-white">
        <div className="mx-auto flex max-w-6xl flex-col items-center justify-between gap-3 px-4 py-6 sm:flex-row">
          <Logo />
          <p className="text-xs text-slate-500">Tamper-proof academic certificates, recorded on the Ethereum blockchain.</p>
          <nav className="flex gap-4 text-xs font-medium text-slate-600">
            <Link to="/verify" className="hover:text-navy-700">Verify</Link>
            <Link to="/login" className="hover:text-navy-700">Log in</Link>
            <Link to="/register" className="hover:text-navy-700">Sign up</Link>
          </nav>
        </div>
      </footer>
    </div>
  )
}
