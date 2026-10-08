import { apiRequest } from './client'
import type { PageResponse, Role } from './types'
import type { VerificationStatus } from './verify'

export type InstitutionStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'SUSPENDED'
export type InstitutionType = 'UNIVERSITY' | 'COLLEGE' | 'SCHOOL' | 'BOARD' | 'TRAINING_INSTITUTE'

export interface Institution {
  id: string
  name: string
  code: string
  registrationNumber: string
  type: InstitutionType
  email: string
  phone: string | null
  website: string | null
  addressLine: string | null
  city: string
  state: string
  country: string | null
  postalCode: string | null
  contactPersonName: string
  contactPersonEmail: string
  walletAddress: string | null
  status: InstitutionStatus
  rejectionReason: string | null
  reviewedAt: string | null
  createdAt: string
}

export interface InstitutionApproval {
  institution: Institution
  adminAccount: { userId: string; email: string; temporaryPassword: string; note: string }
}

export type WalletStatus = 'PENDING_ACTIVATION' | 'ACTIVE' | 'PENDING_DEACTIVATION' | 'INACTIVE'

export interface InstitutionWallet {
  institutionId: string
  address: string
  custody: string
  status: WalletStatus
  canIssue: boolean
  balanceEth: string | null
  fundingTxHash: string | null
  grantTxHash: string | null
  revokeTxHash: string | null
  activatedAt: string | null
  attempts: number
  nextAttemptAt: string | null
  lastError: string | null
  explorerUrl: string | null
}

export type UserStatus = 'PENDING_VERIFICATION' | 'ACTIVE' | 'SUSPENDED' | 'DELETED'

export interface AdminUser {
  id: string
  email: string
  fullName: string
  phone: string | null
  role: Role
  status: UserStatus
  emailVerified: boolean
  lastLoginAt: string | null
  createdAt: string
}

export interface ReconciliationReport {
  onChainCertificates: number
  neverChecked: number
  mismatches: number
  mismatchList: PageResponse<{
    certificateId: string
    institutionId: string
    certificateNumber: string
    studentName: string
    databaseStatus: string
    note: string | null
    checkedAt: string | null
  }>
}

export interface VerificationEntry {
  id: string
  checkedAt: string
  method: 'HASH' | 'PDF'
  result: VerificationStatus
  blockchainChecked: boolean
  certHash: string | null
  certificateId: string | null
  certificateNumber: string | null
  studentName: string | null
  institutionId: string | null
  userAgent: string | null
}

export interface VerificationActivity {
  totalChecks: number
  byResult: Record<VerificationStatus, number>
  checks: PageResponse<VerificationEntry>
}

const query = (params: Record<string, string | number | undefined>) => {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== '') search.set(key, String(value))
  }
  return search.toString()
}

export const PAGE_SIZE = 20

export const adminApi = {
  institutions: (status: InstitutionStatus | undefined, page: number, size = PAGE_SIZE) =>
    apiRequest<PageResponse<Institution>>(`/api/v1/admin/institutions?${query({ status, page, size })}`),
  institution: (id: string) => apiRequest<Institution>(`/api/v1/admin/institutions/${id}`),
  approve: (id: string) => apiRequest<InstitutionApproval>(`/api/v1/admin/institutions/${id}/approve`, { method: 'POST' }),
  reject: (id: string, reason: string) =>
    apiRequest<Institution>(`/api/v1/admin/institutions/${id}/reject`, { method: 'POST', body: { reason } }),
  suspend: (id: string) => apiRequest<Institution>(`/api/v1/admin/institutions/${id}/suspend`, { method: 'POST' }),
  reinstate: (id: string) => apiRequest<Institution>(`/api/v1/admin/institutions/${id}/reinstate`, { method: 'POST' }),
  wallet: (institutionId: string) => apiRequest<InstitutionWallet>(`/api/v1/admin/institutions/${institutionId}/wallet`),

  users: (page: number, size = PAGE_SIZE) => apiRequest<PageResponse<AdminUser>>(`/api/v1/admin/users?${query({ page, size })}`),

  reconciliation: (page: number, size = PAGE_SIZE) =>
    apiRequest<ReconciliationReport>(`/api/v1/admin/reconciliation?${query({ page, size })}`),

  verifications: (result: VerificationStatus | undefined, page: number, size = PAGE_SIZE) =>
    apiRequest<VerificationActivity>(`/api/v1/admin/verifications?${query({ result, page, size })}`),
}

export interface InstitutionApplication {
  name: string
  code: string
  registrationNumber: string
  type: InstitutionType
  email: string
  phone?: string
  website?: string
  addressLine?: string
  city: string
  state: string
  postalCode?: string
  contactPersonName: string
  contactPersonEmail: string
}

/** Public: a college applies to join (no login). */
export const applicationApi = {
  apply: (application: InstitutionApplication) =>
    apiRequest<Institution>('/api/v1/institutions/applications', { method: 'POST', body: application, auth: false }),
}
