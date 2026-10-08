import { Link, NavLink, Outlet } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { HOME_BY_ROLE } from '../auth/roles'
import { Logo } from '../components/Logo'

/** Pages anyone can see: home, verify, login, register. */
export function PublicLayout() {
  const { user } = useAuth()
  const link = ({ isActive }: { isActive: boolean }) =>
    `rounded-md px-3 py-2 text-sm font-medium ${isActive ? 'text-navy-700' : 'text-slate-600 hover:text-navy-700'}`

  return (
    <div className="flex min-h-full flex-col">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-4 py-3">
          <Logo />
          <nav className="flex items-center gap-1">
            <NavLink to="/verify" className={link}>Verify</NavLink>
            {user ? (
              <Link to={HOME_BY_ROLE[user.role]}
                    className="ml-2 rounded-lg bg-navy-700 px-4 py-2 text-sm font-semibold text-white hover:bg-navy-800">
                My dashboard
              </Link>
            ) : (
              <>
                <NavLink to="/login" className={link}>Log in</NavLink>
                <Link to="/register"
                      className="ml-2 rounded-lg bg-navy-700 px-4 py-2 text-sm font-semibold text-white hover:bg-navy-800">
                  Sign up
                </Link>
              </>
            )}
          </nav>
        </div>
      </header>
      <main className="flex-1">
        <Outlet />
      </main>
      <footer className="border-t border-slate-200 bg-white py-6 text-center text-xs text-slate-500">
        CredChain · Certificates recorded on the Ethereum blockchain
      </footer>
    </div>
  )
}
