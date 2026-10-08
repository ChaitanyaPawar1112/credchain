import type { BatchStatus, CertificateStatus, CertificateType, RevocationReason } from '../../api/institution'
import type { BadgeTone } from '../../components/Badge'

export const BATCH_STATUS: Record<BatchStatus, { label: string; tone: BadgeTone; help: string }> = {
  DRAFT: { label: 'Draft', tone: 'slate', help: 'Add certificates, then issue the batch.' },
  QUEUED: { label: 'Waiting to send', tone: 'amber', help: 'Frozen and waiting to be sent to the blockchain.' },
  SUBMITTED: { label: 'Confirming', tone: 'amber', help: 'Sent to the blockchain, waiting for confirmation.' },
  ANCHORED: { label: 'Issued', tone: 'green', help: 'Recorded on the blockchain. Certificates can be verified.' },
  REVOKED: { label: 'Revoked', tone: 'red', help: 'The whole batch was revoked.' },
}

export const CERTIFICATE_STATUS: Record<CertificateStatus, { label: string; tone: BadgeTone }> = {
  DRAFT: { label: 'Draft', tone: 'slate' },
  PENDING: { label: 'Being issued', tone: 'amber' },
  ISSUED: { label: 'Issued', tone: 'green' },
  REVOCATION_PENDING: { label: 'Being revoked', tone: 'amber' },
  REVOKED: { label: 'Revoked', tone: 'red' },
}

export const CERTIFICATE_TYPE: Record<CertificateType, string> = {
  DEGREE: 'Degree',
  DIPLOMA: 'Diploma',
  CERTIFICATE: 'Certificate',
  TRANSCRIPT: 'Transcript / mark sheet',
  PROVISIONAL: 'Provisional certificate',
  OTHER: 'Other',
}

export const REVOCATION_REASON: Record<RevocationReason, string> = {
  ISSUED_IN_ERROR: 'Issued in error',
  FRAUD: 'Fraud',
  SUPERSEDED: 'Replaced by a newer certificate',
  OTHER: 'Other reason',
}

export const options = <K extends string>(labels: Record<K, string>) =>
  (Object.entries(labels) as [K, string][]).map(([value, label]) => ({ value, label }))
