import { useState, type ReactNode } from 'react'
import type { PdfCheck, PdfVerificationResult, VerificationResult, VerificationStatus } from '../../api/verify'
import { Icon } from '../../components/Icon'
import {
  STATUS_LOOK, formatDate, formatDateTime, networkName, revocationReason, shorten, titleCase,
} from './format'

/** Big coloured box with the answer: genuine, revoked, expired, not found or fake. */
export function StatusBanner({ status, message, children }: { status: VerificationStatus; message: string; children?: ReactNode }) {
  const look = STATUS_LOOK[status]
  return (
    <section className={`animate-fade-up rounded-3xl p-6 ring-1 sm:p-8 ${look.banner}`} aria-live="polite">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-start">
        <span className={`flex h-14 w-14 shrink-0 items-center justify-center rounded-2xl shadow-lg ${look.badge}`}>
          <Icon name={look.icon} className="h-8 w-8" />
        </span>
        <div className="min-w-0 flex-1">
          <p className="text-xs font-bold uppercase tracking-wider text-slate-500">Result</p>
          <h2 className="mt-0.5 text-2xl font-extrabold tracking-tight text-slate-900" data-testid="status-title">
            {look.title}
          </h2>
          <p className="mt-1 text-slate-700">{message}</p>
          {children}
        </div>
      </div>
    </section>
  )
}

function Field({ label, children, wide = false }: { label: string; children: ReactNode; wide?: boolean }) {
  return (
    <div className={wide ? 'sm:col-span-2' : ''}>
      <dt className="text-xs font-medium uppercase tracking-wide text-slate-400">{label}</dt>
      <dd className="mt-0.5 break-words font-medium text-slate-900">{children}</dd>
    </div>
  )
}

function CopyButton({ value, label }: { value: string; label: string }) {
  const [copied, setCopied] = useState(false)
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(value)
      setCopied(true)
      setTimeout(() => setCopied(false), 1500)
    } catch {
      // clipboard blocked (e.g. http on another host): nothing to do, the value is visible anyway
    }
  }
  return (
    <button type="button" onClick={copy} aria-label={label}
            className="rounded-md p-1 text-slate-400 transition hover:bg-slate-100 hover:text-navy-700">
      <Icon name={copied ? 'check' : 'copy'} className="h-4 w-4" />
    </button>
  )
}

/** The official certificate record: what is printed on it, revocation, and where it is on the blockchain. */
export function CertificateRecord({ result, heading = 'Certificate details' }: { result: VerificationResult; heading?: string }) {
  const c = result.certificate
  const chain = result.blockchain
  return (
    <div className="animate-fade-up space-y-6">
      {c && (
        <section className="overflow-hidden rounded-3xl bg-white shadow-sm ring-1 ring-slate-200">
          <div className="bg-brand flex items-center gap-3 px-6 py-5 text-white sm:px-8">
            <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-white/10 text-gold-300 ring-1 ring-white/15">
              <Icon name="student" className="h-5 w-5" />
            </span>
            <div className="min-w-0">
              <p className="text-xs uppercase tracking-wider text-navy-200">{heading}</p>
              <p className="truncate font-semibold">{c.institutionName}</p>
            </div>
          </div>
          <div className="px-6 py-6 sm:px-8">
            <p className="text-xs uppercase tracking-wider text-slate-400">Awarded to</p>
            <p className="font-display text-2xl font-bold text-navy-800">{c.studentName}</p>
            <p className="mt-1 text-slate-600">{c.title}</p>
            <dl className="mt-6 grid gap-5 sm:grid-cols-2">
              <Field label="Certificate number">{c.certificateNumber}</Field>
              <Field label="Type">{titleCase(c.type)}</Field>
              {c.program && <Field label="Programme">{c.program}</Field>}
              {c.enrollmentNo && <Field label="Enrollment number">{c.enrollmentNo}</Field>}
              {c.cgpa && <Field label="CGPA">{c.cgpa}</Field>}
              {c.grade && <Field label="Grade">{c.grade}</Field>}
              <Field label="Awarded on">{formatDate(c.awardedOn)}</Field>
              {c.expiresAt && <Field label="Valid until">{formatDate(c.expiresAt)}</Field>}
              <Field label="College code">{c.institutionCode}</Field>
            </dl>
          </div>
        </section>
      )}

      {result.revocation && (
        <section className="rounded-2xl bg-red-50 p-5 ring-1 ring-red-200">
          <h3 className="flex items-center gap-2 font-bold text-red-800"><Icon name="xCircle" /> Revoked</h3>
          <p className="mt-1 text-sm text-red-800">
            Reason: {revocationReason(result.revocation.reason)} · on {formatDateTime(result.revocation.revokedAt)}
          </p>
        </section>
      )}

      {chain && (
        <section className="rounded-3xl bg-white p-6 shadow-sm ring-1 ring-slate-200 sm:p-8">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <h3 className="flex items-center gap-2 font-bold text-slate-900">
              <Icon name="chain" className="h-5 w-5 text-navy-600" /> Blockchain record
            </h3>
            {chain.explorerTxUrl && (
              <a href={chain.explorerTxUrl} target="_blank" rel="noreferrer"
                 className="inline-flex items-center gap-1.5 rounded-lg bg-navy-50 px-3 py-1.5 text-sm font-semibold text-navy-700 hover:bg-navy-100">
                Check it yourself on Etherscan <Icon name="external" className="h-4 w-4" />
              </a>
            )}
          </div>
          <dl className="mt-5 grid gap-5 sm:grid-cols-2">
            <Field label="Network">{networkName(chain.chainId)}</Field>
            <Field label="Recorded on">{formatDateTime(chain.anchoredAt)}</Field>
            {chain.blockNumber != null && <Field label="Block">#{chain.blockNumber}</Field>}
            {chain.txHash && (
              <Field label="Transaction">
                <span className="inline-flex items-center gap-1 font-mono text-sm">{shorten(chain.txHash)}
                  <CopyButton value={chain.txHash} label="Copy transaction hash" /></span>
              </Field>
            )}
            {chain.issuerAddress && (
              <Field label="College's blockchain address">
                <span className="inline-flex items-center gap-1 font-mono text-sm">{shorten(chain.issuerAddress)}
                  <CopyButton value={chain.issuerAddress} label="Copy college address" /></span>
              </Field>
            )}
            {chain.merkleRoot && (
              <Field label="Batch fingerprint (Merkle root)">
                <span className="inline-flex items-center gap-1 font-mono text-sm">{shorten(chain.merkleRoot)}
                  <CopyButton value={chain.merkleRoot} label="Copy Merkle root" /></span>
              </Field>
            )}
          </dl>
        </section>
      )}

      <p className="flex items-start gap-2 text-xs text-slate-500">
        <Icon name="hash" className="mt-0.5 h-4 w-4 shrink-0" />
        <span className="break-all">Certificate hash: <span className="font-mono">{result.certHash}</span></span>
      </p>
    </div>
  )
}

/** Note shown when the answer came from the database only (blockchain node unreachable). */
export function ChainNote({ checked, checkedAt }: { checked: boolean; checkedAt: string }) {
  return (
    <p className="mt-4 flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-slate-600">
      <span className="inline-flex items-center gap-1.5">
        <Icon name={checked ? 'chain' : 'alert'} className={`h-4 w-4 ${checked ? 'text-emerald-600' : 'text-amber-600'}`} />
        {checked ? 'Confirmed live on the blockchain' : 'Blockchain not reachable right now: checked in the CredChain database'}
      </span>
      <span className="inline-flex items-center gap-1.5"><Icon name="clock" className="h-4 w-4" /> {formatDateTime(checkedAt)}</span>
    </p>
  )
}

const CHECK_LOOK: Record<PdfCheck['result'], { icon: 'checkCircle' | 'xCircle' | 'minusCircle'; color: string; label: string }> = {
  PASSED: { icon: 'checkCircle', color: 'text-emerald-600', label: 'Passed' },
  FAILED: { icon: 'xCircle', color: 'text-red-600', label: 'Failed' },
  SKIPPED: { icon: 'minusCircle', color: 'text-slate-400', label: 'Skipped' },
}

/** Step-by-step list of what the PDF check looked at. */
export function PdfChecks({ result }: { result: PdfVerificationResult }) {
  return (
    <section className="animate-fade-up rounded-3xl bg-white p-6 shadow-sm ring-1 ring-slate-200 sm:p-8">
      <h3 className="flex items-center gap-2 font-bold text-slate-900">
        <Icon name="file" className="h-5 w-5 text-navy-600" /> What we checked in the PDF
      </h3>
      <ol className="mt-5 space-y-4">
        {result.checks.map((check, i) => {
          const look = CHECK_LOOK[check.result]
          return (
            <li key={check.name} className="flex gap-3">
              <span className={`mt-0.5 ${look.color}`}><Icon name={look.icon} className="h-5 w-5" /></span>
              <div>
                <p className="font-medium text-slate-900">
                  <span className="text-slate-400">{i + 1}.</span> {check.name}
                  <span className="sr-only">: {look.label}</span>
                </p>
                {check.detail && <p className="text-sm text-slate-600">{check.detail}</p>}
              </div>
            </li>
          )
        })}
      </ol>
      <p className="mt-6 break-all text-xs text-slate-400">File fingerprint (SHA-256): <span className="font-mono">{result.fileSha256}</span></p>
    </section>
  )
}
