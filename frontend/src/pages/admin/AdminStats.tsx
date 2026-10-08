import { useQuery } from '@tanstack/react-query'
import { adminApi } from '../../api/admin'
import { StatTile } from '../../components/StatTile'

/** Numbers at the top of the super admin overview; each tile opens its page. */
export function AdminStats() {
  const pending = useQuery({ queryKey: ['admin', 'institutions', 'PENDING', 'count'], queryFn: () => adminApi.institutions('PENDING', 0, 1),
    select: (d) => d.totalElements })
  const colleges = useQuery({ queryKey: ['admin', 'institutions', 'APPROVED', 'count'], queryFn: () => adminApi.institutions('APPROVED', 0, 1),
    select: (d) => d.totalElements })
  const users = useQuery({ queryKey: ['admin', 'users', 'count'], queryFn: () => adminApi.users(0, 1), select: (d) => d.totalElements })
  const checks = useQuery({ queryKey: ['admin', 'verifications', 'ALL', 'count'], queryFn: () => adminApi.verifications(undefined, 0, 1) })
  const chain = useQuery({ queryKey: ['admin', 'reconciliation', 'count'], queryFn: () => adminApi.reconciliation(0, 1) })

  return (
    <div className="mt-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
      <StatTile label="Applications waiting" value={pending.data} icon="building" tone={pending.data ? 'amber' : 'slate'}
                to="/admin/institutions" hint={colleges.data !== undefined ? `${colleges.data} approved` : undefined} />
      <StatTile label="Users" value={users.data} icon="users" to="/admin/users" />
      <StatTile label="Certificate checks" value={checks.data?.totalChecks} icon="activity" to="/admin/verifications"
                tone={checks.data?.byResult.FAKE ? 'red' : 'green'}
                hint={checks.data ? `${checks.data.byResult.FAKE} fake or edited` : undefined} />
      <StatTile label="Blockchain mismatches" value={chain.data?.mismatches} icon="chain" to="/admin/reconciliation"
                tone={chain.data?.mismatches ? 'red' : 'green'}
                hint={chain.data ? `${chain.data.onChainCertificates} certificates on chain` : undefined} />
    </div>
  )
}
