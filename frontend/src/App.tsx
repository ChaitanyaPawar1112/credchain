import { Route, Routes } from 'react-router'
import { RequireAuth } from './auth/RequireAuth'
import { AppLayout } from './layouts/AppLayout'
import { PublicLayout } from './layouts/PublicLayout'
import { NAV_BY_ROLE } from './layouts/navigation'
import { ChangePasswordPage } from './pages/ChangePasswordPage'
import { ComingSoonPage } from './pages/ComingSoonPage'
import { DashboardHome } from './pages/DashboardHome'
import { HomePage } from './pages/HomePage'
import { LoginPage } from './pages/LoginPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { RegisterPage } from './pages/RegisterPage'
import { VerifyPage } from './pages/verify/VerifyPage'
import type { Role } from './api/types'

/** One dashboard area per role: /admin, /institution, /student, /verifier. */
const AREAS: { role: Role; path: string }[] = [
  { role: 'SUPER_ADMIN', path: '/admin' },
  { role: 'INSTITUTION_ADMIN', path: '/institution' },
  { role: 'STUDENT', path: '/student' },
  { role: 'VERIFIER', path: '/verifier' },
]

export function App() {
  return (
    <Routes>
      <Route element={<PublicLayout />}>
        <Route index element={<HomePage />} />
        <Route path="verify" element={<VerifyPage />} />
        <Route path="verify/:certHash" element={<VerifyPage />} />
        <Route path="login" element={<LoginPage />} />
        <Route path="register" element={<RegisterPage />} />
        <Route path="change-password" element={<RequireAuth><ChangePasswordPage /></RequireAuth>} />
      </Route>

      {AREAS.map(({ role, path }) => (
        <Route key={role} path={path} element={<RequireAuth roles={[role]}><AppLayout /></RequireAuth>}>
          <Route index element={<DashboardHome />} />
          {NAV_BY_ROLE[role]
            .filter((item) => item.soon && item.to.startsWith(`${path}/`))
            .map((item) => (
              <Route key={item.to} path={item.to.slice(path.length + 1)} element={<ComingSoonPage />} />
            ))}
        </Route>
      ))}

      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}
