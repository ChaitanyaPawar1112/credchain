import type { DocumentCheckResult, DocumentSignal, FieldCheck, RiskLevel } from '../../api/verify'
import { Icon, type IconName } from '../../components/Icon'
import { CHECK_LOOK, CertificateRecord, ChainNote, PdfResult } from './ResultViews'
import { STATUS_LOOK, formatDateTime } from './format'

const RISK_LOOK: Record<RiskLevel, { title: string; icon: IconName; banner: string; badge: string; bar: string }> = {
  LOW: {
    title: 'No signs of forgery found',
    icon: 'checkCircle',
    banner: 'bg-gradient-to-br from-emerald-50 to-white ring-emerald-200',
    badge: 'bg-emerald-600 text-white shadow-emerald-600/30',
    bar: 'bg-emerald-500',
  },
  MEDIUM: {
    title: 'Some warning signs: check carefully',
    icon: 'alert',
    banner: 'bg-gradient-to-br from-amber-50 to-white ring-amber-200',
    badge: 'bg-amber-500 text-white shadow-amber-500/30',
    bar: 'bg-amber-500',
  },
  HIGH: {
    title: 'High risk: probably forged or edited',
    icon: 'xCircle',
    banner: 'bg-gradient-to-br from-red-50 to-white ring-red-300',
    badge: 'bg-red-700 text-white shadow-red-700/30',
    bar: 'bg-red-600',
  },
}

const SEVERITY_LOOK: Record<DocumentSignal['severity'], { label: string; color: string }> = {
  HIGH: { label: 'High', color: 'bg-red-100 text-red-800' },
  MEDIUM: { label: 'Medium', color: 'bg-amber-100 text-amber-800' },
  LOW: { label: 'Low', color: 'bg-slate-100 text-slate-700' },
  INFO: { label: 'Info', color: 'bg-navy-50 text-navy-700' },
}

/**
 * Answer for a photo, scan or other copy. A CredChain PDF got the exact PDF check instead, so that result is
 * shown as it is; anything else gets the AI risk report.
 */
export function DocumentResult({ result }: { result: DocumentCheckResult }) {
  if (result.kind === 'EXACT_PDF' && result.pdfCheck) {
    return <PdfResult result={result.pdfCheck} />
  }
  const level = result.riskLevel ?? 'MEDIUM'
  const score = result.riskScore ?? 0
  return (
    <div className="space-y-6">
      <RiskBanner level={level} score={score} summary={result.summary ?? ''} checkedAt={result.checkedAt} />
      <OfficialStatus result={result} />
      {result.fieldChecks.length > 0 && <FieldChecks checks={result.fieldChecks} />}
      <Findings result={result} />
      {result.elaImage && <ElaHeatMap image={result.elaImage} />}
      {result.record?.certificate && (
        <CertificateRecord result={result.record} heading="Official record (compare with the copy you have)" />
      )}
    </div>
  )
}

function RiskBanner({ level, score, summary, checkedAt }: { level: RiskLevel; score: number; summary: string; checkedAt: string }) {
  const look = RISK_LOOK[level]
  return (
    <section className={`animate-fade-up rounded-3xl p-6 ring-1 sm:p-8 ${look.banner}`} aria-live="polite">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-start">
        <span className={`flex h-14 w-14 shrink-0 items-center justify-center rounded-2xl shadow-lg ${look.badge}`}>
          <Icon name={look.icon} className="h-8 w-8" />
        </span>
        <div className="min-w-0 flex-1">
          <p className="text-xs font-bold uppercase tracking-wider text-slate-500">AI forgery check</p>
          <h2 className="mt-0.5 text-2xl font-extrabold tracking-tight text-slate-900" data-testid="status-title">{look.title}</h2>
          <p className="mt-1 text-slate-700">{summary}</p>
          <div className="mt-4">
            <div className="flex items-baseline justify-between text-sm">
              <span className="font-medium text-slate-700">Risk score</span>
              <span className="font-bold text-slate-900" data-testid="risk-score">{score} / 100</span>
            </div>
            <div className="mt-1.5 h-2.5 overflow-hidden rounded-full bg-slate-200" role="meter" aria-label="Risk score"
                 aria-valuemin={0} aria-valuemax={100} aria-valuenow={score}>
              <div className={`h-full rounded-full ${look.bar}`} style={{ width: `${Math.max(score, 2)}%` }} />
            </div>
            <p className="mt-1.5 text-xs text-slate-500">0 to 24 low · 25 to 54 medium · 55 and above high</p>
          </div>
          <p className="mt-3 inline-flex items-center gap-1.5 text-sm text-slate-600">
            <Icon name="clock" className="h-4 w-4" /> {formatDateTime(checkedAt)}
          </p>
        </div>
      </div>
    </section>
  )
}

/** What the QR code on the copy points to in CredChain. */
function OfficialStatus({ result }: { result: DocumentCheckResult }) {
  const record = result.record
  if (!result.qrFound) {
    return <Note icon="qr" text="No QR code could be read on this copy, so it could not be matched with an official record. Ask for the original PDF or the verification link." />
  }
  if (!record) {
    return <Note icon="qr" text="The QR code on this copy does not point to a CredChain certificate." />
  }
  const look = STATUS_LOOK[record.status]
  return (
    <section className="animate-fade-up rounded-3xl bg-white p-6 shadow-sm ring-1 ring-slate-200 sm:p-8">
      <h3 className="flex items-center gap-2 font-bold text-slate-900">
        <Icon name="qr" className="h-5 w-5 text-navy-600" /> The QR code points to
      </h3>
      <div className="mt-4 flex items-start gap-3">
        <span className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-xl ${look.badge}`}>
          <Icon name={look.icon} className="h-5 w-5" />
        </span>
        <div>
          <p className="font-semibold text-slate-900" data-testid="record-status">{look.title}</p>
          <p className="text-sm text-slate-600">{record.message}</p>
        </div>
      </div>
      <ChainNote checked={record.blockchainChecked} checkedAt={record.checkedAt} />
    </section>
  )
}

function Note({ icon, text }: { icon: IconName; text: string }) {
  return (
    <p className="flex items-start gap-3 rounded-2xl bg-white p-5 text-sm text-slate-700 ring-1 ring-slate-200">
      <Icon name={icon} className="mt-0.5 h-5 w-5 shrink-0 text-slate-400" /> {text}
    </p>
  )
}

/** Printed details compared with the official record (read from the copy with OCR). */
function FieldChecks({ checks }: { checks: FieldCheck[] }) {
  return (
    <section className="animate-fade-up rounded-3xl bg-white p-6 shadow-sm ring-1 ring-slate-200 sm:p-8">
      <h3 className="flex items-center gap-2 font-bold text-slate-900">
        <Icon name="search" className="h-5 w-5 text-navy-600" /> Printed details compared with the official record
      </h3>
      <ul className="mt-5 divide-y divide-slate-100" aria-label="Printed details">
        {checks.map((check) => {
          const look = CHECK_LOOK[check.result]
          return (
            <li key={check.field} className="flex items-start gap-3 py-3">
              <span className={`mt-0.5 ${look.color}`}><Icon name={look.icon} className="h-5 w-5" /></span>
              <div className="min-w-0 flex-1">
                <p className="font-medium text-slate-900">{check.field}<span className="sr-only">: {look.label}</span></p>
                <p className="break-words text-sm text-slate-600">Official: {check.officialValue}</p>
              </div>
              <span className={`text-sm font-semibold ${look.color}`}>
                {check.result === 'PASSED' ? 'Matches' : check.result === 'FAILED' ? 'Not found on the copy' : 'Not checked'}
              </span>
            </li>
          )
        })}
      </ul>
      <p className="mt-4 text-xs text-slate-500">
        The text is read from the picture by a machine (OCR). A blurry photo can make a detail "not found" even when it is correct.
      </p>
    </section>
  )
}

/** Every finding, most serious first, with the risk points it added. */
function Findings({ result }: { result: DocumentCheckResult }) {
  return (
    <section className="animate-fade-up rounded-3xl bg-white p-6 shadow-sm ring-1 ring-slate-200 sm:p-8">
      <h3 className="flex items-center gap-2 font-bold text-slate-900">
        <Icon name="sparkle" className="h-5 w-5 text-navy-600" /> What the AI found
      </h3>
      {result.signals.length === 0 ? (
        <p className="mt-4 text-sm text-slate-600">Nothing suspicious in the file, its QR code or its text.</p>
      ) : (
        <ul className="mt-5 space-y-3" aria-label="Findings">
          {result.signals.map((signal) => {
            const look = SEVERITY_LOOK[signal.severity]
            return (
              <li key={signal.code} className="flex items-start gap-3">
                <span className={`mt-0.5 shrink-0 rounded-md px-2 py-0.5 text-xs font-semibold ${look.color}`}>{look.label}</span>
                <p className="flex-1 text-sm text-slate-700">{signal.detail}</p>
                {signal.weight > 0 && <span className="shrink-0 text-sm font-semibold text-slate-900">+{signal.weight}</span>}
              </li>
            )
          })}
        </ul>
      )}
      {!result.textRead && (
        <p className="mt-4 text-xs text-slate-500">No text could be read from this copy, so the printed details were not compared.</p>
      )}
    </section>
  )
}

/** Error Level Analysis: areas that were pasted in or edited often look brighter than the rest. */
function ElaHeatMap({ image }: { image: string }) {
  return (
    <section className="animate-fade-up rounded-3xl bg-white p-6 shadow-sm ring-1 ring-slate-200 sm:p-8">
      <h3 className="flex items-center gap-2 font-bold text-slate-900">
        <Icon name="camera" className="h-5 w-5 text-navy-600" /> Edit heat map (Error Level Analysis)
      </h3>
      <p className="mt-1 text-sm text-slate-600">
        Areas that were edited after the photo was taken often look brighter than the rest. Text and sharp edges are
        always a little bright, so look for one name, number or grade that glows much more than its neighbours.
      </p>
      <img src={`data:image/png;base64,${image}`} alt="Error Level Analysis heat map of the uploaded photo"
           className="mt-4 max-h-[28rem] w-full rounded-xl bg-black object-contain" />
      <p className="mt-2 text-xs text-slate-500">The heat map is extra evidence for a person to look at; it does not change the risk score.</p>
    </section>
  )
}
