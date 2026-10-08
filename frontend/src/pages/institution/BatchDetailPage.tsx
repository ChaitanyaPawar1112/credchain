import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { institutionApi, type Batch, type BatchStatus, type Certificate } from '../../api/institution'
import { Alert } from '../../components/Alert'
import { Badge } from '../../components/Badge'
import { Button } from '../../components/Button'
import { Icon } from '../../components/Icon'
import { Modal } from '../../components/Modal'
import { EmptyState, LoadError, LoadingRows, Panel, td, th } from '../../components/Panel'
import { errorMessage } from '../../components/errors'
import { formatDate, formatDateTime, shorten } from '../../lib/format'
import { AddCertificatesModal } from './AddCertificatesModal'
import { DownloadPdfButton } from './DownloadPdfButton'
import { BATCH_STATUS, CERTIFICATE_STATUS } from './labels'

const STEPS: { status: BatchStatus; label: string }[] = [
  { status: 'DRAFT', label: 'Draft' },
  { status: 'QUEUED', label: 'Waiting to send' },
  { status: 'SUBMITTED', label: 'Confirming' },
  { status: 'ANCHORED', label: 'Issued' },
]

/** College admin: one batch. Add or remove certificates while it is a draft, then issue it on the blockchain. */
export function BatchDetailPage() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [dialog, setDialog] = useState<'add' | 'issue' | 'delete' | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const detail = useQuery({
    queryKey: ['institution', 'batch', id],
    queryFn: () => institutionApi.batch(id),
    refetchInterval: (query) => {
      const status = query.state.data?.batch.status
      return status === 'QUEUED' || status === 'SUBMITTED' ? 5000 : false
    },
  })
  const wallet = useQuery({ queryKey: ['institution', 'wallet'], queryFn: institutionApi.wallet, retry: false })

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['institution', 'batch', id] })
    void queryClient.invalidateQueries({ queryKey: ['institution', 'batches'] })
    void queryClient.invalidateQueries({ queryKey: ['institution', 'certificates'] })
  }
  const remove = useMutation({
    mutationFn: (certificateId: string) => institutionApi.removeCertificate(id, certificateId),
    onSuccess: refresh,
  })

  if (detail.isPending) return <Panel><LoadingRows /></Panel>
  if (detail.isError) return <Panel><LoadError error={detail.error} onRetry={() => detail.refetch()} /></Panel>
  const { batch, certificates } = detail.data
  const draft = batch.status === 'DRAFT'
  const look = BATCH_STATUS[batch.status]
  const canIssue = wallet.data?.canIssue ?? false

  return (
    <div className="animate-fade-up mx-auto max-w-6xl space-y-6">
      <Link to="/institution/batches" className="inline-flex items-center gap-1 text-sm font-medium text-slate-500 hover:text-navy-700">
        <Icon name="arrowRight" className="h-4 w-4 rotate-180" /> All batches
      </Link>

      <section className="bg-brand rounded-3xl p-6 text-white shadow-xl sm:p-8">
        <div className="flex flex-col gap-5 sm:flex-row sm:items-start sm:justify-between">
          <div>
            <Badge tone={look.tone} dot>{look.label}</Badge>
            <h1 className="mt-2 text-2xl font-extrabold tracking-tight sm:text-3xl">{batch.title}</h1>
            <p className="mt-1 text-sm text-navy-100">
              {batch.certificateCount} {batch.certificateCount === 1 ? 'certificate' : 'certificates'}
              {batch.expiresAt ? ` · valid until ${formatDate(batch.expiresAt)}` : ' · no expiry'}
            </p>
          </div>
          {draft && (
            <div className="flex flex-wrap gap-2">
              <Button variant="secondary" onClick={() => setDialog('add')}>Add certificates</Button>
              <Button variant="gold" disabled={certificates.length === 0} onClick={() => setDialog('issue')}>
                <Icon name="chain" className="h-4 w-4" /> Issue on blockchain
              </Button>
            </div>
          )}
        </div>
        <StatusSteps status={batch.status} />
        <p className="mt-3 text-sm text-navy-100">{look.help}</p>
      </section>

      {notice && <Alert tone="success">{notice}</Alert>}
      {batch.lastError && batch.status !== 'ANCHORED' && (
        <Alert tone="warning">The last attempt to send this batch failed ({batch.lastError}). CredChain retries automatically.</Alert>
      )}
      {draft && wallet.data && !canIssue && (
        <Alert tone="warning">
          Your college's blockchain wallet is not ready yet, so this batch can't be issued. It is activated automatically after approval;
          check the status on the overview page.
        </Alert>
      )}

      {batch.txHash && <ChainRecord batch={batch} />}

      <Panel>
        <div className="flex items-center justify-between border-b border-slate-100 px-5 py-4">
          <h2 className="font-bold text-slate-900">Certificates</h2>
          {remove.isError && <p className="text-sm text-red-600">{errorMessage(remove.error)}</p>}
        </div>
        {certificates.length === 0 ? (
          <EmptyState icon="certificate" title="No certificates in this batch yet"
                      text={draft ? 'Click "Add certificates" and choose the students.' : undefined} />
        ) : (
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-slate-100">
              <thead className="bg-slate-50/80">
                <tr>
                  <th className={th}>Student</th>
                  <th className={th}>Certificate</th>
                  <th className={th}>Result</th>
                  <th className={th}>Status</th>
                  <th className={th}><span className="sr-only">Actions</span></th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {certificates.map((c) => (
                  <CertificateRow key={c.id} certificate={c} draft={draft}
                                  removing={remove.isPending && remove.variables === c.id} onRemove={() => remove.mutate(c.id)} />
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Panel>

      {draft && (
        <div className="flex justify-end">
          <button type="button" onClick={() => setDialog('delete')} className="text-sm font-medium text-red-600 hover:underline">
            Delete this draft batch
          </button>
        </div>
      )}

      {dialog === 'add' && (
        <AddCertificatesModal batchId={id} alreadyIn={new Set(certificates.map((c) => c.studentId))} onClose={() => setDialog(null)}
                              onAdded={(n) => { setDialog(null); setNotice(`${n} ${n === 1 ? 'certificate' : 'certificates'} added.`); refresh() }} />
      )}
      {dialog === 'issue' && (
        <IssueDialog batch={batch} count={certificates.length} canIssue={canIssue} onClose={() => setDialog(null)}
                     onIssued={() => { setDialog(null); setNotice('The batch is on its way to the blockchain. This page updates by itself.'); refresh() }} />
      )}
      {dialog === 'delete' && (
        <DeleteDialog batch={batch} onClose={() => setDialog(null)}
                      onDeleted={() => { void queryClient.invalidateQueries({ queryKey: ['institution', 'batches'] }); navigate('/institution/batches') }} />
      )}
    </div>
  )
}

function StatusSteps({ status }: { status: BatchStatus }) {
  if (status === 'REVOKED') return null
  const current = STEPS.findIndex((s) => s.status === status)
  return (
    <ol className="mt-6 grid grid-cols-4 gap-2" aria-label="Progress">
      {STEPS.map((step, i) => {
        const done = i < current || status === 'ANCHORED'
        const active = i === current && status !== 'ANCHORED'
        return (
          <li key={step.status} className="flex flex-col gap-2">
            <span className={`h-1.5 rounded-full ${done ? 'bg-gold-400' : active ? 'animate-pulse bg-gold-300/70' : 'bg-white/15'}`} />
            <span className={`text-xs ${done || active ? 'font-semibold text-white' : 'text-navy-200'}`}>
              {step.label}{active && status !== 'DRAFT' ? '…' : ''}
            </span>
          </li>
        )
      })}
    </ol>
  )
}

function ChainRecord({ batch }: { batch: Batch }) {
  return (
    <Panel className="p-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="flex items-center gap-2 font-bold text-slate-900"><Icon name="chain" className="h-5 w-5 text-navy-600" /> Blockchain record</h2>
        {batch.explorerTxUrl && (
          <a href={batch.explorerTxUrl} target="_blank" rel="noreferrer"
             className="inline-flex items-center gap-1.5 rounded-lg bg-navy-50 px-3 py-1.5 text-sm font-semibold text-navy-700 hover:bg-navy-100">
            View on Etherscan <Icon name="external" className="h-4 w-4" />
          </a>
        )}
      </div>
      <dl className="mt-4 grid gap-4 text-sm sm:grid-cols-4">
        <div><dt className="text-slate-500">Transaction</dt><dd className="font-mono">{batch.txHash ? shorten(batch.txHash) : '—'}</dd></div>
        <div><dt className="text-slate-500">Block</dt><dd className="font-medium">{batch.blockNumber != null ? `#${batch.blockNumber}` : 'Waiting'}</dd></div>
        <div><dt className="text-slate-500">Recorded on</dt><dd className="font-medium">{formatDateTime(batch.anchoredAt)}</dd></div>
        <div><dt className="text-slate-500">Batch fingerprint</dt><dd className="font-mono">{batch.merkleRoot ? shorten(batch.merkleRoot) : '—'}</dd></div>
      </dl>
    </Panel>
  )
}

function CertificateRow({ certificate: c, draft, removing, onRemove }: {
  certificate: Certificate
  draft: boolean
  removing: boolean
  onRemove: () => void
}) {
  const status = CERTIFICATE_STATUS[c.status]
  return (
    <tr className="transition hover:bg-slate-50/60">
      <td className={td}>
        <p className="font-semibold text-slate-900">{c.studentName}</p>
        <p className="text-xs text-slate-500">{c.enrollmentNo}</p>
      </td>
      <td className={td}>
        <p>{c.title}</p>
        <p className="text-xs text-slate-500">{c.certificateNumber}</p>
      </td>
      <td className={`${td} whitespace-nowrap`}>{[c.cgpa != null ? `CGPA ${c.cgpa}` : null, c.grade].filter(Boolean).join(' · ') || '—'}</td>
      <td className={td}><Badge tone={status.tone} dot>{status.label}</Badge></td>
      <td className={`${td} whitespace-nowrap text-right`}>
        {draft && (
          <button type="button" onClick={onRemove} disabled={removing}
                  className="rounded-lg px-2.5 py-1.5 text-sm font-medium text-red-600 hover:bg-red-50 disabled:opacity-50">
            {removing ? 'Removing…' : 'Remove'}
          </button>
        )}
        {c.pdfAvailable && <DownloadPdfButton certificate={c} />}
        {c.certHash && c.status !== 'PENDING' && (
          <Link to={`/verify/${c.certHash}`} target="_blank"
                className="inline-flex items-center gap-1 rounded-lg px-2.5 py-1.5 text-sm font-semibold text-navy-700 hover:bg-navy-50">
            Verify <Icon name="external" className="h-3.5 w-3.5" />
          </Link>
        )}
      </td>
    </tr>
  )
}

function IssueDialog({ batch, count, canIssue, onClose, onIssued }: {
  batch: Batch
  count: number
  canIssue: boolean
  onClose: () => void
  onIssued: () => void
}) {
  const issue = useMutation({ mutationFn: () => institutionApi.issueBatch(batch.id), onSuccess: onIssued })
  return (
    <Modal title={`Issue "${batch.title}"?`} onClose={onClose} locked={issue.isPending}
           footer={<>
             <Button variant="secondary" onClick={onClose} disabled={issue.isPending}>Cancel</Button>
             <Button variant="gold" disabled={!canIssue} loading={issue.isPending} onClick={() => issue.mutate()}>
               Issue {count} {count === 1 ? 'certificate' : 'certificates'}
             </Button>
           </>}>
      <ul className="list-inside list-disc space-y-1.5 text-sm text-slate-600">
        <li>The batch is frozen: you can't add, change or remove certificates after this.</li>
        <li>All {count} certificates are recorded on the Ethereum blockchain in one transaction. This usually takes under a minute.</li>
        <li>Once confirmed, each certificate gets its PDF with a QR code, and anyone can verify it.</li>
        <li>A mistake later can only be fixed by revoking that certificate and issuing a new one.</li>
      </ul>
      {!canIssue && <div className="mt-4"><Alert tone="warning">Your blockchain wallet is not ready yet.</Alert></div>}
      {issue.isError && <div className="mt-4"><Alert tone="error">{errorMessage(issue.error)}</Alert></div>}
    </Modal>
  )
}

function DeleteDialog({ batch, onClose, onDeleted }: { batch: Batch; onClose: () => void; onDeleted: () => void }) {
  const del = useMutation({ mutationFn: () => institutionApi.deleteBatch(batch.id), onSuccess: onDeleted })
  return (
    <Modal title={`Delete "${batch.title}"?`} onClose={onClose} locked={del.isPending}
           footer={<>
             <Button variant="secondary" onClick={onClose} disabled={del.isPending}>Cancel</Button>
             <Button variant="danger" loading={del.isPending} onClick={() => del.mutate()}>Delete batch</Button>
           </>}>
      <p className="text-sm text-slate-600">The draft batch and its {batch.certificateCount} draft certificates are deleted. Students are not affected.</p>
      {del.isError && <div className="mt-4"><Alert tone="error">{errorMessage(del.error)}</Alert></div>}
    </Modal>
  )
}
