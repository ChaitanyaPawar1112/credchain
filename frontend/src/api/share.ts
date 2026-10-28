import { apiRequest } from './client'
import type { PageResponse } from './types'
import type { VerificationResult } from './verify'

export type ShareLinkStatus = 'ACTIVE' | 'EXPIRED' | 'REVOKED'

/** One of the student's named links (backend ShareLinkResponse). */
export interface ShareLink {
  id: string
  /** Who the link is for; only the student sees it. */
  name: string
  url: string
  certificateId: string
  certificateNumber: string | null
  certificateTitle: string | null
  status: ShareLinkStatus
  createdAt: string
  expiresAt: string
  revokedAt: string | null
  viewCount: number
  lastViewedAt: string | null
}

export interface CreateShareLink {
  certificateId: string
  name: string
  expiresInDays: number
}

/** What someone opening a link sees (backend SharedCertificateResponse). */
export interface SharedCertificate {
  verification: VerificationResult
  linkExpiresAt: string
  pdfAvailable: boolean
}

export const shareApi = {
  create: (body: CreateShareLink) => apiRequest<ShareLink>('/api/v1/me/share-links', { method: 'POST', body }),
  list: (page: number, size = 20) => apiRequest<PageResponse<ShareLink>>(`/api/v1/me/share-links?page=${page}&size=${size}`),
  revoke: (id: string) => apiRequest<ShareLink>(`/api/v1/me/share-links/${id}/revoke`, { method: 'POST' }),
  open: (token: string, signal?: AbortSignal) =>
    apiRequest<SharedCertificate>(`/api/v1/public/share/${encodeURIComponent(token)}`, { auth: false, signal }),
  pdf: (token: string) =>
    apiRequest<Blob>(`/api/v1/public/share/${encodeURIComponent(token)}/pdf`, { auth: false, accept: 'application/pdf, application/json' }),
}

/** Choices offered when creating a link. */
export const EXPIRY_OPTIONS = [
  { value: '7', label: '7 days' },
  { value: '30', label: '30 days' },
  { value: '90', label: '3 months' },
  { value: '365', label: '1 year' },
]
