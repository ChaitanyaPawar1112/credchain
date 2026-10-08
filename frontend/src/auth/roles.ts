import type { Role } from '../api/types'

/** Where each role lands after logging in. */
export const HOME_BY_ROLE: Record<Role, string> = {
  SUPER_ADMIN: '/admin',
  INSTITUTION_ADMIN: '/institution',
  STUDENT: '/student',
  VERIFIER: '/verifier',
}

export const ROLE_LABEL: Record<Role, string> = {
  SUPER_ADMIN: 'Super admin',
  INSTITUTION_ADMIN: 'College admin',
  STUDENT: 'Student',
  VERIFIER: 'Verifier',
}
