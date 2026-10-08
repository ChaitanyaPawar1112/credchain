import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { Button } from '../components/Button'

const STEPS = [
  { title: 'Colleges issue', text: 'Certificates are fingerprinted and recorded on the Ethereum blockchain in batches.' },
  { title: 'Students share', text: 'Every PDF carries a QR code that opens its live status.' },
  { title: 'Anyone verifies', text: 'Scan the QR, paste the hash or upload the PDF. Edited copies show as FAKE.' },
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
      <section className="bg-navy-900 text-white">
        <div className="mx-auto max-w-6xl px-4 py-16 sm:py-24">
          <p className="text-sm font-semibold uppercase tracking-wider text-gold-400">Blockchain-backed certificates</p>
          <h1 className="mt-3 max-w-2xl text-4xl font-bold tracking-tight sm:text-5xl">
            Check any CredChain certificate in seconds
          </h1>
          <p className="mt-4 max-w-xl text-lg text-navy-100">
            No login needed. Paste the certificate hash, or upload the PDF on the verify page.
          </p>
          <form onSubmit={handleVerify} className="mt-8 flex max-w-2xl flex-col gap-3 sm:flex-row">
            <label htmlFor="hash" className="sr-only">Certificate hash</label>
            <input id="hash" value={hash} onChange={(e) => setHash(e.target.value)} placeholder="0x… certificate hash"
                   className="flex-1 rounded-lg border-0 bg-white px-4 py-3 font-mono text-sm text-slate-900 placeholder:text-slate-400 outline-none ring-2 ring-transparent focus:ring-gold-400" />
            <Button type="submit" variant="gold" className="px-6">
              Verify
            </Button>
          </form>
        </div>
      </section>

      <section className="mx-auto grid max-w-6xl gap-6 px-4 py-14 sm:grid-cols-3">
        {STEPS.map((step, i) => (
          <div key={step.title} className="rounded-xl bg-white p-6 shadow-sm ring-1 ring-slate-200">
            <span className="flex h-8 w-8 items-center justify-center rounded-full bg-navy-50 text-sm font-bold text-navy-700">
              {i + 1}
            </span>
            <h2 className="mt-4 font-semibold text-slate-900">{step.title}</h2>
            <p className="mt-1 text-sm text-slate-600">{step.text}</p>
          </div>
        ))}
      </section>

      <section className="mx-auto max-w-6xl px-4 pb-16 text-sm text-slate-600">
        Are you a college? <Link to="/login" className="font-medium text-navy-700 hover:underline">Log in</Link> with
        the admin account CredChain created for you.
      </section>
    </div>
  )
}
