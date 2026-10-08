import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { institutionApi } from '../../api/institution'
import { Alert } from '../../components/Alert'
import { Badge } from '../../components/Badge'
import { Button } from '../../components/Button'
import { Icon } from '../../components/Icon'
import { Modal } from '../../components/Modal'
import { PageHeader } from '../../components/PageHeader'
import { Pagination } from '../../components/Pagination'
import { EmptyState, LoadError, LoadingRows, Panel, td, th } from '../../components/Panel'
import { TextField } from '../../components/TextField'
import { errorMessage, fieldErrors } from '../../components/errors'
import { formatDate, timeAgo } from '../../lib/format'
import { BATCH_STATUS } from './labels'

/** College admin: batches of certificates. Each batch is recorded on the blockchain in one transaction. */
export function BatchesPage() {
  const [page, setPage] = useState(0)
  const [creating, setCreating] = useState(false)
  const batches = useQuery({
    queryKey: ['institution', 'batches', page],
    queryFn: () => institutionApi.batches(page),
    placeholderData: (previous) => previous,
    // keep the status fresh while a batch is on its way to the blockchain
    refetchInterval: (query) => (query.state.data?.content.some((b) => b.status === 'QUEUED' || b.status === 'SUBMITTED') ? 5000 : false),
  })

  return (
    <div className="animate-fade-up mx-auto max-w-6xl space-y-6">
      <PageHeader icon="layers" title="Batches"
                  description="Group certificates (for example one convocation) and record them on the blockchain together."
                  actions={<Button onClick={() => setCreating(true)}>New batch</Button>} />

      <Panel>
        {batches.isPending && <LoadingRows />}
        {batches.isError && <LoadError error={batches.error} onRetry={() => batches.refetch()} />}
        {batches.data && batches.data.content.length === 0 && (
          <EmptyState icon="layers" title="No batches yet" text="Create a batch, add certificates to it, then issue it." />
        )}
        {batches.data && batches.data.content.length > 0 && (
          <>
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-slate-100">
                <thead className="bg-slate-50/80">
                  <tr>
                    <th className={th}>Batch</th>
                    <th className={th}>Certificates</th>
                    <th className={th}>Status</th>
                    <th className={th}>Recorded</th>
                    <th className={th}><span className="sr-only">Open</span></th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {batches.data.content.map((b) => (
                    <tr key={b.id} className="transition hover:bg-slate-50/60">
                      <td className={td}>
                        <Link to={`/institution/batches/${b.id}`} className="font-semibold text-slate-900 hover:text-navy-700">{b.title}</Link>
                        {b.expiresAt && <p className="text-xs text-slate-500">Valid until {formatDate(b.expiresAt)}</p>}
                      </td>
                      <td className={td}>{b.certificateCount}</td>
                      <td className={td}>
                        <Badge tone={BATCH_STATUS[b.status].tone} dot>{BATCH_STATUS[b.status].label}</Badge>
                        {b.lastError && b.status !== 'ANCHORED' && <p className="mt-1 text-xs text-amber-700">retrying</p>}
                      </td>
                      <td className={`${td} whitespace-nowrap`}>{b.anchoredAt ? timeAgo(b.anchoredAt) : '—'}</td>
                      <td className={`${td} text-right`}>
                        <Link to={`/institution/batches/${b.id}`} aria-label={`Open ${b.title}`}
                              className="inline-flex items-center gap-1 rounded-lg px-2.5 py-1.5 text-sm font-semibold text-navy-700 hover:bg-navy-50">
                          Open <Icon name="arrowRight" className="h-4 w-4" />
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination data={batches.data} onPage={setPage} />
          </>
        )}
      </Panel>

      {creating && <NewBatchModal onClose={() => setCreating(false)} />}
    </div>
  )
}

function NewBatchModal({ onClose }: { onClose: () => void }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [title, setTitle] = useState('')
  const [expires, setExpires] = useState('')
  const create = useMutation({
    // a validity date means "valid until the end of that day" in India
    mutationFn: () => institutionApi.createBatch(title.trim(), expires ? new Date(`${expires}T23:59:59+05:30`).toISOString() : undefined),
    onSuccess: (batch) => {
      void queryClient.invalidateQueries({ queryKey: ['institution', 'batches'] })
      navigate(`/institution/batches/${batch.id}`)
    },
  })
  const errors = create.isError ? fieldErrors(create.error) : {}
  const submit = (event: FormEvent) => {
    event.preventDefault()
    create.mutate()
  }
  return (
    <Modal title="New batch" onClose={onClose} locked={create.isPending}
           footer={<>
             <Button variant="secondary" onClick={onClose} disabled={create.isPending}>Cancel</Button>
             <Button type="submit" form="new-batch" disabled={!title.trim()} loading={create.isPending}>Create batch</Button>
           </>}>
      <form id="new-batch" onSubmit={submit} className="space-y-4" noValidate>
        {create.isError && <Alert tone="error">{errorMessage(create.error)}</Alert>}
        <TextField label="Batch name" required value={title} onChange={(e) => setTitle(e.target.value)} error={errors.title}
                   placeholder="B.Tech Convocation 2026" />
        <TextField label="Valid until (optional)" type="date" value={expires} onChange={(e) => setExpires(e.target.value)}
                   error={errors.expiresAt} hint="Leave empty for certificates that never expire, like degrees." />
      </form>
    </Modal>
  )
}
