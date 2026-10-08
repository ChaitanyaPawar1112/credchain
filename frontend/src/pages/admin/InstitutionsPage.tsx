import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { adminApi, type InstitutionStatus } from '../../api/admin'
import { Badge } from '../../components/Badge'
import { Icon } from '../../components/Icon'
import { PageHeader } from '../../components/PageHeader'
import { Pagination } from '../../components/Pagination'
import { EmptyState, FilterTabs, LoadError, LoadingRows, Panel, td, th } from '../../components/Panel'
import { timeAgo } from '../../lib/format'
import { INSTITUTION_STATUS, INSTITUTION_TYPE } from './labels'

type Filter = InstitutionStatus | 'ALL'

const FILTERS: { value: Filter; label: string }[] = [
  { value: 'PENDING', label: 'Waiting for review' },
  { value: 'APPROVED', label: 'Approved' },
  { value: 'SUSPENDED', label: 'Suspended' },
  { value: 'REJECTED', label: 'Rejected' },
  { value: 'ALL', label: 'All' },
]

const EMPTY: Record<Filter, string> = {
  PENDING: 'No applications are waiting. New ones appear here as colleges apply.',
  APPROVED: 'No college has been approved yet.',
  SUSPENDED: 'No college is suspended.',
  REJECTED: 'No application has been rejected.',
  ALL: 'No college has applied yet.',
}

/** Super admin: every college that applied, filtered by status (pending first). */
export function InstitutionsPage() {
  const [params, setParams] = useSearchParams()
  const filter = (FILTERS.some((f) => f.value === params.get('status')) ? params.get('status') : 'PENDING') as Filter
  const [page, setPage] = useState(0)
  const status = filter === 'ALL' ? undefined : filter

  const list = useQuery({
    queryKey: ['admin', 'institutions', status, page],
    queryFn: () => adminApi.institutions(status, page),
    placeholderData: (previous) => previous,
  })
  const pendingCount = useQuery({
    queryKey: ['admin', 'institutions', 'PENDING', 'count'],
    queryFn: () => adminApi.institutions('PENDING', 0, 1),
    select: (data) => data.totalElements,
  })

  const changeFilter = (value: Filter) => {
    setPage(0)
    setParams(value === 'PENDING' ? {} : { status: value }, { replace: true })
  }

  return (
    <div className="animate-fade-up mx-auto max-w-6xl space-y-6">
      <PageHeader icon="building" title="College applications"
                  description="Approve a college to create its admin account and blockchain wallet, or reject it with a reason." />

      <FilterTabs value={filter} onChange={changeFilter}
                  options={FILTERS.map((f) => (f.value === 'PENDING' ? { ...f, count: pendingCount.data } : f))} />

      <Panel>
        {list.isPending && <LoadingRows />}
        {list.isError && <LoadError error={list.error} onRetry={() => list.refetch()} />}
        {list.data && list.data.content.length === 0 && <EmptyState icon="building" title="Nothing here" text={EMPTY[filter]} />}
        {list.data && list.data.content.length > 0 && (
          <>
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-slate-100">
                <thead className="bg-slate-50/80">
                  <tr>
                    <th className={th}>College</th>
                    <th className={th}>Location</th>
                    <th className={th}>Contact person</th>
                    <th className={th}>Applied</th>
                    <th className={th}>Status</th>
                    <th className={th}><span className="sr-only">Open</span></th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {list.data.content.map((i) => (
                    <tr key={i.id} className="transition hover:bg-slate-50/60">
                      <td className={td}>
                        <Link to={`/admin/institutions/${i.id}`} className="font-semibold text-slate-900 hover:text-navy-700">{i.name}</Link>
                        <p className="text-xs text-slate-500">{i.code} · {INSTITUTION_TYPE[i.type]}</p>
                      </td>
                      <td className={td}>{i.city}, {i.state}</td>
                      <td className={td}>
                        <p>{i.contactPersonName}</p>
                        <p className="text-xs text-slate-500">{i.contactPersonEmail}</p>
                      </td>
                      <td className={`${td} whitespace-nowrap`}>{timeAgo(i.createdAt)}</td>
                      <td className={td}><Badge tone={INSTITUTION_STATUS[i.status].tone} dot>{INSTITUTION_STATUS[i.status].label}</Badge></td>
                      <td className={`${td} text-right`}>
                        <Link to={`/admin/institutions/${i.id}`} aria-label={`Open ${i.name}`}
                              className="inline-flex items-center gap-1 whitespace-nowrap rounded-lg px-2.5 py-1.5 text-sm font-semibold text-navy-700 hover:bg-navy-50">
                          {i.status === 'PENDING' ? 'Review' : 'Open'} <Icon name="arrowRight" className="h-4 w-4" />
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination data={list.data} onPage={setPage} />
          </>
        )}
      </Panel>
    </div>
  )
}
