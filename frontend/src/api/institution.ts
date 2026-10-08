import { apiRequest } from './client'
import type { Institution, InstitutionWallet, VerificationActivity } from './admin'
import type { PageResponse } from './types'
import type { VerificationStatus } from './verify'

export type StudentStatus = 'ACTIVE' | 'GRADUATED' | 'WITHDRAWN'

export interface Student {
  id: string
  enrollmentNo: string
  fullName: string
  email: string | null
  dateOfBirth: string | null
  program: string
  department: string | null
  admissionYear: number
  graduationYear: number | null
  status: StudentStatus
  accountLinked: boolean
  linkedAt: string | null
  createdAt: string
}

export interface NewStudent {
  enrollmentNo: string
  fullName: string
  email?: string
  dateOfBirth?: string
  program: string
  department?: string
  admissionYear: number
  graduationYear?: number
}

export interface ClaimCode {
  studentId: string
  enrollmentNo: string
  claimCode: string
  expiresAt: string
  note: string
}

export interface StudentImportResult {
  totalRows: number
  valid: number
  imported: number
  failed: number
  dryRun: boolean
  errors: { row: number; enrollmentNo: string | null; messages: string[] }[]
}

export type BatchStatus = 'DRAFT' | 'QUEUED' | 'SUBMITTED' | 'ANCHORED' | 'REVOKED'

export interface Batch {
  id: string
  title: string
  status: BatchStatus
  certificateCount: number
  expiresAt: string | null
  merkleRoot: string | null
  issuerAddress: string | null
  txHash: string | null
  blockNumber: number | null
  queuedAt: string | null
  anchoredAt: string | null
  attempts: number
  lastError: string | null
  explorerTxUrl: string | null
}

export type CertificateType = 'DEGREE' | 'DIPLOMA' | 'CERTIFICATE' | 'TRANSCRIPT' | 'PROVISIONAL' | 'OTHER'
export type CertificateStatus = 'DRAFT' | 'PENDING' | 'ISSUED' | 'REVOCATION_PENDING' | 'REVOKED'
export type RevocationReason = 'ISSUED_IN_ERROR' | 'FRAUD' | 'SUPERSEDED' | 'OTHER'

export interface Certificate {
  id: string
  batchId: string
  studentId: string
  certificateNumber: string
  type: CertificateType
  title: string
  program: string | null
  grade: string | null
  cgpa: number | null
  awardedOn: string
  studentName: string
  enrollmentNo: string
  certHash: string | null
  status: CertificateStatus
  revocationReason: RevocationReason | null
  revokedAt: string | null
  pdfAvailable: boolean
}

export interface BulkCertificates {
  type: CertificateType
  title: string
  program?: string
  awardedOn: string
  items: { studentId: string; grade?: string; cgpa?: number }[]
}

const query = (params: Record<string, string | number | undefined>) => {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== '') search.set(key, String(value))
  }
  return search.toString()
}

const BASE = '/api/v1/institution'

export const institutionApi = {
  profile: () => apiRequest<Institution>(`${BASE}/profile`),
  wallet: () => apiRequest<InstitutionWallet>(`${BASE}/wallet`),

  students: (q: string, page: number, size = 20) => apiRequest<PageResponse<Student>>(`${BASE}/students?${query({ q, page, size })}`),
  addStudent: (student: NewStudent) => apiRequest<Student>(`${BASE}/students`, { method: 'POST', body: student }),
  claimCode: (studentId: string) => apiRequest<ClaimCode>(`${BASE}/students/${studentId}/claim-code`, { method: 'POST' }),
  importStudents: (file: File, dryRun: boolean) => {
    const form = new FormData()
    form.append('file', file)
    return apiRequest<StudentImportResult>(`${BASE}/students/import?dryRun=${dryRun}`, { method: 'POST', body: form })
  },
  studentTemplate: () => apiRequest<Blob>(`${BASE}/students/import/template`, { accept: 'text/csv, application/json' }),

  batches: (page: number, size = 20) => apiRequest<PageResponse<Batch>>(`${BASE}/certificate-batches?${query({ page, size })}`),
  batch: (id: string) => apiRequest<{ batch: Batch; certificates: Certificate[] }>(`${BASE}/certificate-batches/${id}`),
  createBatch: (title: string, expiresAt?: string) =>
    apiRequest<Batch>(`${BASE}/certificate-batches`, { method: 'POST', body: { title, expiresAt } }),
  deleteBatch: (id: string) => apiRequest<void>(`${BASE}/certificate-batches/${id}`, { method: 'DELETE' }),
  addCertificates: (batchId: string, body: BulkCertificates) =>
    apiRequest<Certificate[]>(`${BASE}/certificate-batches/${batchId}/certificates/bulk`, { method: 'POST', body }),
  removeCertificate: (batchId: string, certificateId: string) =>
    apiRequest<void>(`${BASE}/certificate-batches/${batchId}/certificates/${certificateId}`, { method: 'DELETE' }),
  issueBatch: (id: string) => apiRequest<Batch>(`${BASE}/certificate-batches/${id}/issue`, { method: 'POST' }),

  certificates: (status: CertificateStatus | undefined, search: string, page: number, size = 20) =>
    apiRequest<PageResponse<Certificate>>(`${BASE}/certificates?${query({ status, search, page, size })}`),
  certificatePdf: (id: string) => apiRequest<Blob>(`${BASE}/certificates/${id}/pdf`, { accept: 'application/pdf, application/json' }),
  revoke: (id: string, reason: RevocationReason, note?: string) =>
    apiRequest<Certificate>(`${BASE}/certificates/${id}/revoke`, { method: 'POST', body: { reason, note } }),

  verifications: (result: VerificationStatus | undefined, page: number, size = 20) =>
    apiRequest<VerificationActivity>(`${BASE}/verifications?${query({ result, page, size })}`),
}

/** Saves a downloaded file (PDF, CSV) through the browser's normal download. */
export function saveFile(blob: Blob, fileName: string) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  link.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
