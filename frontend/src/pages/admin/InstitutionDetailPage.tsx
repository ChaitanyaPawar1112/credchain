import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type ReactNode } from 'react'
import { Link, useParams } from 'react-router'
import { adminApi, type Institution, type InstitutionApproval } from '../../api/admin'
import { Alert } from '../../components/Alert'
import { Badge } from '../../components/Badge'
import { Button } from '../../components/Button'
import { Icon } from '../../components/Icon'
import { Modal } from '../../components/Modal'
import { LoadError, LoadingRows, Panel } from '../../components/Panel'
import { errorMessage } from '../../components/errors'
import { formatDateTime, shorten } from '../../lib/format'
import { INSTITUTION_STATUS, INSTITUTION_TYPE, WALLET_STATUS } from './labels'

type Dialog = 'approve' | 'reject' | 'suspend' | 'reinstate' | null

/** Super admin: one college's application, its wallet, and the approve / reject / suspend / reinstate actions. */
export function InstitutionDetailPage() {
  const { id = '' } = useParams()
  const queryClient = useQueryClient()
  const [dialog, setDialog] = useState<Dialog>(null)
  const [approval, setApproval] = useState<InstitutionApproval | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const institution = useQuery({ queryKey: ['admin', 'institution', id], queryFn: () => adminApi.institution(id) })
  const hasWallet = institution.data?.status === 'APPROVED' || institution.data?.status === 'SUSPENDED'
  const wallet = useQuery({
    queryKey: ['admin', 'institution', id, 'wallet'],
    queryFn: () => adminApi.wallet(id),
    enabled: hasWallet,
    retry: false,
  })

  const afterChange = (updated: Institution, message: string | null) => {
    queryClient.setQueryData(['admin', 'institution', id], updated)
    void queryClient.invalidateQueries({ queryKey: ['admin', 'institutions'] })
    void queryClient.invalidateQueries({ queryKey: ['admin', 'institution', id, 'wallet'] })
    setDialog(null)
    setNotice(message)
  }

  if (institution.isPending) return <Panel><LoadingRows /></Panel>
  if (institution.isError) return <Panel><LoadError error={institution.error} onRetry={() => institution.refetch()} /></Panel>
  const i = institution.data
  const status = INSTITUTION_STATUS[i.status]

  return (
    <div className="animate-fade-up mx-auto max-w-5xl space-y-6">
      <Link to="/admin/institutions" className="inline-flex items-center gap-1 text-sm font-medium text-slate-500 hover:text-navy-700">
        <Icon name="arrowRight" className="h-4 w-4 rotate-180" /> All college applications
      </Link>

      <section className="bg-brand rounded-3xl p-6 text-white shadow-xl sm:p-8">
        <div className="flex flex-col gap-5 sm:flex-row sm:items-start sm:justify-between">
          <div className="flex items-start gap-4">
            <span className="flex h-14 w-14 shrink-0 items-center justify-center rounded-2xl bg-white/10 text-gold-300 ring-1 ring-white/15">
              <Icon name="building" className="h-7 w-7" />
            </span>
            <div>
              <Badge tone={status.tone} dot>{status.label}</Badge>
              <h1 className="mt-2 text-2xl font-extrabold tracking-tight sm:text-3xl">{i.name}</h1>
              <p className="mt-1 text-sm text-navy-100">{i.code} · {INSTITUTION_TYPE[i.type]} · {i.city}, {i.state}</p>
            </div>
          </div>
          <div className="flex flex-wrap gap-2">
            {i.status === 'PENDING' && (
              <>
                <Button variant="gold" onClick={() => setDialog('approve')}><Icon name="check" className="h-4 w-4" /> Approve</Button>
                <Button variant="secondary" onClick={() => setDialog('reject')}>Reject</Button>
              </>
            )}
            {i.status === 'APPROVED' && <Button variant="secondary" onClick={() => setDialog('suspend')}>Suspend</Button>}
            {i.status === 'SUSPENDED' && <Button variant="gold" onClick={() => setDialog('reinstate')}>Reinstate</Button>}
          </div>
        </div>
      </section>

      {notice && <Alert tone="success">{notice}</Alert>}
      {approval && <TemporaryPassword approval={approval} onDone={() => setApproval(null)} />}
      {i.status === 'REJECTED' && i.rejectionReason && (
        <Alert tone="error"><strong>Rejected:</strong> {i.rejectionReason}</Alert>
      )}

      <div className="grid gap-6 lg:grid-cols-2">
        <Panel className="p-6">
          <h2 className="font-bold text-slate-900">Application</h2>
          <dl className="mt-4 space-y-3">
            <Row label="Registration / AISHE number">{i.registrationNumber}</Row>
            <Row label="Official email"><a href={`mailto:${i.email}`} className="text-navy-700 hover:underline">{i.email}</a></Row>
            {i.phone && <Row label="Phone">{i.phone}</Row>}
            {i.website && <Row label="Website"><a href={i.website} target="_blank" rel="noreferrer" className="text-navy-700 hover:underline">{i.website}</a></Row>}
            <Row label="Address">{[i.addressLine, i.city, i.state, i.postalCode, i.country].filter(Boolean).join(', ')}</Row>
            <Row label="Applied on">{formatDateTime(i.createdAt)}</Row>
            {i.reviewedAt && <Row label="Last reviewed">{formatDateTime(i.reviewedAt)}</Row>}
          </dl>
        </Panel>

        <div className="space-y-6">
          <Panel className="p-6">
            <h2 className="font-bold text-slate-900">Contact person</h2>
            <p className="mt-1 text-xs text-slate-500">Becomes the college admin. Their email is the login.</p>
            <dl className="mt-4 space-y-3">
              <Row label="Name">{i.contactPersonName}</Row>
              <Row label="Email">{i.contactPersonEmail}</Row>
            </dl>
          </Panel>

          <Panel className="p-6">
            <h2 className="flex items-center gap-2 font-bold text-slate-900"><Icon name="chain" className="h-5 w-5 text-navy-600" /> Blockchain wallet</h2>
            {!hasWallet && <p className="mt-2 text-sm text-slate-500">Created automatically when the college is approved.</p>}
            {hasWallet && wallet.isPending && <p className="mt-2 text-sm text-slate-500">Loading…</p>}
            {hasWallet && wallet.isError && <p className="mt-2 text-sm text-slate-500">{errorMessage(wallet.error)}</p>}
            {wallet.data && (
              <dl className="mt-4 space-y-3">
                <Row label="Status"><Badge tone={WALLET_STATUS[wallet.data.status].tone} dot>{WALLET_STATUS[wallet.data.status].label}</Badge></Row>
                <Row label="Address">
                  {wallet.data.explorerUrl
                    ? <a href={wallet.data.explorerUrl} target="_blank" rel="noreferrer" className="inline-flex items-center gap-1 font-mono text-sm text-navy-700 hover:underline">
                        {shorten(wallet.data.address)} <Icon name="external" className="h-3.5 w-3.5" /></a>
                    : <span className="font-mono text-sm">{shorten(wallet.data.address)}</span>}
                </Row>
                <Row label="Balance">{wallet.data.balanceEth != null ? `${wallet.data.balanceEth} ETH` : 'Not available right now'}</Row>
                {wallet.data.activatedAt && <Row label="Activated">{formatDateTime(wallet.data.activatedAt)}</Row>}
                {wallet.data.lastError && <Row label="Last problem"><span className="text-red-700">{wallet.data.lastError}</span></Row>}
              </dl>
            )}
          </Panel>
        </div>
      </div>

      {dialog === 'approve' && (
        <ConfirmDialog title={`Approve ${i.name}?`} confirmLabel="Approve" onClose={() => setDialog(null)}
                       action={() => adminApi.approve(id)}
                       onDone={(result) => { setApproval(result); afterChange(result.institution, null) }}>
          <p className="text-sm text-slate-600">This will:</p>
          <ul className="mt-2 list-inside list-disc space-y-1 text-sm text-slate-600">
            <li>create a college admin account for <strong>{i.contactPersonEmail}</strong></li>
            <li>create the college's blockchain wallet and allow it to issue certificates</li>
          </ul>
          <p className="mt-3 text-sm text-slate-600">You'll see a temporary password once, to share with the college.</p>
        </ConfirmDialog>
      )}
      {dialog === 'reject' && <RejectDialog institution={i} onClose={() => setDialog(null)}
                                            onDone={(updated) => afterChange(updated, 'The application was rejected.')} />}
      {dialog === 'suspend' && (
        <ConfirmDialog title={`Suspend ${i.name}?`} confirmLabel="Suspend" danger onClose={() => setDialog(null)}
                       action={() => adminApi.suspend(id)}
                       onDone={(updated) => afterChange(updated, 'The college is suspended. Its admins were logged out.')}>
          <p className="text-sm text-slate-600">
            Its admins are logged out at once and can't issue certificates. Certificates already issued stay valid. You can reinstate it later.
          </p>
        </ConfirmDialog>
      )}
      {dialog === 'reinstate' && (
        <ConfirmDialog title={`Reinstate ${i.name}?`} confirmLabel="Reinstate" onClose={() => setDialog(null)}
                       action={() => adminApi.reinstate(id)}
                       onDone={(updated) => afterChange(updated, 'The college is active again.')}>
          <p className="text-sm text-slate-600">Its admins can log in and issue certificates again.</p>
        </ConfirmDialog>
      )}
    </div>
  )
}

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="grid grid-cols-1 gap-0.5 sm:grid-cols-[11rem_1fr] sm:gap-4">
      <dt className="text-sm text-slate-500">{label}</dt>
      <dd className="break-words text-sm font-medium text-slate-900">{children}</dd>
    </div>
  )
}

function ConfirmDialog<T>({ title, confirmLabel, danger = false, action, onDone, onClose, children }: {
  title: string
  confirmLabel: string
  danger?: boolean
  action: () => Promise<T>
  onDone: (result: T) => void
  onClose: () => void
  children: ReactNode
}) {
  const mutation = useMutation({ mutationFn: action, onSuccess: onDone })
  return (
    <Modal title={title} onClose={onClose} locked={mutation.isPending}
           footer={<>
             <Button variant="secondary" onClick={onClose} disabled={mutation.isPending}>Cancel</Button>
             <Button variant={danger ? 'danger' : 'primary'} loading={mutation.isPending} onClick={() => mutation.mutate()}>{confirmLabel}</Button>
           </>}>
      {children}
      {mutation.isError && <div className="mt-4"><Alert tone="error">{errorMessage(mutation.error)}</Alert></div>}
    </Modal>
  )
}

function RejectDialog({ institution, onClose, onDone }: { institution: Institution; onClose: () => void; onDone: (i: Institution) => void }) {
  const [reason, setReason] = useState('')
  const mutation = useMutation({ mutationFn: () => adminApi.reject(institution.id, reason.trim()), onSuccess: onDone })
  const length = reason.trim().length
  const valid = length >= 10 && length <= 500
  return (
    <Modal title={`Reject ${institution.name}?`} onClose={onClose} locked={mutation.isPending}
           footer={<>
             <Button variant="secondary" onClick={onClose} disabled={mutation.isPending}>Cancel</Button>
             <Button variant="danger" disabled={!valid} loading={mutation.isPending} onClick={() => mutation.mutate()}>Reject application</Button>
           </>}>
      <label htmlFor="reject-reason" className="block text-sm font-medium text-slate-700">Reason (the college sees this when it checks its application)</label>
      <textarea id="reject-reason" rows={4} value={reason} onChange={(e) => setReason(e.target.value)} maxLength={500}
                placeholder="For example: the registration number could not be verified with AISHE records."
                className="mt-1.5 block w-full rounded-xl border border-slate-300 px-3.5 py-2.5 text-sm outline-none focus:border-navy-600 focus:ring-4 focus:ring-navy-100" />
      <p className={`mt-1 text-xs ${length > 0 && length < 10 ? 'text-red-600' : 'text-slate-500'}`}>
        {length < 10 ? `At least 10 characters (${length} so far)` : `${length} / 500`}
      </p>
      {mutation.isError && <div className="mt-4"><Alert tone="error">{errorMessage(mutation.error)}</Alert></div>}
    </Modal>
  )
}

/** Shown once after approval: the backend never returns this password again. */
function TemporaryPassword({ approval, onDone }: { approval: InstitutionApproval; onDone: () => void }) {
  const [copied, setCopied] = useState(false)
  const account = approval.adminAccount
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(`Email: ${account.email}\nTemporary password: ${account.temporaryPassword}`)
      setCopied(true)
    } catch {
      // clipboard blocked: the password is on screen to copy by hand
    }
  }
  return (
    <section className="rounded-3xl bg-emerald-50 p-6 ring-1 ring-emerald-200" aria-live="polite">
      <h2 className="flex items-center gap-2 font-bold text-emerald-900"><Icon name="checkCircle" /> Approved. College admin account created</h2>
      <p className="mt-1 text-sm text-emerald-900">
        Share these details with the college privately (not by group chat or public email). They must set a new password at first login.
        <strong> This password is shown only once.</strong>
      </p>
      <div className="mt-4 grid gap-3 rounded-2xl bg-white p-4 ring-1 ring-emerald-200 sm:grid-cols-2">
        <div>
          <p className="text-xs uppercase tracking-wide text-slate-500">Login email</p>
          <p className="font-medium text-slate-900">{account.email}</p>
        </div>
        <div>
          <p className="text-xs uppercase tracking-wide text-slate-500">Temporary password</p>
          <p className="font-mono text-lg font-bold tracking-wide text-slate-900" data-testid="temporary-password">{account.temporaryPassword}</p>
        </div>
      </div>
      <div className="mt-4 flex flex-wrap gap-3">
        <Button variant="secondary" onClick={copy}><Icon name={copied ? 'check' : 'copy'} className="h-4 w-4" /> {copied ? 'Copied' : 'Copy email and password'}</Button>
        <Button variant="ghost" onClick={onDone}>I've shared it, hide it</Button>
      </div>
    </section>
  )
}
