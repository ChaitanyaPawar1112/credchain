import { useMutation, useQueryClient } from '@tanstack/react-query'
import QRCode from 'qrcode'
import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { EXPIRY_OPTIONS, shareApi, type ShareLink } from '../../api/share'
import type { MyCertificate } from '../../api/student'
import { Alert } from '../../components/Alert'
import { Button } from '../../components/Button'
import { Icon } from '../../components/Icon'
import { Modal } from '../../components/Modal'
import { SelectField } from '../../components/SelectField'
import { TextField } from '../../components/TextField'
import { errorMessage } from '../../components/errors'
import { formatDate } from '../../lib/format'

/**
 * Share a certificate: a named link that expires and can be turned off (with a view count), or the permanent
 * public verify link. Either way as text, a QR code and quick share buttons.
 */
export function ShareModal({ certificate, onClose }: { certificate: MyCertificate; onClose: () => void }) {
  const [created, setCreated] = useState<ShareLink | null>(null)
  const url = created?.url ?? certificate.verificationUrl
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
      {created ? (
        <Alert tone="success">
          Your link for <strong>{created.name}</strong> works until {formatDate(created.expiresAt)}. You can see how often it was
          opened, or turn it off, under <Link to="/student/share-links" className="font-semibold underline">Share links</Link>.
        </Alert>
      ) : (
        <NamedLinkForm certificate={certificate} onCreated={setCreated} />
      )}
      <p className="mt-5 text-sm text-slate-600">
        {created
          ? <>Anyone with this link sees whether <strong>{certificate.title}</strong> is genuine, straight from the blockchain.</>
          : <>Or share the permanent verify link below. Anyone with it sees whether <strong>{certificate.title}</strong> is genuine,
              straight from the blockchain. They don't need an account.</>}
      </p>
      <div className="mt-5 flex flex-col items-center gap-5 sm:flex-row sm:items-start">
        <div className="flex h-[156px] w-[156px] shrink-0 items-center justify-center rounded-2xl bg-white p-2 ring-1 ring-slate-200">
          {qr ? <img src={qr} alt="QR code for the verify link" className="h-full w-full" data-testid="share-qr" />
              : <Icon name="qr" className="h-10 w-10 text-slate-300" />}
        </div>
        <div className="w-full min-w-0 space-y-3">
          <div className="flex items-center gap-2 rounded-xl bg-slate-50 p-2 ring-1 ring-slate-200">
            <input readOnly value={url} aria-label={created ? 'Share link' : 'Verify link'} onFocus={(e) => e.target.select()}
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

/** Name + how long: creates a link that only works for that long and can be turned off. */
function NamedLinkForm({ certificate, onCreated }: { certificate: MyCertificate; onCreated: (link: ShareLink) => void }) {
  const queryClient = useQueryClient()
  const [name, setName] = useState('')
  const [days, setDays] = useState('30')
  const create = useMutation({
    mutationFn: () => shareApi.create({ certificateId: certificate.id, name: name.trim(), expiresInDays: Number(days) }),
    onSuccess: (link) => {
      queryClient.invalidateQueries({ queryKey: ['student', 'share-links'] })
      onCreated(link)
    },
  })
  const submit = (e: FormEvent) => {
    e.preventDefault()
    if (name.trim()) create.mutate()
  }
  return (
    <form onSubmit={submit} className="rounded-2xl bg-navy-50/60 p-4 ring-1 ring-navy-100">
      <p className="text-sm font-semibold text-slate-900">Create a private link (recommended)</p>
      <p className="mt-0.5 text-xs text-slate-600">It stops working after the time you choose, you can turn it off any time,
        and you see how often it was opened.</p>
      <div className="mt-3 grid gap-3 sm:grid-cols-[1fr_9rem_auto] sm:items-end">
        <TextField label="Who is it for?" placeholder="e.g. Infosys HR" maxLength={100} value={name}
                   onChange={(e) => setName(e.target.value)} />
        <SelectField label="Works for" options={EXPIRY_OPTIONS} value={days} onChange={(e) => setDays(e.target.value)} />
        <Button type="submit" loading={create.isPending} disabled={!name.trim()}>Create link</Button>
      </div>
      {create.isError && <div className="mt-3"><Alert tone="error">{errorMessage(create.error)}</Alert></div>}
    </form>
  )
}
