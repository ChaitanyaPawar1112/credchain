import type { ReactNode } from 'react'
import { Alert } from './Alert'
import { Icon, type IconName } from './Icon'
import { Spinner } from './Spinner'
import { errorMessage } from './errors'

/** White rounded box that holds a table or a list. */
export function Panel({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <section className={`overflow-hidden rounded-2xl bg-white shadow-sm ring-1 ring-slate-200 ${className}`}>{children}</section>
}

export function LoadingRows() {
  return (
    <div className="flex items-center justify-center gap-3 px-5 py-14 text-sm text-slate-500" role="status">
      <Spinner className="h-5 w-5 text-navy-600" /> Loading…
    </div>
  )
}

export function LoadError({ error, onRetry }: { error: unknown; onRetry: () => void }) {
  return (
    <div className="space-y-3 p-5">
      <Alert tone="error">{errorMessage(error)}</Alert>
      <button type="button" onClick={onRetry} className="text-sm font-semibold text-navy-700 hover:underline">Try again</button>
    </div>
  )
}

export function EmptyState({ icon, title, text }: { icon: IconName; title: string; text?: string }) {
  return (
    <div className="px-5 py-14 text-center">
      <span className="mx-auto flex h-12 w-12 items-center justify-center rounded-2xl bg-slate-100 text-slate-400">
        <Icon name={icon} className="h-6 w-6" />
      </span>
      <p className="mt-3 font-semibold text-slate-900">{title}</p>
      {text && <p className="mt-1 text-sm text-slate-500">{text}</p>}
    </div>
  )
}

/** Header cell / body cell styles shared by every table. */
export const th = 'whitespace-nowrap px-5 py-3 text-left text-xs font-semibold uppercase tracking-wide text-slate-500'
export const td = 'px-5 py-3.5 align-top text-sm text-slate-700'

/** Row of filter buttons, e.g. Pending / Approved / All. */
export function FilterTabs<T extends string>({ options, value, onChange }: {
  options: { value: T; label: string; count?: number }[]
  value: T
  onChange: (value: T) => void
}) {
  return (
    <div role="tablist" className="flex flex-wrap gap-1.5">
      {options.map((o) => (
        <button key={o.value} type="button" role="tab" aria-selected={value === o.value} onClick={() => onChange(o.value)}
                className={`inline-flex items-center gap-2 rounded-xl px-3.5 py-2 text-sm font-semibold transition ${value === o.value
                  ? 'bg-navy-700 text-white shadow-sm' : 'bg-white text-slate-600 ring-1 ring-slate-200 hover:bg-slate-50'}`}>
          {o.label}
          {o.count !== undefined && (
            <span className={`rounded-full px-1.5 text-xs ${value === o.value ? 'bg-white/20' : 'bg-slate-100'}`}>{o.count}</span>
          )}
        </button>
      ))}
    </div>
  )
}
