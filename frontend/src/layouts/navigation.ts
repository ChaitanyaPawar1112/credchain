import type { Role } from '../api/types'

export interface NavItem {
  label: string
  to: string
  /** Built in a later Phase 6 step: shown with a "soon" badge until then. */
  soon?: string
}

/** Sidebar links per role. */
export const NAV_BY_ROLE: Record<Role, NavItem[]> = {
  SUPER_ADMIN: [
    { label: 'Overview', to: '/admin' },
    { label: 'College applications', to: '/admin/institutions', soon: '6.3' },
    { label: 'Users', to: '/admin/users', soon: '6.3' },
    { label: 'Blockchain check', to: '/admin/reconciliation', soon: '6.3' },
    { label: 'Verification log', to: '/admin/verifications', soon: '6.3' },
  ],
  INSTITUTION_ADMIN: [
    { label: 'Overview', to: '/institution' },
    { label: 'Students', to: '/institution/students', soon: '6.4' },
    { label: 'Batches', to: '/institution/batches', soon: '6.4' },
    { label: 'Certificates', to: '/institution/certificates', soon: '6.4' },
    { label: 'Verification activity', to: '/institution/verifications', soon: '6.4' },
  ],
  STUDENT: [
    { label: 'Overview', to: '/student' },
    { label: 'My certificates', to: '/student/certificates', soon: '6.5' },
  ],
  VERIFIER: [
    { label: 'Overview', to: '/verifier' },
    { label: 'Verify a certificate', to: '/verify', soon: '6.2' },
  ],
}
