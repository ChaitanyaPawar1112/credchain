import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router'
import { adminApi, type InstitutionTrust, type TrustLevel } from '../../api/admin'
import { Alert } from '../../components/Alert'
import { Badge, type BadgeTone } from '../../components/Badge'
import { Button } from '../../components/Button'
import { Icon } from '../../components/Icon'
import { PageHeader } from '../../components/PageHeader'
import { EmptyState, LoadError, LoadingRows, Panel, td, th } from '../../components/Panel'
import { StatTile } from '../../components/StatTile'
import { errorMessage } from '../../components/errors'
import { formatDateTime, timeAgo } from '../../lib/format'

export const TRUST_LOOK: Record<TrustLevel, { label: string; tone: BadgeTone; bar: string }> = {
  TRUSTED: { label: 'Trusted', tone: 'green', bar: 'bg-emerald-500' },
  WATCH: { label: 'Watch', tone: 'amber', bar: 'bg-amber-500' },
  AT_RISK: { label: 'At risk', tone: 'red', bar: 'bg-red-600' },
}

/**
 * Super admin: an AI trust score per college. Every night the fraud-detection service looks at 90 days of
 * activity (Isolation Forest on issues, revocations and checks per day) plus rules for revocations and forged
 * copies. Lowest score first, so the colleges to look at are on top.
 */
export function InstitutionTrustPage() {
  const queryClient = useQueryClient()
  const trust = useQuery({ queryKey: ['admin', 'trust'], queryFn: adminApi.trust })
  const recompute = useMutation({
    mutationFn: adminApi.recomputeTrust,
    onSuccess: (report) => queryClient.setQueryData(['admin', 'trust'], report),
  })
  const d = trust.data
  const count = (level: TrustLevel) => d?.institutions.filter((i) => i.level === level).length

  return (
    <div className="animate-fade-up mx-auto max-w-6xl space-y-6">
      <PageHeader icon="gauge" title="College trust scores"
                  description={<>
                    AI check of each college's activity: unusual days (Isolation Forest), revocations and forged copies.
                    It runs every night; {d?.computedAt
                      ? <>last run <span title={formatDateTime(d.computedAt)}>{timeAgo(d.computedAt)}</span>.</>
                      : 'it has not run yet.'}
                  </>}
                  actions={
                    <Button variant="secondary" loading={recompute.isPending} onClick={() => recompute.mutate()}>
                      <Icon name="sparkle" className="h-4 w-4" /> {recompute.isPending ? 'Analysing…' : 'Run now'}
                    </Button>
                  } />

      {recompute.isError && <Alert tone="error">{errorMessage(recompute.error)}</Alert>}
      {recompute.isSuccess && <Alert tone="success">Trust scores updated.</Alert>}
      {trust.isError && <Panel><LoadError error={trust.error} onRetry={() => trust.refetch()} /></Panel>}

      <div className="grid gap-4 sm:grid-cols-3">
        <StatTile label="Trusted (80 to 100)" value={count('TRUSTED')} icon="checkCircle" tone="green" />
        <StatTile label="Watch (50 to 79)" value={count('WATCH')} icon="alert" tone={count('WATCH') ? 'amber' : 'slate'} />
        <StatTile label="At risk (below 50)" value={count('AT_RISK')} icon="xCircle" tone={count('AT_RISK') ? 'red' : 'slate'} />
      </div>

      <Panel>
        {trust.isPending && <LoadingRows />}
        {d && d.institutions.length === 0 && (
          <EmptyState icon="gauge" title="No approved colleges yet" text="Colleges get a trust score once they are approved." />
        )}
        {d && d.institutions.length > 0 && (
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-slate-100">
              <thead className="bg-slate-50/80">
                <tr>
                  <th className={th}>College</th>
                  <th className={th}>Trust score</th>
                  <th className={th}>Why</th>
                  <th className={th}>Checked</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {d.institutions.map((i) => <TrustRow key={i.institutionId} entry={i} />)}
              </tbody>
            </table>
          </div>
        )}
      </Panel>

      <p className="text-xs text-slate-500">
        A low score is a reason to look closer, not proof of fraud. "Rules only" means the college has too little history
        on CredChain yet for the machine-learning model.
      </p>
    </div>
  )
}

function TrustRow({ entry }: { entry: InstitutionTrust }) {
  const look = entry.level ? TRUST_LOOK[entry.level] : null
  return (
    <tr className="transition hover:bg-slate-50/60">
      <td className={td}>
        <Link to={`/admin/institutions/${entry.institutionId}`} className="font-semibold text-slate-900 hover:text-navy-700">
          {entry.name}
        </Link>
        <p className="text-xs text-slate-500">{entry.code}</p>
      </td>
      <td className={`${td} min-w-44`}>
        {entry.trustScore == null || !look ? (
          <span className="text-slate-400">Not analysed yet</span>
        ) : (
          <>
            <div className="flex items-center gap-2">
              <span className="w-8 text-lg font-bold text-slate-900">{entry.trustScore}</span>
              <Badge tone={look.tone} dot>{look.label}</Badge>
            </div>
            <div className="mt-1.5 h-1.5 w-36 overflow-hidden rounded-full bg-slate-200">
              <div className={`h-full rounded-full ${look.bar}`} style={{ width: `${Math.max(entry.trustScore, 2)}%` }} />
            </div>
          </>
        )}
      </td>
      <td className={td}>
        {entry.reasons.length > 0 ? (
          <ul className="list-disc space-y-0.5 pl-4">
            {entry.reasons.map((reason) => <li key={reason}>{reason}</li>)}
          </ul>
        ) : <span className="text-slate-400">—</span>}
        {entry.computedAt && (
          <p className="mt-1 text-xs text-slate-500">
            {entry.modelUsed ? 'AI model + rules' : 'Rules only'}
            {entry.anomalyCount > 0 && ` · ${entry.anomalyCount} unusual ${entry.anomalyCount === 1 ? 'day' : 'days'}`}
          </p>
        )}
      </td>
      <td className={`${td} whitespace-nowrap`} title={formatDateTime(entry.computedAt)}>
        {entry.computedAt ? timeAgo(entry.computedAt) : '—'}
      </td>
    </tr>
  )
}
