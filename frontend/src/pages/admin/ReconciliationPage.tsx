import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { adminApi } from '../../api/admin'
import { Alert } from '../../components/Alert'
import { Badge } from '../../components/Badge'
import { PageHeader } from '../../components/PageHeader'
import { Pagination } from '../../components/Pagination'
import { EmptyState, LoadError, LoadingRows, Panel, td, th } from '../../components/Panel'
import { StatTile } from '../../components/StatTile'
import { formatDateTime, titleCase } from '../../lib/format'

/**
 * Super admin: the background job compares every issued certificate with the smart contract.
 * This page shows the totals and every certificate where the two disagree.
 */
export function ReconciliationPage() {
  const [page, setPage] = useState(0)
  const report = useQuery({
    queryKey: ['admin', 'reconciliation', page],
    queryFn: () => adminApi.reconciliation(page),
    placeholderData: (previous) => previous,
  })
  const r = report.data

  return (
    <div className="animate-fade-up mx-auto max-w-6xl space-y-6">
      <PageHeader icon="chain" title="Blockchain check"
                  description="CredChain regularly compares every issued certificate with what is recorded on Ethereum." />

      {report.isError && <Panel><LoadError error={report.error} onRetry={() => report.refetch()} /></Panel>}

      <div className="grid gap-4 sm:grid-cols-3">
        <StatTile label="Certificates on the blockchain" value={r?.onChainCertificates} icon="chain" />
        <StatTile label="Not checked yet" value={r?.neverChecked} icon="clock" tone="slate"
                  hint="New certificates are picked up by the next check." />
        <StatTile label="Don't match" value={r?.mismatches} icon="alert" tone={r && r.mismatches > 0 ? 'red' : 'green'} />
      </div>

      {r && r.mismatches === 0 && (
        <Alert tone="success">Everything matches: the database and the blockchain agree on every checked certificate.</Alert>
      )}

      {(report.isPending || (r && r.mismatches > 0)) && (
        <Panel>
          <div className="border-b border-slate-100 px-5 py-4">
            <h2 className="font-bold text-slate-900">Certificates that don't match</h2>
            <p className="text-sm text-slate-500">Look into each one: the database may be out of date, or the chain was changed outside CredChain.</p>
          </div>
          {report.isPending && <LoadingRows />}
          {r && r.mismatchList.content.length === 0 && <EmptyState icon="checkCircle" title="Nothing on this page" />}
          {r && r.mismatchList.content.length > 0 && (
            <>
              <div className="overflow-x-auto">
                <table className="min-w-full divide-y divide-slate-100">
                  <thead className="bg-slate-50/80">
                    <tr>
                      <th className={th}>Certificate</th>
                      <th className={th}>In the database</th>
                      <th className={th}>What was found</th>
                      <th className={th}>Checked</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {r.mismatchList.content.map((m) => (
                      <tr key={m.certificateId}>
                        <td className={td}>
                          <p className="font-semibold text-slate-900">{m.certificateNumber}</p>
                          <p className="text-xs text-slate-500">{m.studentName}</p>
                        </td>
                        <td className={td}><Badge tone="blue">{titleCase(m.databaseStatus)}</Badge></td>
                        <td className={`${td} text-red-700`}>{m.note ?? '—'}</td>
                        <td className={`${td} whitespace-nowrap`}>{formatDateTime(m.checkedAt)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <Pagination data={r.mismatchList} onPage={setPage} />
            </>
          )}
        </Panel>
      )}
    </div>
  )
}
