import type { InstitutionStatus, InstitutionType, UserStatus, WalletStatus } from '../../api/admin'
import type { VerificationStatus } from '../../api/verify'
import type { BadgeTone } from '../../components/Badge'

export const INSTITUTION_STATUS: Record<InstitutionStatus, { label: string; tone: BadgeTone }> = {
  PENDING: { label: 'Waiting for review', tone: 'amber' },
  APPROVED: { label: 'Approved', tone: 'green' },
  REJECTED: { label: 'Rejected', tone: 'red' },
  SUSPENDED: { label: 'Suspended', tone: 'slate' },
}

export const INSTITUTION_TYPE: Record<InstitutionType, string> = {
  UNIVERSITY: 'University',
  COLLEGE: 'College',
  SCHOOL: 'School',
  BOARD: 'Education board',
  TRAINING_INSTITUTE: 'Training institute',
}

export const USER_STATUS: Record<UserStatus, { label: string; tone: BadgeTone }> = {
  PENDING_VERIFICATION: { label: 'Email not verified', tone: 'amber' },
  ACTIVE: { label: 'Active', tone: 'green' },
  SUSPENDED: { label: 'Suspended', tone: 'red' },
  DELETED: { label: 'Deleted', tone: 'slate' },
}

export const WALLET_STATUS: Record<WalletStatus, { label: string; tone: BadgeTone }> = {
  PENDING_ACTIVATION: { label: 'Being activated', tone: 'amber' },
  ACTIVE: { label: 'Active, can issue', tone: 'green' },
  PENDING_DEACTIVATION: { label: 'Being switched off', tone: 'amber' },
  INACTIVE: { label: 'Switched off', tone: 'slate' },
}

export const RESULT_LOOK: Record<VerificationStatus, { label: string; tone: BadgeTone }> = {
  VALID: { label: 'Genuine', tone: 'green' },
  REVOKED: { label: 'Revoked', tone: 'red' },
  EXPIRED: { label: 'Expired', tone: 'amber' },
  NOT_FOUND: { label: 'Not found', tone: 'slate' },
  FAKE: { label: 'Fake', tone: 'red' },
}

export const ROLE_TONE = {
  SUPER_ADMIN: 'purple',
  INSTITUTION_ADMIN: 'blue',
  STUDENT: 'gold',
  VERIFIER: 'green',
} as const satisfies Record<string, BadgeTone>

/** "Mozilla/5.0 (Windows NT 10.0; ...) Chrome/129" -> "Chrome on Windows" (best effort). */
export function describeBrowser(userAgent: string | null) {
  if (!userAgent) return 'Unknown'
  const browser = /Edg\//.test(userAgent) ? 'Edge' : /Chrome\//.test(userAgent) ? 'Chrome'
    : /Firefox\//.test(userAgent) ? 'Firefox' : /Safari\//.test(userAgent) ? 'Safari' : null
  const os = /Android/.test(userAgent) ? 'Android' : /iPhone|iPad/.test(userAgent) ? 'iPhone' : /Windows/.test(userAgent) ? 'Windows'
    : /Mac OS X/.test(userAgent) ? 'Mac' : /Linux/.test(userAgent) ? 'Linux' : null
  if (browser && os) return `${browser} on ${os}`
  return browser ?? os ?? userAgent.slice(0, 40)
}
