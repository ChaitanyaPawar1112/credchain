import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { adminApi } from '../../api/admin'
import { institutionApi } from '../../api/institution'
import type { VerificationStatus } from '../../api/verify'
import { Badge } from '../../components/Badge'
import { Icon } from '../../components/Icon'
import { PageHeader } from '../../components/PageHeader'
import { Pagination } from '../../components/Pagination'
import { EmptyState, FilterTabs, LoadError, LoadingRows, Panel, td, th } from '../../components/Panel'
import { formatDateTime, timeAgo } from '../../lib/format'
import { RESULT_LOOK, describeBrowser } from './labels'

type Filter = VerificationStatus | 'ALL'
const RESULTS: VerificationStatus[] = ['VALID', 'FAKE', 'REVOKED', 'EXPIRED', 'NOT_FOUND']

const SCOPES = {
  admin: {
    fetch: adminApi.verifications,
    title: 'Verification log',
    description: 'Every time someone checks a certificate on CredChain: by QR link, hash or PDF upload.',
  },
  institution: {
    fetch: institutionApi.verifications,
    title: 'Verification activity',
    description: "Every time an employer or anyone else checks one of your college's certificates.",
  },
}

/**
 * Public certificate checks with totals per result. The super admin sees all of them (FAKE shows forged
 * certificates in use); a college admin sees the checks of its own certificates.
 */
export function VerificationLogPage({ scope = 'admin' }: { scope?: keyof typeof SCOPES }) {
  const source = SCOPES[scope]
  const [params, setParams] = useSearchParams()
  const requested = params.get('result') as VerificationStatus | null
  const filter: Filter = requested && RESULTS.includes(requested) ? requested : 'ALL'
  const [page, setPage] = useState(0)

  const log = useQuery({
    queryKey: [scope, 'verifications', filter, page],
    queryFn: () => source.fetch(filter === 'ALL' ? undefined : filter, page),
    placeholderData: (previous) => previous,
  })
  const d = log.data

  const changeFilter = (value: Filter) => {
    setPage(0)
    setParams(value === 'ALL' ? {} : { result: value }, { replace: true })
  }

  return (
    <div className="animate-fade-up mx-auto max-w-6xl space-y-6">
      <PageHeader icon="activity" title={source.title} description={source.description} />

      <FilterTabs value={filter} onChange={changeFilter} options={[
        { value: 'ALL' as Filter, label: 'All checks', count: d?.totalChecks },
        ...RESULTS.map((r) => ({ value: r as Filter, label: RESULT_LOOK[r].label, count: d?.byResult[r] })),
      ]} />

      {d && d.byResult.FAKE > 0 && filter !== 'FAKE' && (
        <button type="button" onClick={() => changeFilter('FAKE')}
                className="flex w-full items-center gap-3 rounded-2xl bg-red-50 px-5 py-4 text-left ring-1 ring-red-200 transition hover:bg-red-100">
          <Icon name="alert" className="h-5 w-5 shrink-0 text-red-600" />
          <span className="text-sm text-red-800">
            <strong>{d.byResult.FAKE} fake or edited {d.byResult.FAKE === 1 ? 'certificate was' : 'certificates were'} checked.</strong> Click to see them.
          </span>
        </button>
      )}

      <Panel>
        {log.isPending && <LoadingRows />}
        {log.isError && <LoadError error={log.error} onRetry={() => log.refetch()} />}
        {d && d.checks.content.length === 0 && (
          <EmptyState icon="activity" title="No checks yet"
                      text={filter === 'ALL' ? 'Checks appear here as soon as anyone verifies a certificate.' : 'No check had this result.'} />
        )}
        {d && d.checks.content.length > 0 && (
          <>
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-slate-100">
                <thead className="bg-slate-50/80">
                  <tr>
                    <th className={th}>When</th>
                    <th className={th}>Result</th>
                    <th className={th}>How</th>
                    <th className={th}>Certificate</th>
                    <th className={th}>Checked from</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {d.checks.content.map((c) => (
                    <tr key={c.id} className="transition hover:bg-slate-50/60">
                      <td className={`${td} whitespace-nowrap`} title={formatDateTime(c.checkedAt)}>{timeAgo(c.checkedAt)}</td>
                      <td className={td}>
                        <Badge tone={RESULT_LOOK[c.result].tone} dot>{RESULT_LOOK[c.result].label}</Badge>
                        {!c.blockchainChecked && c.result !== 'FAKE' && c.result !== 'NOT_FOUND' && (
                          <p className="mt-1 text-xs text-amber-700">database only</p>
                        )}
                      </td>
                      <td className={td}>
                        <span className="inline-flex items-center gap-1.5 whitespace-nowrap">
                          <Icon name={c.method === 'PDF' ? 'file' : 'qr'} className="h-4 w-4 text-slate-400" />
                          {c.method === 'PDF' ? 'PDF upload' : 'QR link / hash'}
                        </span>
                      </td>
                      <td className={td}>
                        {c.certificateNumber ? (
                          <>
                            <p className="font-semibold text-slate-900">{c.certificateNumber}</p>
                            <p className="text-xs text-slate-500">{c.studentName}</p>
                          </>
                        ) : c.certHash ? (
                          <Link to={`/verify/${c.certHash}`} className="font-mono text-xs text-slate-500 hover:text-navy-700">
                            {c.certHash.slice(0, 18)}…
                          </Link>
                        ) : <span className="text-slate-400">Not a CredChain certificate</span>}
                      </td>
                      <td className={`${td} whitespace-nowrap`} title={c.userAgent ?? undefined}>{describeBrowser(c.userAgent)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination data={d.checks} onPage={setPage} />
          </>
        )}
      </Panel>
    </div>
  )
}
