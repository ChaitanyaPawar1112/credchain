import { Route, Routes } from 'react-router'
import { RequireAuth } from './auth/RequireAuth'
import { AppLayout } from './layouts/AppLayout'
import { PublicLayout } from './layouts/PublicLayout'
import { ChangePasswordPage } from './pages/ChangePasswordPage'
import { ApplyCollegePage } from './pages/ApplyCollegePage'
import { DashboardHome } from './pages/DashboardHome'
import { HomePage } from './pages/HomePage'
import { LoginPage } from './pages/LoginPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { RegisterPage } from './pages/RegisterPage'
import { InstitutionDetailPage } from './pages/admin/InstitutionDetailPage'
import { InstitutionsPage } from './pages/admin/InstitutionsPage'
import { ReconciliationPage } from './pages/admin/ReconciliationPage'
import { UsersPage } from './pages/admin/UsersPage'
import { VerificationLogPage } from './pages/admin/VerificationLogPage'
import { BatchDetailPage } from './pages/institution/BatchDetailPage'
import { BatchesPage } from './pages/institution/BatchesPage'
import { CertificatesPage } from './pages/institution/CertificatesPage'
import { StudentsPage } from './pages/institution/StudentsPage'
import { MyCertificatesPage } from './pages/student/MyCertificatesPage'
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
        <Route path="apply" element={<ApplyCollegePage />} />
        <Route path="change-password" element={<RequireAuth><ChangePasswordPage /></RequireAuth>} />
      </Route>

      {AREAS.map(({ role, path }) => (
        <Route key={role} path={path} element={<RequireAuth roles={[role]}><AppLayout /></RequireAuth>}>
          <Route index element={<DashboardHome />} />
          {role === 'SUPER_ADMIN' && (
            <>
              <Route path="institutions" element={<InstitutionsPage />} />
              <Route path="institutions/:id" element={<InstitutionDetailPage />} />
              <Route path="users" element={<UsersPage />} />
              <Route path="reconciliation" element={<ReconciliationPage />} />
              <Route path="verifications" element={<VerificationLogPage />} />
            </>
          )}
          {role === 'INSTITUTION_ADMIN' && (
            <>
              <Route path="students" element={<StudentsPage />} />
              <Route path="batches" element={<BatchesPage />} />
              <Route path="batches/:id" element={<BatchDetailPage />} />
              <Route path="certificates" element={<CertificatesPage />} />
              <Route path="verifications" element={<VerificationLogPage scope="institution" />} />
            </>
          )}
          {role === 'STUDENT' && <Route path="certificates" element={<MyCertificatesPage />} />}
        </Route>
      ))}

      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}
