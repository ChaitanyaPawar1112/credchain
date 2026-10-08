import type { IconName } from '../../components/Icon'
import type { VerificationStatus } from '../../api/verify'

export interface StatusLook {
  title: string
  icon: IconName
  /** Banner colours. */
  banner: string
  /** Icon circle colours. */
  badge: string
}

export const STATUS_LOOK: Record<VerificationStatus, StatusLook> = {
  VALID: {
    title: 'Genuine certificate',
    icon: 'checkCircle',
    banner: 'bg-gradient-to-br from-emerald-50 to-white ring-emerald-200',
    badge: 'bg-emerald-600 text-white shadow-emerald-600/30',
  },
  REVOKED: {
    title: 'Revoked by the college',
    icon: 'xCircle',
    banner: 'bg-gradient-to-br from-red-50 to-white ring-red-200',
    badge: 'bg-red-600 text-white shadow-red-600/30',
  },
  EXPIRED: {
    title: 'Expired',
    icon: 'clock',
    banner: 'bg-gradient-to-br from-amber-50 to-white ring-amber-200',
    badge: 'bg-amber-500 text-white shadow-amber-500/30',
  },
  NOT_FOUND: {
    title: 'Not found on CredChain',
    icon: 'question',
    banner: 'bg-gradient-to-br from-slate-100 to-white ring-slate-300',
    badge: 'bg-slate-600 text-white shadow-slate-600/30',
  },
  FAKE: {
    title: 'Fake or edited: do not accept',
    icon: 'alert',
    banner: 'bg-gradient-to-br from-red-50 to-white ring-red-300',
    badge: 'bg-red-700 text-white shadow-red-700/30',
  },
}

const REVOCATION_REASON: Record<string, string> = {
  ISSUED_IN_ERROR: 'Issued in error',
  FRAUD: 'Fraud',
  SUPERSEDED: 'Replaced by a newer certificate',
  OTHER: 'Other reason',
}

export const revocationReason = (reason: string) => REVOCATION_REASON[reason] ?? reason

/** DEGREE -> Degree */
export const titleCase = (value: string) =>
  value.toLowerCase().replace(/_/g, ' ').replace(/^\w/, (c) => c.toUpperCase())

const NETWORKS: Record<number, string> = { 1: 'Ethereum mainnet', 11155111: 'Ethereum Sepolia (test network)', 31337: 'Local test chain' }

export const networkName = (chainId: number | null) =>
  chainId == null ? '—' : NETWORKS[chainId] ?? `Chain ${chainId}`

export function formatDate(value: string | null | undefined) {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value
    : date.toLocaleDateString('en-IN', { day: 'numeric', month: 'long', year: 'numeric' })
}

export function formatDateTime(value: string | null | undefined) {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value
    : date.toLocaleString('en-IN', { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' })
}

/** 0x1234…cdef, for long hashes and addresses. */
export const shorten = (value: string, keep = 10) =>
  value.length <= keep * 2 + 1 ? value : `${value.slice(0, keep)}…${value.slice(-keep + 2)}`
