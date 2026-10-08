import type { Role } from '../api/types'
import type { IconName } from '../components/Icon'

export interface NavItem {
  label: string
  to: string
  icon: IconName
  /** One line shown on the dashboard shortcut card. */
  description?: string
  /** Built in a later Phase 6 step: shown with a "soon" badge until then. */
  soon?: string
}

/** Sidebar links per role. */
export const NAV_BY_ROLE: Record<Role, NavItem[]> = {
  SUPER_ADMIN: [
    { label: 'Overview', to: '/admin', icon: 'home' },
    { label: 'College applications', to: '/admin/institutions', icon: 'building', soon: '6.3',
      description: 'Review, approve or reject colleges that want to issue certificates.' },
    { label: 'Users', to: '/admin/users', icon: 'users', soon: '6.3',
      description: 'See every account and lock or unlock access.' },
    { label: 'Blockchain check', to: '/admin/reconciliation', icon: 'chain', soon: '6.3',
      description: 'Compare the database with what is recorded on Ethereum.' },
    { label: 'Verification log', to: '/admin/verifications', icon: 'activity', soon: '6.3',
      description: 'Every public check, including FAKE attempts.' },
  ],
  INSTITUTION_ADMIN: [
    { label: 'Overview', to: '/institution', icon: 'home' },
    { label: 'Students', to: '/institution/students', icon: 'student', soon: '6.4',
      description: 'Add students one by one or import a CSV file.' },
    { label: 'Batches', to: '/institution/batches', icon: 'layers', soon: '6.4',
      description: 'Group certificates and issue them on the blockchain together.' },
    { label: 'Certificates', to: '/institution/certificates', icon: 'certificate', soon: '6.4',
      description: 'Download PDFs, check status and revoke if needed.' },
    { label: 'Verification activity', to: '/institution/verifications', icon: 'activity', soon: '6.4',
      description: 'See when employers check your certificates.' },
  ],
  STUDENT: [
    { label: 'Overview', to: '/student', icon: 'home' },
    { label: 'My certificates', to: '/student/certificates', icon: 'certificate', soon: '6.5',
      description: 'Claim, download and share the certificates your college issued.' },
  ],
  VERIFIER: [
    { label: 'Overview', to: '/verifier', icon: 'home' },
    { label: 'Verify a certificate', to: '/verify', icon: 'shield',
      description: 'Paste a certificate hash or upload the PDF a candidate sent you.' },
  ],
}
