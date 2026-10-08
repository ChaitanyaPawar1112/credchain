import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { Button } from '../components/Button'
import { Icon, type IconName } from '../components/Icon'

const STEPS: { icon: IconName; title: string; text: string }[] = [
  { icon: 'building', title: 'Colleges issue',
    text: 'Each certificate is fingerprinted and recorded on the Ethereum blockchain in batches.' },
  { icon: 'qr', title: 'Students share',
    text: 'Every PDF carries a QR code that opens its live status. No screenshots, no calls to the college.' },
  { icon: 'shield', title: 'Anyone verifies',
    text: 'Scan the QR, paste the hash or upload the PDF. Edited or forged copies show as FAKE.' },
]

const AUDIENCES: { icon: IconName; title: string; text: string; cta: string; to: string }[] = [
  { icon: 'building', title: 'For colleges', text: 'Issue tamper-proof degrees and mark sheets for a whole batch in one go.',
    cta: 'Apply as a college', to: '/apply' },
  { icon: 'student', title: 'For students', text: 'Keep every certificate in one place and share a link that proves it is real.',
    cta: 'Create a student account', to: '/register' },
  { icon: 'briefcase', title: 'For employers', text: 'Check a candidate\'s certificate in seconds, with no paperwork.',
    cta: 'Verify a certificate', to: '/verify' },
]

const FACTS = [
  { value: 'Ethereum', label: 'Recorded on a public blockchain' },
  { value: 'Merkle proof', label: 'Inside every PDF' },
  { value: 'No login', label: 'Needed to verify' },
]

export function HomePage() {
  const navigate = useNavigate()
  const [hash, setHash] = useState('')

  const handleVerify = (event: FormEvent) => {
    event.preventDefault()
    const value = hash.trim()
    navigate(value ? `/verify/${encodeURIComponent(value)}` : '/verify')
  }

  return (
    <div>
      {/* Hero */}
      <section className="bg-brand relative overflow-hidden text-white">
        <div className="mx-auto grid max-w-6xl items-center gap-12 px-4 py-16 sm:py-24 lg:grid-cols-[1.1fr_0.9fr]">
          <div className="animate-fade-up">
            <span className="inline-flex items-center gap-2 rounded-full bg-white/10 px-3 py-1 text-xs font-semibold
                             uppercase tracking-wider text-gold-300 ring-1 ring-white/15">
              <span className="h-1.5 w-1.5 rounded-full bg-gold-400" />
              Blockchain-backed certificates
            </span>
            <h1 className="mt-5 text-4xl font-extrabold leading-tight tracking-tight sm:text-5xl lg:text-6xl">
              Certificates that{' '}
              <span className="bg-gradient-to-r from-gold-300 to-gold-400 bg-clip-text text-transparent">prove themselves</span>
            </h1>
            <p className="mt-5 max-w-xl text-lg text-navy-100">
              Check any CredChain certificate in seconds. No login needed: paste the certificate hash below, or upload
              the PDF on the verify page.
            </p>

            <form onSubmit={handleVerify}
                  className="mt-8 flex max-w-xl flex-col gap-2 rounded-2xl bg-white/10 p-2 ring-1 ring-white/15 backdrop-blur sm:flex-row">
              <label htmlFor="hash" className="sr-only">Certificate hash</label>
              <div className="relative flex-1">
                <span className="pointer-events-none absolute inset-y-0 left-3 flex items-center text-slate-400">
                  <Icon name="search" />
                </span>
                <input id="hash" value={hash} onChange={(e) => setHash(e.target.value)} placeholder="0x… certificate hash"
                       className="w-full rounded-xl border-0 bg-white py-3 pl-10 pr-4 font-mono text-sm text-slate-900
                                  placeholder:text-slate-400 outline-none ring-2 ring-transparent focus:ring-gold-400" />
              </div>
              <Button type="submit" variant="gold" className="rounded-xl px-6 py-3">
                Verify <Icon name="arrowRight" className="h-4 w-4" />
              </Button>
            </form>
            <p className="mt-3 flex items-center gap-2 text-sm text-navy-200">
              <Icon name="upload" className="h-4 w-4" />
              Have the PDF instead? <Link to="/verify?tab=pdf" className="font-semibold text-white underline-offset-4 hover:underline">Upload it</Link>
            </p>
          </div>

          <CertificatePreview />
        </div>

        <div className="border-t border-white/10 bg-white/5">
          <dl className="mx-auto grid max-w-6xl grid-cols-1 divide-white/10 px-4 sm:grid-cols-3 sm:divide-x">
            {FACTS.map((f) => (
              <div key={f.value} className="py-5 text-center">
                <dt className="font-display text-lg font-bold text-white">{f.value}</dt>
                <dd className="text-sm text-navy-200">{f.label}</dd>
              </div>
            ))}
          </dl>
        </div>
      </section>

      {/* How it works */}
      <section className="mx-auto max-w-6xl px-4 py-20">
        <p className="text-center text-sm font-semibold uppercase tracking-wider text-gold-500">How it works</p>
        <h2 className="mt-2 text-center text-3xl font-bold tracking-tight text-slate-900">Three steps, zero paperwork</h2>
        <div className="mt-12 grid gap-6 md:grid-cols-3">
          {STEPS.map((step, i) => (
            <div key={step.title}
                 className="group relative rounded-2xl bg-white p-7 shadow-sm ring-1 ring-slate-200 transition
                            hover:-translate-y-1 hover:shadow-lg hover:ring-navy-200">
              <span className="absolute right-6 top-5 font-display text-5xl font-extrabold text-slate-100">{i + 1}</span>
              <span className="relative flex h-12 w-12 items-center justify-center rounded-xl bg-gradient-to-br
                               from-navy-600 to-navy-800 text-gold-300 shadow-md">
                <Icon name={step.icon} className="h-6 w-6" />
              </span>
              <h3 className="relative mt-5 text-lg font-bold text-slate-900">{step.title}</h3>
              <p className="relative mt-2 text-sm leading-relaxed text-slate-600">{step.text}</p>
            </div>
          ))}
        </div>
      </section>

      {/* Who it's for */}
      <section className="bg-white py-20">
        <div className="mx-auto max-w-6xl px-4">
          <h2 className="text-center text-3xl font-bold tracking-tight text-slate-900">Built for everyone in the chain</h2>
          <div className="mt-12 grid gap-6 md:grid-cols-3">
            {AUDIENCES.map((a) => (
              <div key={a.title} className="flex flex-col rounded-2xl bg-slate-50 p-7 ring-1 ring-slate-200">
                <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-gold-100 text-gold-600">
                  <Icon name={a.icon} className="h-6 w-6" />
                </span>
                <h3 className="mt-5 text-lg font-bold text-slate-900">{a.title}</h3>
                <p className="mt-2 flex-1 text-sm leading-relaxed text-slate-600">{a.text}</p>
                <Link to={a.to} className="mt-5 inline-flex items-center gap-1.5 text-sm font-semibold text-navy-700 hover:gap-2.5 transition-all">
                  {a.cta} <Icon name="arrowRight" className="h-4 w-4" />
                </Link>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Call to action */}
      <section className="mx-auto max-w-6xl px-4 py-20">
        <div className="bg-brand flex flex-col items-start justify-between gap-6 overflow-hidden rounded-3xl px-8 py-12
                        text-white shadow-xl sm:flex-row sm:items-center sm:px-12">
          <div>
            <h2 className="text-2xl font-bold sm:text-3xl">Got a certificate to check?</h2>
            <p className="mt-2 text-navy-100">It takes less than a minute and you don't need an account.</p>
          </div>
          <Link to="/verify"
                className="inline-flex shrink-0 items-center gap-2 rounded-xl bg-gold-400 px-6 py-3 text-sm font-bold
                           text-navy-900 shadow-lg transition hover:bg-gold-300">
            Verify now <Icon name="arrowRight" className="h-4 w-4" />
          </Link>
        </div>
      </section>
    </div>
  )
}

/** Decorative certificate card on the right of the hero. */
function CertificatePreview() {
  return (
    <div className="relative hidden lg:block" aria-hidden="true">
      <div className="absolute -inset-6 rounded-[2rem] bg-gold-400/20 blur-3xl" />
      <div className="animate-float relative rounded-2xl bg-white p-6 text-slate-900 shadow-2xl ring-1 ring-black/5">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-navy-700 text-gold-400">
              <Icon name="student" className="h-5 w-5" />
            </span>
            <div>
              <p className="text-xs font-semibold text-slate-900">Pune Institute of Technology</p>
              <p className="text-[11px] text-slate-500">Degree certificate</p>
            </div>
          </div>
          <span className="inline-flex items-center gap-1 rounded-full bg-emerald-50 px-2.5 py-1 text-xs font-bold text-emerald-700 ring-1 ring-emerald-200">
            <Icon name="check" className="h-3.5 w-3.5" /> VALID
          </span>
        </div>

        <div className="mt-6 border-t border-dashed border-slate-200 pt-5">
          <p className="text-[11px] uppercase tracking-wider text-slate-400">Awarded to</p>
          <p className="font-display text-2xl font-bold text-navy-800">Asha Patil</p>
          <p className="mt-1 text-sm text-slate-600">Bachelor of Technology · CGPA 8.50</p>
        </div>

        <div className="mt-5 grid grid-cols-2 gap-3 text-xs">
          <div className="rounded-lg bg-slate-50 p-3">
            <p className="text-slate-400">Blockchain</p>
            <p className="font-semibold text-slate-800">Ethereum Sepolia</p>
          </div>
          <div className="rounded-lg bg-slate-50 p-3">
            <p className="text-slate-400">Block</p>
            <p className="font-semibold text-slate-800">#6 284 117</p>
          </div>
        </div>
        <div className="mt-3 flex items-center gap-3 rounded-lg bg-navy-50 p-3">
          <Icon name="chain" className="h-4 w-4 shrink-0 text-navy-600" />
          <p className="truncate font-mono text-[11px] text-navy-700">0x7f3a9c2e…b41d08e6f5a2c97d</p>
        </div>
      </div>
    </div>
  )
}
