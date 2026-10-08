import { Link } from 'react-router'
import { Icon, type IconName } from './Icon'

const TONES = {
  navy: 'bg-navy-50 text-navy-700',
  green: 'bg-emerald-50 text-emerald-600',
  red: 'bg-red-50 text-red-600',
  amber: 'bg-amber-50 text-amber-600',
  slate: 'bg-slate-100 text-slate-600',
  gold: 'bg-gold-100 text-gold-600',
} as const

/** One number with a label, optionally a link. */
export function StatTile({ label, value, icon, tone = 'navy', to, hint }: {
  label: string
  value: number | string | undefined
  icon: IconName
  tone?: keyof typeof TONES
  to?: string
  hint?: string
}) {
  const body = (
    <>
      <div className="flex items-center justify-between">
        <p className="text-sm font-medium text-slate-500">{label}</p>
        <span className={`flex h-9 w-9 items-center justify-center rounded-xl ${TONES[tone]}`}>
          <Icon name={icon} className="h-5 w-5" />
        </span>
      </div>
      <p className="mt-2 font-display text-3xl font-extrabold tracking-tight text-slate-900">
        {value === undefined ? <span className="inline-block h-8 w-12 animate-pulse rounded bg-slate-100" /> : value}
      </p>
      {hint && <p className="mt-1 text-xs text-slate-500">{hint}</p>}
    </>
  )
  const style = 'block rounded-2xl bg-white p-5 shadow-sm ring-1 ring-slate-200'
  return to
    ? <Link to={to} className={`${style} transition hover:-translate-y-0.5 hover:shadow-md hover:ring-navy-200`}>{body}</Link>
    : <div className={style}>{body}</div>
}
