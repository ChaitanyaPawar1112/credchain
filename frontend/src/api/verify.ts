import { apiRequest } from './client'

/** What a verifier is told about a certificate (backend VerificationStatus). */
export type VerificationStatus = 'VALID' | 'REVOKED' | 'EXPIRED' | 'NOT_FOUND' | 'FAKE'

export interface CertificateDetails {
  certificateNumber: string
  type: string
  title: string
  program: string | null
  grade: string | null
  cgpa: string | null
  awardedOn: string | null
  studentName: string
  enrollmentNo: string | null
  institutionName: string
  institutionCode: string
  expiresAt: string | null
}

export interface BlockchainRecord {
  chainId: number | null
  contractAddress: string | null
  merkleRoot: string | null
  issuerAddress: string | null
  txHash: string | null
  blockNumber: number | null
  anchoredAt: string | null
  explorerTxUrl: string | null
}

export interface VerificationResult {
  certHash: string
  status: VerificationStatus
  message: string
  blockchainChecked: boolean
  checkedAt: string
  certificate: CertificateDetails | null
  revocation: { reason: string; revokedAt: string } | null
  blockchain: BlockchainRecord | null
}

export interface PdfCheck {
  name: string
  result: 'PASSED' | 'FAILED' | 'SKIPPED'
  detail: string | null
}

export interface PdfVerificationResult {
  status: VerificationStatus
  message: string
  fileSha256: string
  checkedAt: string
  checks: PdfCheck[]
  /** The official record, also when the file was edited (to compare); null when the PDF is not linked to one. */
  record: VerificationResult | null
}

/** Same limit as spring.servlet.multipart.max-file-size. */
export const MAX_PDF_BYTES = 2 * 1024 * 1024

export const verifyApi = {
  byHash: (certHash: string, signal?: AbortSignal) =>
    apiRequest<VerificationResult>(`/api/v1/public/verify/${encodeURIComponent(certHash)}`, { auth: false, signal }),

  byPdf: (file: File) => {
    const form = new FormData()
    form.append('file', file)
    return apiRequest<PdfVerificationResult>('/api/v1/public/verify/pdf', { method: 'POST', body: form, auth: false })
  },
}

/**
 * Accepts a bare hash or a pasted QR link (".../verify/0xabc...") and returns just the hash.
 * The backend does the real validation.
 */
export function extractHash(input: string): string {
  const value = input.trim()
  const match = value.match(/\/verify\/([^/?#\s]+)/)
  return match ? decodeURIComponent(match[1]!) : value
}
