import type { ReactNode } from 'react'
import { Icon, type IconName } from '../components/Icon'

const POINTS: { icon: IconName; text: string }[] = [
  { icon: 'shield', text: 'Every certificate is fingerprinted and anchored on Ethereum' },
  { icon: 'qr', text: 'Share one link or QR code instead of photocopies' },
  { icon: 'lock', text: 'Edited or forged PDFs are caught as FAKE' },
]

/** Login, sign-up and change-password: brand panel on the left (large screens), form card on the right. */
export function AuthCard({ title, subtitle, children }: { title: string; subtitle?: ReactNode; children: ReactNode }) {
  return (
    <div className="mx-auto grid max-w-6xl items-start gap-10 px-4 py-10 sm:py-14 lg:grid-cols-2 lg:items-center">
      <aside className="bg-brand hidden rounded-3xl p-10 text-white shadow-xl lg:block">
        <p className="text-sm font-semibold uppercase tracking-wider text-gold-300">CredChain</p>
        <h2 className="mt-3 text-3xl font-extrabold leading-tight">
          Your certificates,<br />verifiable by anyone.
        </h2>
        <ul className="mt-8 space-y-4">
          {POINTS.map((p) => (
            <li key={p.text} className="flex items-start gap-3">
              <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-white/10 text-gold-300 ring-1 ring-white/15">
                <Icon name={p.icon} className="h-5 w-5" />
              </span>
              <span className="pt-1.5 text-sm text-navy-100">{p.text}</span>
            </li>
          ))}
        </ul>
      </aside>

      <div className="animate-fade-up mx-auto w-full max-w-md rounded-3xl bg-white p-8 shadow-xl shadow-navy-900/5 ring-1 ring-slate-200 sm:p-10">
        <h1 className="text-2xl font-bold tracking-tight text-slate-900">{title}</h1>
        {subtitle && <p className="mt-1.5 text-sm text-slate-600">{subtitle}</p>}
        <div className="mt-7">{children}</div>
      </div>
    </div>
  )
}
