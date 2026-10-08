import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { ApiError } from '../../api/client'
import { studentApi, type MyCertificate } from '../../api/student'
import { Badge } from '../../components/Badge'
import { Icon } from '../../components/Icon'
import { PageHeader } from '../../components/PageHeader'
import { Pagination } from '../../components/Pagination'
import { EmptyState, LoadError, LoadingRows, Panel } from '../../components/Panel'
import { formatDate } from '../../lib/format'
import { DownloadPdfButton } from '../institution/DownloadPdfButton'
import { CERTIFICATE_STATUS, CERTIFICATE_TYPE, REVOCATION_REASON } from '../institution/labels'
import { LinkAccountCard } from './LinkAccountCard'
import { ShareModal } from './ShareModal'

/** True when the student account has not been linked to a college record yet (the profile endpoint answers 404). */
export const notLinked = (error: unknown) => error instanceof ApiError && error.status === 404

/** Student: the certificates their college issued to them. Download the PDF, or share the verify link and QR code. */
export function MyCertificatesPage() {
  const [page, setPage] = useState(0)
  const [sharing, setSharing] = useState<MyCertificate | null>(null)
  const profile = useQuery({ queryKey: ['student', 'profile'], queryFn: studentApi.profile, retry: false })
  const certificates = useQuery({
    queryKey: ['student', 'certificates', page],
    queryFn: () => studentApi.certificates(page),
    enabled: profile.isSuccess,
    placeholderData: (previous) => previous,
  })

  return (
    <div className="animate-fade-up mx-auto max-w-5xl space-y-6">
      <PageHeader icon="certificate" title="My certificates"
                  description="Every certificate your college has issued to you on the blockchain." />

      {profile.isPending && <Panel><LoadingRows /></Panel>}
      {profile.isError && (notLinked(profile.error)
        ? <LinkAccountCard />
        : <Panel><LoadError error={profile.error} onRetry={() => profile.refetch()} /></Panel>)}

      {profile.isSuccess && (
        <>
          {certificates.isPending && <Panel><LoadingRows /></Panel>}
          {certificates.isError && <Panel><LoadError error={certificates.error} onRetry={() => certificates.refetch()} /></Panel>}
          {certificates.data && certificates.data.content.length === 0 && (
            <Panel>
              <EmptyState icon="certificate" title="No certificates yet"
                          text={`When ${profile.data.institutionName} issues you a certificate, it appears here.`} />
            </Panel>
          )}
          {certificates.data && certificates.data.content.length > 0 && (
            <>
              <div className="grid gap-5 md:grid-cols-2">
                {certificates.data.content.map((c) => <CertificateCard key={c.id} certificate={c} onShare={() => setSharing(c)} />)}
              </div>
              {certificates.data.totalPages > 1 && <Panel><Pagination data={certificates.data} onPage={setPage} /></Panel>}
            </>
          )}
        </>
      )}

      {sharing && <ShareModal certificate={sharing} onClose={() => setSharing(null)} />}
    </div>
  )
}

function CertificateCard({ certificate: c, onShare }: { certificate: MyCertificate; onShare: () => void }) {
  const status = CERTIFICATE_STATUS[c.status]
  const revoked = c.status === 'REVOKED' || c.status === 'REVOCATION_PENDING'
  return (
    <article className={`flex flex-col overflow-hidden rounded-2xl bg-white shadow-sm ring-1 transition hover:shadow-md ${revoked ? 'ring-red-200' : 'ring-slate-200'}`}>
      <div className={`h-1.5 ${revoked ? 'bg-red-400' : 'bg-gradient-to-r from-gold-300 via-gold-400 to-gold-500'}`} />
      <div className="flex flex-1 flex-col p-6">
        <div className="flex items-start justify-between gap-3">
          <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-navy-50 text-navy-700">
            <Icon name="certificate" className="h-5 w-5" />
          </span>
          <Badge tone={status.tone} dot>{status.label}</Badge>
        </div>
        <p className="mt-4 text-xs font-semibold uppercase tracking-wide text-gold-600">{CERTIFICATE_TYPE[c.type]}</p>
        <h2 className="mt-1 text-lg font-bold text-slate-900">{c.title}{c.program ? `, ${c.program}` : ''}</h2>
        <p className="text-sm text-slate-600">{c.institutionName}</p>
        <dl className="mt-4 grid grid-cols-2 gap-3 text-sm">
          <div><dt className="text-xs text-slate-500">Awarded on</dt><dd className="font-medium">{formatDate(c.awardedOn)}</dd></div>
          <div><dt className="text-xs text-slate-500">Result</dt>
            <dd className="font-medium">{[c.cgpa != null ? `CGPA ${c.cgpa}` : null, c.grade].filter(Boolean).join(' · ') || '—'}</dd></div>
          <div className="col-span-2"><dt className="text-xs text-slate-500">Certificate number</dt><dd className="font-mono text-xs">{c.certificateNumber}</dd></div>
        </dl>
        {revoked && (
          <p className="mt-4 rounded-xl bg-red-50 px-3 py-2 text-sm text-red-800 ring-1 ring-red-200">
            Your college revoked this certificate{c.revocationReason ? ` (${REVOCATION_REASON[c.revocationReason].toLowerCase()})` : ''}.
            Anyone who checks it sees it as revoked.
          </p>
        )}
        <div className="mt-5 flex flex-wrap items-center gap-2 border-t border-slate-100 pt-4">
          {!revoked && (
            <button type="button" onClick={onShare}
                    className="inline-flex items-center gap-1.5 rounded-lg bg-navy-700 px-3 py-1.5 text-sm font-semibold text-white shadow-sm hover:bg-navy-800">
              <Icon name="qr" className="h-4 w-4" /> Share
            </button>
          )}
          {c.pdfAvailable
            ? <DownloadPdfButton certificate={c} download={studentApi.certificatePdf} />
            : !revoked && <span className="text-xs text-slate-500">PDF is being prepared</span>}
          <a href={c.verificationUrl} target="_blank" rel="noreferrer"
             className="ml-auto inline-flex items-center gap-1 rounded-lg px-2.5 py-1.5 text-sm font-semibold text-navy-700 hover:bg-navy-50">
            Verify <Icon name="external" className="h-3.5 w-3.5" />
          </a>
        </div>
      </div>
    </article>
  )
}
