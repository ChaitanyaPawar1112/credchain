import { useQuery } from '@tanstack/react-query'
import { institutionApi } from '../../api/institution'
import { Badge } from '../../components/Badge'
import { Icon } from '../../components/Icon'
import { StatTile } from '../../components/StatTile'
import { errorMessage } from '../../components/errors'
import { formatDateTime, shorten } from '../../lib/format'
import { WALLET_STATUS } from '../admin/labels'

/** Top of the college admin overview: whether the college can issue right now, and its numbers. */
export function InstitutionStats() {
  const profile = useQuery({ queryKey: ['institution', 'profile'], queryFn: institutionApi.profile })
  const wallet = useQuery({ queryKey: ['institution', 'wallet'], queryFn: institutionApi.wallet, retry: false,
    refetchInterval: (query) => (query.state.data && !query.state.data.canIssue ? 15000 : false) })
  const students = useQuery({ queryKey: ['institution', 'students', 'count'], queryFn: () => institutionApi.students('', 0, 1),
    select: (d) => d.totalElements })
  const issued = useQuery({ queryKey: ['institution', 'certificates', 'ISSUED', 'count'],
    queryFn: () => institutionApi.certificates('ISSUED', '', 0, 1), select: (d) => d.totalElements })
  const drafts = useQuery({ queryKey: ['institution', 'certificates', 'DRAFT', 'count'],
    queryFn: () => institutionApi.certificates('DRAFT', '', 0, 1), select: (d) => d.totalElements })
  const checks = useQuery({ queryKey: ['institution', 'verifications', 'ALL', 'count'], queryFn: () => institutionApi.verifications(undefined, 0, 1) })

  const w = wallet.data
  const look = w ? WALLET_STATUS[w.status] : null

  return (
    <>
      <section className="mt-8 rounded-2xl bg-white p-6 shadow-sm ring-1 ring-slate-200">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex items-start gap-4">
            <span className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-xl ${w?.canIssue ? 'bg-emerald-50 text-emerald-600' : 'bg-amber-50 text-amber-600'}`}>
              <Icon name="chain" className="h-5 w-5" />
            </span>
            <div>
              <h2 className="font-bold text-slate-900">
                {profile.data ? `${profile.data.name} (${profile.data.code})` : 'Your college'}
              </h2>
              {wallet.isPending && <p className="text-sm text-slate-500">Checking your blockchain wallet…</p>}
              {wallet.isError && <p className="text-sm text-slate-500">{errorMessage(wallet.error)}</p>}
              {w && (
                <p className="text-sm text-slate-600">
                  {w.canIssue
                    ? 'Your blockchain wallet is active. You can issue certificates.'
                    : 'Your blockchain wallet is being set up. You can add students and prepare batches meanwhile.'}
                </p>
              )}
            </div>
          </div>
          {look && <Badge tone={look.tone} dot>{look.label}</Badge>}
        </div>
        {w && (
          <dl className="mt-5 grid gap-4 border-t border-slate-100 pt-5 text-sm sm:grid-cols-3">
            <div>
              <dt className="text-slate-500">Wallet address</dt>
              <dd>
                {w.explorerUrl
                  ? <a href={w.explorerUrl} target="_blank" rel="noreferrer" className="inline-flex items-center gap-1 font-mono text-navy-700 hover:underline">
                      {shorten(w.address)} <Icon name="external" className="h-3.5 w-3.5" /></a>
                  : <span className="font-mono">{shorten(w.address)}</span>}
              </dd>
            </div>
            <div><dt className="text-slate-500">Balance</dt><dd className="font-medium">{w.balanceEth != null ? `${w.balanceEth} ETH` : 'Not available right now'}</dd></div>
            <div><dt className="text-slate-500">Activated</dt><dd className="font-medium">{w.activatedAt ? formatDateTime(w.activatedAt) : 'Not yet'}</dd></div>
          </dl>
        )}
      </section>

      <div className="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatTile label="Students" value={students.data} icon="student" to="/institution/students" />
        <StatTile label="Certificates issued" value={issued.data} icon="certificate" tone="green" to="/institution/certificates" />
        <StatTile label="Drafts not issued yet" value={drafts.data} icon="layers" tone={drafts.data ? 'amber' : 'slate'} to="/institution/batches" />
        <StatTile label="Times checked" value={checks.data?.totalChecks} icon="activity" to="/institution/verifications"
                  hint={checks.data ? `${checks.data.byResult.VALID} genuine` : undefined} />
      </div>
    </>
  )
}
