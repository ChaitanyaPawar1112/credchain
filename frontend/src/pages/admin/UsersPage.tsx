import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { adminApi } from '../../api/admin'
import { ROLE_LABEL } from '../../auth/roles'
import { Avatar } from '../../components/Avatar'
import { Badge } from '../../components/Badge'
import { PageHeader } from '../../components/PageHeader'
import { Pagination } from '../../components/Pagination'
import { EmptyState, LoadError, LoadingRows, Panel, td, th } from '../../components/Panel'
import { formatDate, timeAgo } from '../../lib/format'
import { ROLE_TONE, USER_STATUS } from './labels'

/** Super admin: every account on the platform, newest first. */
export function UsersPage() {
  const [page, setPage] = useState(0)
  const users = useQuery({
    queryKey: ['admin', 'users', page],
    queryFn: () => adminApi.users(page),
    placeholderData: (previous) => previous,
  })

  return (
    <div className="animate-fade-up mx-auto max-w-6xl space-y-6">
      <PageHeader icon="users" title="Users"
                  description={users.data ? `${users.data.totalElements} accounts, newest first.` : 'Every account on CredChain, newest first.'} />
      <Panel>
        {users.isPending && <LoadingRows />}
        {users.isError && <LoadError error={users.error} onRetry={() => users.refetch()} />}
        {users.data && users.data.content.length === 0 && <EmptyState icon="users" title="No users yet" />}
        {users.data && users.data.content.length > 0 && (
          <>
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-slate-100">
                <thead className="bg-slate-50/80">
                  <tr>
                    <th className={th}>Name</th>
                    <th className={th}>Role</th>
                    <th className={th}>Status</th>
                    <th className={th}>Last login</th>
                    <th className={th}>Joined</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {users.data.content.map((u) => (
                    <tr key={u.id} className="transition hover:bg-slate-50/60">
                      <td className={td}>
                        <div className="flex items-center gap-3">
                          <Avatar name={u.fullName} />
                          <div className="min-w-0">
                            <p className="font-semibold text-slate-900">{u.fullName}</p>
                            <p className="truncate text-xs text-slate-500">{u.email}{u.phone ? ` · ${u.phone}` : ''}</p>
                          </div>
                        </div>
                      </td>
                      <td className={td}><Badge tone={ROLE_TONE[u.role]}>{ROLE_LABEL[u.role]}</Badge></td>
                      <td className={td}><Badge tone={USER_STATUS[u.status].tone} dot>{USER_STATUS[u.status].label}</Badge></td>
                      <td className={`${td} whitespace-nowrap`}>{timeAgo(u.lastLoginAt)}</td>
                      <td className={`${td} whitespace-nowrap`}>{formatDate(u.createdAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination data={users.data} onPage={setPage} />
          </>
        )}
      </Panel>
    </div>
  )
}
