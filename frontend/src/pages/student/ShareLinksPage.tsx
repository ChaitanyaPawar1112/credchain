import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'
import { shareApi, type ShareLink, type ShareLinkStatus } from '../../api/share'
import { Badge, type BadgeTone } from '../../components/Badge'
import { Icon } from '../../components/Icon'
import { PageHeader } from '../../components/PageHeader'
import { Pagination } from '../../components/Pagination'
import { EmptyState, LoadError, LoadingRows, Panel, td, th } from '../../components/Panel'
import { errorMessage } from '../../components/errors'
import { formatDate, timeAgo } from '../../lib/format'

const STATUS_LOOK: Record<ShareLinkStatus, { label: string; tone: BadgeTone }> = {
  ACTIVE: { label: 'Active', tone: 'green' },
  EXPIRED: { label: 'Expired', tone: 'slate' },
  REVOKED: { label: 'Turned off', tone: 'red' },
}

/** Student: every link they created, how often each was opened, and a way to turn one off. */
export function ShareLinksPage() {
  const [page, setPage] = useState(0)
  const links = useQuery({
    queryKey: ['student', 'share-links', page],
    queryFn: () => shareApi.list(page),
    placeholderData: (previous) => previous,
  })

  return (
    <div className="animate-fade-up mx-auto max-w-5xl space-y-6">
      <PageHeader icon="external" title="Share links"
                  description="Links you gave to employers or others. See how often each was opened, and turn off any you no longer want to work." />
      <Panel>
        {links.isPending && <LoadingRows />}
        {links.isError && <LoadError error={links.error} onRetry={() => links.refetch()} />}
        {links.data && links.data.content.length === 0 && (
          <EmptyState icon="external" title="No share links yet"
                      text="Open My certificates, press Share on a certificate, and create a private link." />
        )}
        {links.data && links.data.content.length > 0 && (
          <>
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead className="bg-slate-50">
                  <tr>
                    <th className={th}>For</th>
                    <th className={th}>Certificate</th>
                    <th className={th}>Status</th>
                    <th className={`${th} text-right`}>Opened</th>
                    <th className={th}>Works until</th>
                    <th className={th}><span className="sr-only">Actions</span></th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {links.data.content.map((l) => <LinkRow key={l.id} link={l} />)}
                </tbody>
              </table>
            </div>
            {links.data.totalPages > 1 && <Pagination data={links.data} onPage={setPage} />}
          </>
        )}
      </Panel>
      <p className="text-center text-sm text-slate-500">
        To create a new link, go to <Link to="/student/certificates" className="font-semibold text-navy-700 hover:underline">My certificates</Link>.
      </p>
    </div>
  )
}

function LinkRow({ link: l }: { link: ShareLink }) {
  const queryClient = useQueryClient()
  const [copied, setCopied] = useState(false)
  const revoke = useMutation({
    mutationFn: () => shareApi.revoke(l.id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['student', 'share-links'] }),
  })
  const look = STATUS_LOOK[l.status]
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(l.url)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch {
      setCopied(false)
    }
  }
  return (
    <tr>
      <td className={`${td} font-medium text-slate-900`}>{l.name}</td>
      <td className={td}>
        {l.certificateTitle ?? '—'}
        {l.certificateNumber && <span className="block font-mono text-xs text-slate-400">{l.certificateNumber}</span>}
      </td>
      <td className={td}><Badge tone={look.tone} dot>{look.label}</Badge></td>
      <td className={`${td} text-right`}>
        <span className="font-semibold tabular-nums">{l.viewCount}</span> {l.viewCount === 1 ? 'time' : 'times'}
        {l.lastViewedAt && <span className="block text-xs text-slate-400">last {timeAgo(l.lastViewedAt)}</span>}
      </td>
      <td className={td}>{l.status === 'REVOKED' ? '—' : formatDate(l.expiresAt)}</td>
      <td className={`${td} whitespace-nowrap text-right`}>
        {l.status === 'ACTIVE' && (
          <div className="flex justify-end gap-1">
            <button type="button" onClick={copy}
                    className="inline-flex items-center gap-1 rounded-lg px-2.5 py-1.5 text-sm font-semibold text-navy-700 hover:bg-navy-50">
              <Icon name={copied ? 'check' : 'copy'} className="h-4 w-4" /> {copied ? 'Copied' : 'Copy'}
            </button>
            <button type="button" onClick={() => revoke.mutate()} disabled={revoke.isPending}
                    title={revoke.isError ? errorMessage(revoke.error) : 'The link stops working for everyone'}
                    className="rounded-lg px-2.5 py-1.5 text-sm font-semibold text-red-600 hover:bg-red-50 disabled:opacity-50">
              {revoke.isPending ? 'Turning off…' : revoke.isError ? 'Try again' : 'Turn off'}
            </button>
          </div>
        )}
      </td>
    </tr>
  )
}
