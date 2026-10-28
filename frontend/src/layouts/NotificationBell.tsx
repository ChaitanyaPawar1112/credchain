import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { notificationsApi, type AppNotification, type NotificationType } from '../api/notifications'
import { Icon, type IconName } from '../components/Icon'
import { timeAgo } from '../lib/format'

/** How often the number on the bell is refreshed. */
const POLL_MS = 30_000

const TYPE_LOOK: Record<NotificationType, { icon: IconName; tone: string }> = {
  WELCOME: { icon: 'sparkle', tone: 'bg-gold-100 text-gold-600' },
  CERTIFICATE_ISSUED: { icon: 'certificate', tone: 'bg-emerald-100 text-emerald-700' },
  CERTIFICATE_REVOKED: { icon: 'x', tone: 'bg-rose-100 text-rose-700' },
  CERTIFICATE_CHECKED: { icon: 'shield', tone: 'bg-sky-100 text-sky-700' },
  BATCH_ANCHORED: { icon: 'chain', tone: 'bg-emerald-100 text-emerald-700' },
  FAKE_COPY_CHECKED: { icon: 'alert', tone: 'bg-rose-100 text-rose-700' },
  COLLEGE_AT_RISK: { icon: 'gauge', tone: 'bg-amber-100 text-amber-700' },
}

/**
 * The bell with the unread count; opens the latest notifications. Clicking one marks it read and opens its page.
 * light = drawn on the dark sidebar.
 */
export function NotificationBell({ light = false, align = 'left' }: { light?: boolean; align?: 'left' | 'right' }) {
  const [open, setOpen] = useState(false)
  const box = useRef<HTMLDivElement>(null)
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const count = useQuery({
    queryKey: ['notifications', 'count'],
    queryFn: notificationsApi.unreadCount,
    refetchInterval: POLL_MS,
  })
  const list = useQuery({
    queryKey: ['notifications', 'list'],
    queryFn: () => notificationsApi.list(),
    enabled: open,
  })
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['notifications'] })
  const markRead = useMutation({ mutationFn: notificationsApi.markRead, onSuccess: refresh })
  const markAll = useMutation({ mutationFn: notificationsApi.markAllRead, onSuccess: refresh })

  // close on a click outside or Escape
  useEffect(() => {
    if (!open) return
    const onClick = (e: MouseEvent) => {
      if (box.current && !box.current.contains(e.target as Node)) setOpen(false)
    }
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') setOpen(false) }
    document.addEventListener('mousedown', onClick)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onClick)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  const unread = list.data?.unreadCount ?? count.data?.unreadCount ?? 0
  const items = list.data?.notifications.content ?? []

  const openItem = (n: AppNotification) => {
    if (!n.read) markRead.mutate(n.id)
    setOpen(false)
    if (n.link) navigate(n.link)
  }

  return (
    <div ref={box} className="relative">
      <button
        onClick={() => setOpen((o) => !o)}
        aria-label={unread > 0 ? `Notifications, ${unread} unread` : 'Notifications'}
        aria-expanded={open}
        className={`relative rounded-lg p-2 transition ${light
          ? 'text-navy-100 hover:bg-white/10 hover:text-white' : 'text-slate-700 hover:bg-slate-100'}`}
      >
        <Icon name="bell" className="h-5 w-5" />
        {unread > 0 && (
          <span className="absolute -right-0.5 -top-0.5 flex h-5 min-w-5 items-center justify-center rounded-full
                           bg-rose-500 px-1 text-[11px] font-bold text-white ring-2 ring-white">
            {unread > 99 ? '99+' : unread}
          </span>
        )}
      </button>

      {open && (
        <div role="dialog" aria-label="Notifications"
             className={`absolute top-full z-50 mt-2 w-[min(22rem,calc(100vw-2rem))] overflow-hidden rounded-2xl
                         bg-white text-slate-800 shadow-2xl ring-1 ring-slate-200 ${align === 'right' ? 'right-0' : 'left-0'}`}>
          <div className="flex items-center justify-between border-b border-slate-100 px-4 py-3">
            <p className="text-sm font-semibold">Notifications</p>
            {unread > 0 && (
              <button onClick={() => markAll.mutate()} disabled={markAll.isPending}
                      className="text-xs font-medium text-navy-600 hover:underline disabled:opacity-50">
                Mark all as read
              </button>
            )}
          </div>
          <div className="max-h-96 overflow-y-auto">
            {list.isPending && <p className="px-4 py-6 text-center text-sm text-slate-500">Loading…</p>}
            {list.isError && <p className="px-4 py-6 text-center text-sm text-rose-600">Could not load notifications.</p>}
            {list.isSuccess && items.length === 0 && (
              <p className="px-4 py-8 text-center text-sm text-slate-500">You have no notifications yet.</p>
            )}
            {items.map((n) => {
              const look = TYPE_LOOK[n.type] ?? { icon: 'bell' as IconName, tone: 'bg-slate-100 text-slate-600' }
              return (
                <button key={n.id} onClick={() => openItem(n)}
                        className={`flex w-full gap-3 border-b border-slate-50 px-4 py-3 text-left transition hover:bg-slate-50
                                    ${n.read ? '' : 'bg-sky-50/50'}`}>
                  <span className={`mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-full ${look.tone}`}>
                    <Icon name={look.icon} className="h-4 w-4" />
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="flex items-start gap-2">
                      <span className={`flex-1 text-sm ${n.read ? 'font-medium text-slate-700' : 'font-semibold'}`}>{n.title}</span>
                      {!n.read && <span className="mt-1.5 h-2 w-2 shrink-0 rounded-full bg-sky-500" aria-label="unread" />}
                    </span>
                    <span className="mt-0.5 block text-xs leading-relaxed text-slate-600">{n.body}</span>
                    <span className="mt-1 block text-[11px] text-slate-400">{timeAgo(n.createdAt)}</span>
                  </span>
                </button>
              )
            })}
          </div>
        </div>
      )}
    </div>
  )
}
