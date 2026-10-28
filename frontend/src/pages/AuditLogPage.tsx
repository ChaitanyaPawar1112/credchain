import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { auditApi, type AuditAction, type AuditCategory } from '../api/audit'
import { Badge, type BadgeTone } from '../components/Badge'
import { PageHeader } from '../components/PageHeader'
import { Pagination } from '../components/Pagination'
import { EmptyState, FilterTabs, LoadError, LoadingRows, Panel, td, th } from '../components/Panel'
import { formatDateTime, timeAgo } from '../lib/format'

type Filter = AuditCategory | 'ALL'

const SCOPES = {
  admin: {
    fetch: auditApi.platform,
    description: 'Every important action on CredChain: logins, college reviews, students, batches and revocations. Entries can never be edited or deleted.',
    categories: ['ACCOUNT', 'INSTITUTION', 'STUDENT', 'CERTIFICATE', 'SYSTEM'] as AuditCategory[],
  },
  institution: {
    fetch: auditApi.institution,
    description: "Who did what in your college's account: logins, students, batches and revocations. Entries can never be edited or deleted.",
    categories: ['ACCOUNT', 'STUDENT', 'CERTIFICATE'] as AuditCategory[],
  },
}

const CATEGORY_LABEL: Record<AuditCategory, string> = {
  ACCOUNT: 'Logins & accounts',
  INSTITUTION: 'Colleges',
  STUDENT: 'Students',
  CERTIFICATE: 'Certificates',
  SYSTEM: 'System',
}

/** Actions that deserve a second look stand out in the list. */
const ACTION_TONE: Partial<Record<AuditAction, BadgeTone>> = {
  LOGIN_FAILED: 'amber',
  LOGIN_BLOCKED: 'red',
  INSTITUTION_REJECTED: 'red',
  INSTITUTION_SUSPENDED: 'red',
  CERTIFICATE_REVOKED: 'red',
  INSTITUTION_APPROVED: 'green',
  BATCH_ISSUED: 'green',
}

const ROLE_LABEL: Record<string, string> = {
  SUPER_ADMIN: 'Super admin',
  INSTITUTION_ADMIN: 'College admin',
  STUDENT: 'Student',
  VERIFIER: 'Verifier',
  SYSTEM: 'System',
}

export const actionLabel = (action: AuditAction) =>
  action.charAt(0) + action.slice(1).toLowerCase().replaceAll('_', ' ')

/** The audit log: super admin sees everything, a college admin sees their own college. */
export function AuditLogPage({ scope = 'admin' }: { scope?: keyof typeof SCOPES }) {
  const source = SCOPES[scope]
  const [filter, setFilter] = useState<Filter>('ALL')
  const [page, setPage] = useState(0)

  const log = useQuery({
    queryKey: [scope, 'audit', filter, page],
    queryFn: () => source.fetch(filter === 'ALL' ? undefined : filter, page),
    placeholderData: (previous) => previous,
  })
  const d = log.data

  const changeFilter = (value: Filter) => {
    setPage(0)
    setFilter(value)
  }

  return (
    <div className="animate-fade-up mx-auto max-w-6xl space-y-6">
      <PageHeader icon="history" title="Audit log" description={source.description} />

      <FilterTabs value={filter} onChange={changeFilter} options={[
        { value: 'ALL' as Filter, label: 'Everything' },
        ...source.categories.map((c) => ({ value: c as Filter, label: CATEGORY_LABEL[c] })),
      ]} />

      <Panel>
        {log.isPending && <LoadingRows />}
        {log.isError && <LoadError error={log.error} onRetry={() => log.refetch()} />}
        {d && d.content.length === 0 && (
          <EmptyState icon="history" title="Nothing recorded yet"
                      text={filter === 'ALL' ? 'Actions appear here as soon as someone logs in or changes something.' : 'Nothing in this group yet.'} />
        )}
        {d && d.content.length > 0 && (
          <>
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-slate-100">
                <thead className="bg-slate-50/80">
                  <tr>
                    <th className={th}>When</th>
                    <th className={th}>Who</th>
                    <th className={th}>What happened</th>
                    <th className={th}>From</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {d.content.map((e) => (
                    <tr key={e.id} className="transition hover:bg-slate-50/60">
                      <td className={`${td} whitespace-nowrap`} title={formatDateTime(e.occurredAt)}>{timeAgo(e.occurredAt)}</td>
                      <td className={td}>
                        <p className="font-semibold text-slate-900">{e.actorEmail ?? (e.actorRole === 'SYSTEM' ? 'CredChain' : 'Unknown')}</p>
                        <p className="text-xs text-slate-500">{e.actorRole ? ROLE_LABEL[e.actorRole] ?? e.actorRole : 'Not logged in'}</p>
                      </td>
                      <td className={td}>
                        <Badge tone={ACTION_TONE[e.action] ?? 'slate'}>{actionLabel(e.action)}</Badge>
                        <p className="mt-1 text-slate-800">{e.summary}</p>
                      </td>
                      <td className={`${td} whitespace-nowrap font-mono text-xs`}>{e.ipAddress ?? '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination data={d} onPage={setPage} />
          </>
        )}
      </Panel>
    </div>
  )
}
