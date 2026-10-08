import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'
import { institutionApi, type Certificate, type CertificateStatus, type RevocationReason } from '../../api/institution'
import { Alert } from '../../components/Alert'
import { Badge } from '../../components/Badge'
import { Button } from '../../components/Button'
import { Icon } from '../../components/Icon'
import { Modal } from '../../components/Modal'
import { PageHeader } from '../../components/PageHeader'
import { Pagination } from '../../components/Pagination'
import { EmptyState, FilterTabs, LoadError, LoadingRows, Panel, td, th } from '../../components/Panel'
import { SearchBox } from '../../components/SearchBox'
import { SelectField } from '../../components/SelectField'
import { errorMessage } from '../../components/errors'
import { formatDate } from '../../lib/format'
import { DownloadPdfButton } from './DownloadPdfButton'
import { CERTIFICATE_STATUS, CERTIFICATE_TYPE, REVOCATION_REASON, options } from './labels'

type Filter = CertificateStatus | 'ALL'
const FILTERS: { value: Filter; label: string }[] = [
  { value: 'ALL', label: 'All' },
  { value: 'ISSUED', label: 'Issued' },
  { value: 'PENDING', label: 'Being issued' },
  { value: 'DRAFT', label: 'Draft' },
  { value: 'REVOKED', label: 'Revoked' },
]

/** College admin: every certificate. Download the PDF, open its public verify page, or revoke it. */
export function CertificatesPage() {
  const [filter, setFilter] = useState<Filter>('ALL')
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)
  const [revoking, setRevoking] = useState<Certificate | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const certificates = useQuery({
    queryKey: ['institution', 'certificates', filter, search, page],
    queryFn: () => institutionApi.certificates(filter === 'ALL' ? undefined : filter, search, page),
    placeholderData: (previous) => previous,
    refetchInterval: (query) =>
      query.state.data?.content.some((c) => c.status === 'PENDING' || c.status === 'REVOCATION_PENDING') ? 5000 : false,
  })

  const onSearch = (value: string) => {
    setSearch(value)
    setPage(0)
  }

  return (
    <div className="animate-fade-up mx-auto max-w-6xl space-y-6">
      <PageHeader icon="certificate" title="Certificates"
                  description="Every certificate your college has created. Issue new ones from a batch." />

      {notice && <Alert tone="success">{notice}</Alert>}

      <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
        <FilterTabs value={filter} onChange={(v) => { setFilter(v); setPage(0) }} options={FILTERS} />
        <SearchBox placeholder="Search name, enrollment or certificate no." onSearch={onSearch} />
      </div>

      <Panel>
        {certificates.isPending && <LoadingRows />}
        {certificates.isError && <LoadError error={certificates.error} onRetry={() => certificates.refetch()} />}
        {certificates.data && certificates.data.content.length === 0 && (
          <EmptyState icon="certificate" title={search || filter !== 'ALL' ? 'No certificate matches' : 'No certificates yet'}
                      text={search || filter !== 'ALL' ? undefined : 'Create a batch, add certificates to it and issue it.'} />
        )}
        {certificates.data && certificates.data.content.length > 0 && (
          <>
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-slate-100">
                <thead className="bg-slate-50/80">
                  <tr>
                    <th className={th}>Student</th>
                    <th className={th}>Certificate</th>
                    <th className={th}>Awarded</th>
                    <th className={th}>Status</th>
                    <th className={th}><span className="sr-only">Actions</span></th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {certificates.data.content.map((c) => {
                    const status = CERTIFICATE_STATUS[c.status]
                    return (
                      <tr key={c.id} className="transition hover:bg-slate-50/60">
                        <td className={td}>
                          <p className="font-semibold text-slate-900">{c.studentName}</p>
                          <p className="text-xs text-slate-500">{c.enrollmentNo}</p>
                        </td>
                        <td className={td}>
                          <p>{c.title}{c.program ? `, ${c.program}` : ''}</p>
                          <p className="text-xs text-slate-500">{CERTIFICATE_TYPE[c.type]} · {c.certificateNumber}</p>
                        </td>
                        <td className={`${td} whitespace-nowrap`}>{formatDate(c.awardedOn)}</td>
                        <td className={td}>
                          <Badge tone={status.tone} dot>{status.label}</Badge>
                          {c.revocationReason && <p className="mt-1 text-xs text-slate-500">{REVOCATION_REASON[c.revocationReason]}</p>}
                        </td>
                        <td className={`${td} whitespace-nowrap text-right`}>
                          {c.status === 'DRAFT' && (
                            <Link to={`/institution/batches/${c.batchId}`}
                                  className="rounded-lg px-2.5 py-1.5 text-sm font-semibold text-navy-700 hover:bg-navy-50">Open batch</Link>
                          )}
                          {c.pdfAvailable && <DownloadPdfButton certificate={c} />}
                          {c.certHash && c.status !== 'DRAFT' && c.status !== 'PENDING' && (
                            <Link to={`/verify/${c.certHash}`} target="_blank"
                                  className="inline-flex items-center gap-1 rounded-lg px-2.5 py-1.5 text-sm font-semibold text-navy-700 hover:bg-navy-50">
                              Verify <Icon name="external" className="h-3.5 w-3.5" />
                            </Link>
                          )}
                          {c.status === 'ISSUED' && (
                            <button type="button" onClick={() => setRevoking(c)}
                                    className="rounded-lg px-2.5 py-1.5 text-sm font-medium text-red-600 hover:bg-red-50">Revoke</button>
                          )}
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
            <Pagination data={certificates.data} onPage={setPage} />
          </>
        )}
      </Panel>

      {revoking && (
        <RevokeDialog certificate={revoking} onClose={() => setRevoking(null)}
                      onRevoked={(c) => { setRevoking(null); setNotice(`${c.certificateNumber} is being revoked. Verifiers will see it as revoked.`) }} />
      )}
    </div>
  )
}

function RevokeDialog({ certificate, onClose, onRevoked }: {
  certificate: Certificate
  onClose: () => void
  onRevoked: (c: Certificate) => void
}) {
  const queryClient = useQueryClient()
  const [reason, setReason] = useState<RevocationReason>('ISSUED_IN_ERROR')
  const [note, setNote] = useState('')
  const revoke = useMutation({
    mutationFn: () => institutionApi.revoke(certificate.id, reason, note.trim() || undefined),
    onSuccess: (c) => {
      void queryClient.invalidateQueries({ queryKey: ['institution', 'certificates'] })
      onRevoked(c)
    },
  })
  return (
    <Modal title={`Revoke ${certificate.certificateNumber}?`} onClose={onClose} locked={revoke.isPending}
           footer={<>
             <Button variant="secondary" onClick={onClose} disabled={revoke.isPending}>Cancel</Button>
             <Button variant="danger" loading={revoke.isPending} onClick={() => revoke.mutate()}>Revoke certificate</Button>
           </>}>
      <p className="text-sm text-slate-600">
        <strong>{certificate.studentName}</strong>'s certificate "{certificate.title}" is marked revoked on the blockchain.
        Anyone who checks it afterwards sees it as <strong>revoked</strong>. This can't be undone.
      </p>
      <div className="mt-4 space-y-4">
        <SelectField label="Reason" value={reason} onChange={(e) => setReason(e.target.value as RevocationReason)}
                     options={options(REVOCATION_REASON)} />
        <div>
          <label htmlFor="revoke-note" className="block text-sm font-medium text-slate-700">Internal note (optional)</label>
          <textarea id="revoke-note" rows={3} maxLength={500} value={note} onChange={(e) => setNote(e.target.value)}
                    className="mt-1.5 block w-full rounded-xl border border-slate-300 px-3.5 py-2.5 text-sm outline-none focus:border-navy-600 focus:ring-4 focus:ring-navy-100" />
          <p className="mt-1 text-xs text-slate-500">Only your college sees this. {note.length}/500</p>
        </div>
      </div>
      {revoke.isError && <div className="mt-4"><Alert tone="error">{errorMessage(revoke.error)}</Alert></div>}
    </Modal>
  )
}
