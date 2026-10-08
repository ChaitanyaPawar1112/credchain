import type { ReactNode } from 'react'

export type BadgeTone = 'green' | 'red' | 'amber' | 'blue' | 'slate' | 'gold' | 'purple'

const TONES: Record<BadgeTone, string> = {
  green: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
  red: 'bg-red-50 text-red-700 ring-red-200',
  amber: 'bg-amber-50 text-amber-800 ring-amber-200',
  blue: 'bg-navy-50 text-navy-700 ring-navy-100',
  slate: 'bg-slate-100 text-slate-600 ring-slate-200',
  gold: 'bg-gold-50 text-gold-600 ring-gold-100',
  purple: 'bg-violet-50 text-violet-700 ring-violet-200',
}

/** Small rounded status label. */
export function Badge({ tone = 'slate', children, dot = false }: { tone?: BadgeTone; children: ReactNode; dot?: boolean }) {
  return (
    <span className={`inline-flex items-center gap-1.5 whitespace-nowrap rounded-full px-2.5 py-0.5 text-xs font-semibold ring-1 ${TONES[tone]}`}>
      {dot && <span className="h-1.5 w-1.5 rounded-full bg-current" />}
      {children}
    </span>
  )
}
