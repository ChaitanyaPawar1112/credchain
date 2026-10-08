import { apiRequest } from './client'
import type { CertificateStatus, CertificateType, RevocationReason, Student } from './institution'
import type { PageResponse } from './types'

/** The student's official record at their college, once their account is linked with a claim code. */
export interface MyStudentProfile {
  record: Student
  institutionId: string
  institutionName: string
  institutionCode: string
}

export interface LinkRequest {
  institutionCode: string
  enrollmentNo: string
  claimCode: string
}

/** One of the student's own certificates. Only certificates already on the blockchain are listed. */
export interface MyCertificate {
  id: string
  certificateNumber: string
  institutionName: string
  type: CertificateType
  title: string
  program: string | null
  grade: string | null
  cgpa: number | null
  awardedOn: string
  studentName: string
  enrollmentNo: string
  certHash: string
  status: CertificateStatus
  revocationReason: RevocationReason | null
  revokedAt: string | null
  pdfAvailable: boolean
  /** Public verify page for this certificate (the same link as the QR code on the PDF). */
  verificationUrl: string
}

export const studentApi = {
  profile: () => apiRequest<MyStudentProfile>('/api/v1/me/student-profile'),
  link: (body: LinkRequest) => apiRequest<MyStudentProfile>('/api/v1/me/student-profile/link', { method: 'POST', body }),
  certificates: (page: number, size = 20) => apiRequest<PageResponse<MyCertificate>>(`/api/v1/me/certificates?page=${page}&size=${size}`),
  certificatePdf: (id: string) => apiRequest<Blob>(`/api/v1/me/certificates/${id}/pdf`, { accept: 'application/pdf, application/json' }),
}
