import QRCode from 'qrcode'
import { useEffect, useState } from 'react'
import type { MyCertificate } from '../../api/student'
import { Button } from '../../components/Button'
import { Icon } from '../../components/Icon'
import { Modal } from '../../components/Modal'

/** The public verify link of a certificate, as text, a QR code and quick share buttons. Anyone with it can check the certificate. */
export function ShareModal({ certificate, onClose }: { certificate: MyCertificate; onClose: () => void }) {
  const url = certificate.verificationUrl
  const [qr, setQr] = useState<string | null>(null)
  const [copied, setCopied] = useState(false)

  useEffect(() => {
    let live = true
    QRCode.toDataURL(url, { width: 220, margin: 1, color: { dark: '#0b1f3a', light: '#ffffff' } })
      .then((data) => live && setQr(data))
      .catch(() => live && setQr(null))
    return () => { live = false }
  }, [url])

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(url)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch {
      setCopied(false)
    }
  }

  const text = `My ${certificate.title} from ${certificate.institutionName}, verifiable on the blockchain:`
  const links = [
    { label: 'WhatsApp', href: `https://wa.me/?text=${encodeURIComponent(`${text} ${url}`)}` },
    { label: 'LinkedIn', href: `https://www.linkedin.com/sharing/share-offsite/?url=${encodeURIComponent(url)}` },
    { label: 'Email', href: `mailto:?subject=${encodeURIComponent(`${certificate.title} - ${certificate.institutionName}`)}&body=${encodeURIComponent(`${text}\n${url}`)}` },
  ]
  const canShare = typeof navigator !== 'undefined' && typeof navigator.share === 'function'

  return (
    <Modal title="Share your certificate" onClose={onClose} footer={<Button onClick={onClose}>Done</Button>}>
      <p className="text-sm text-slate-600">
        Anyone with this link sees whether <strong>{certificate.title}</strong> is genuine, straight from the blockchain. They don't need an account.
      </p>
      <div className="mt-5 flex flex-col items-center gap-5 sm:flex-row sm:items-start">
        <div className="flex h-[156px] w-[156px] shrink-0 items-center justify-center rounded-2xl bg-white p-2 ring-1 ring-slate-200">
          {qr ? <img src={qr} alt="QR code for the verify link" className="h-full w-full" data-testid="share-qr" />
              : <Icon name="qr" className="h-10 w-10 text-slate-300" />}
        </div>
        <div className="w-full min-w-0 space-y-3">
          <div className="flex items-center gap-2 rounded-xl bg-slate-50 p-2 ring-1 ring-slate-200">
            <input readOnly value={url} aria-label="Verify link" onFocus={(e) => e.target.select()}
                   className="min-w-0 flex-1 bg-transparent px-1 font-mono text-xs text-slate-700 outline-none" />
            <button type="button" onClick={copy}
                    className="inline-flex shrink-0 items-center gap-1 rounded-lg bg-navy-700 px-2.5 py-1.5 text-xs font-semibold text-white hover:bg-navy-800">
              <Icon name={copied ? 'check' : 'copy'} className="h-3.5 w-3.5" /> {copied ? 'Copied' : 'Copy'}
            </button>
          </div>
          <div className="flex flex-wrap gap-2">
            {canShare && (
              <button type="button" onClick={() => navigator.share({ title: certificate.title, text, url }).catch(() => undefined)}
                      className="rounded-lg bg-white px-3 py-1.5 text-sm font-semibold text-slate-700 ring-1 ring-slate-300 hover:bg-slate-50">
                Share…
              </button>
            )}
            {links.map((l) => (
              <a key={l.label} href={l.href} target="_blank" rel="noreferrer"
                 className="rounded-lg bg-white px-3 py-1.5 text-sm font-semibold text-slate-700 ring-1 ring-slate-300 hover:bg-slate-50">{l.label}</a>
            ))}
          </div>
          <a href={url} target="_blank" rel="noreferrer" className="inline-flex items-center gap-1 text-sm font-semibold text-navy-700 hover:underline">
            See what they will see <Icon name="external" className="h-3.5 w-3.5" />
          </a>
        </div>
      </div>
    </Modal>
  )
}
