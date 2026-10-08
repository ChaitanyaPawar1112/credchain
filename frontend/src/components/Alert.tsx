import type { ReactNode } from 'react'

type Tone = 'error' | 'success' | 'info' | 'warning'

const TONES: Record<Tone, string> = {
  error: 'bg-red-50 text-red-800 ring-red-200',
  success: 'bg-emerald-50 text-emerald-800 ring-emerald-200',
  info: 'bg-navy-50 text-navy-800 ring-navy-100',
  warning: 'bg-amber-50 text-amber-900 ring-amber-200',
}

export function Alert({ tone = 'info', children }: { tone?: Tone; children: ReactNode }) {
  return (
    <div role={tone === 'error' ? 'alert' : 'status'} className={`rounded-xl px-4 py-3 text-sm ring-1 ${TONES[tone]}`}>
      {children}
    </div>
  )
}
