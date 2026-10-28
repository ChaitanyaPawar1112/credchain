import { apiRequest } from './client'
import type { PageResponse } from './types'

export type AuditCategory = 'ACCOUNT' | 'INSTITUTION' | 'STUDENT' | 'CERTIFICATE' | 'SYSTEM'

export type AuditAction =
  | 'USER_REGISTERED' | 'LOGIN_SUCCEEDED' | 'LOGIN_FAILED' | 'LOGIN_BLOCKED' | 'PASSWORD_CHANGED' | 'LOGGED_OUT_EVERYWHERE'
  | 'INSTITUTION_APPLIED' | 'INSTITUTION_APPROVED' | 'INSTITUTION_REJECTED' | 'INSTITUTION_SUSPENDED' | 'INSTITUTION_REINSTATED'
  | 'STUDENT_ADDED' | 'STUDENTS_IMPORTED' | 'CLAIM_CODE_ISSUED' | 'STUDENT_ACCOUNT_LINKED'
  | 'BATCH_CREATED' | 'BATCH_DELETED' | 'BATCH_ISSUED' | 'CERTIFICATE_REVOKED'
  | 'TRUST_SCORES_RECOMPUTED'

/** One audit log entry (backend AuditEventResponse). */
export interface AuditEntry {
  id: string
  occurredAt: string
  action: AuditAction
  category: AuditCategory
  actorUserId: string | null
  /** Email at the time; for a failed login, the email that was typed. */
  actorEmail: string | null
  /** SUPER_ADMIN, INSTITUTION_ADMIN, STUDENT, VERIFIER, SYSTEM, or null for anonymous requests. */
  actorRole: string | null
  institutionId: string | null
  targetType: string | null
  targetId: string | null
  summary: string
  ipAddress: string | null
}

const path = (base: string, category: AuditCategory | undefined, page: number, size: number) => {
  const search = new URLSearchParams({ page: String(page), size: String(size) })
  if (category) search.set('category', category)
  return `${base}?${search}`
}

export const auditApi = {
  /** Super admin: everything. */
  platform: (category: AuditCategory | undefined, page: number, size = 20) =>
    apiRequest<PageResponse<AuditEntry>>(path('/api/v1/admin/audit', category, page, size)),
  /** College admin: their own college. */
  institution: (category: AuditCategory | undefined, page: number, size = 20) =>
    apiRequest<PageResponse<AuditEntry>>(path('/api/v1/institution/audit', category, page, size)),
}
