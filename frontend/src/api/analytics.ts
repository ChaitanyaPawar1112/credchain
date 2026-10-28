import { apiRequest } from './client'
import type { VerificationStatus } from './verify'

export type AnalyticsDays = 7 | 30 | 90

/** One day of the charts. failed = checks answered "fake" or "not found". */
export interface AnalyticsDay {
  date: string
  issued: number
  revoked: number
  checks: number
  valid: number
  failed: number
}

export interface TopCollege {
  institutionId: string
  name: string
  code: string
  issued: number
  checks: number
  fakeChecks: number
}

/** The dashboard (backend AnalyticsResponse). Totals are all-time; the rest covers from..to. */
export interface Analytics {
  from: string
  to: string
  totals: {
    /** Super admin only (null for a college). */
    colleges: number | null
    pendingApplications: number | null
    students: number
    studentsWithAccount: number
    certificatesIssued: number
    certificatesRevoked: number
    checksInPeriod: number
    fakeChecksInPeriod: number
  }
  daily: AnalyticsDay[]
  checksByResult: Record<VerificationStatus, number>
  checksByMethod: Record<'HASH' | 'PDF' | 'DOCUMENT', number>
  /** Super admin only. */
  topColleges?: TopCollege[]
}

export const analyticsApi = {
  platform: (days: AnalyticsDays) => apiRequest<Analytics>(`/api/v1/admin/analytics?days=${days}`),
  institution: (days: AnalyticsDays) => apiRequest<Analytics>(`/api/v1/institution/analytics?days=${days}`),
}
