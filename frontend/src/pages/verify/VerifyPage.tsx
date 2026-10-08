import { useMutation, useQuery } from '@tanstack/react-query'
import { useRef, useState, type DragEvent, type FormEvent } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router'
import { MAX_PDF_BYTES, extractHash, verifyApi } from '../../api/verify'
import { Alert } from '../../components/Alert'
import { Button } from '../../components/Button'
import { Icon } from '../../components/Icon'
import { Spinner } from '../../components/Spinner'
import { errorMessage } from '../../components/errors'
import { CertificateRecord, ChainNote, PdfChecks, StatusBanner } from './ResultViews'

type Mode = 'hash' | 'pdf'

/**
 * Public certificate check, no login. /verify/{hash} (the QR code on every PDF) shows the result for that hash;
 * /verify shows the form: paste a hash or upload the PDF.
 */
export function VerifyPage() {
  const { certHash } = useParams()
  return (
    <div className="min-h-full bg-slate-50">
      <section className="bg-brand text-white">
        <div className="mx-auto max-w-4xl px-4 py-10 sm:py-14">
          <p className="inline-flex items-center gap-2 text-sm font-semibold uppercase tracking-wider text-gold-300">
            <Icon name="shield" className="h-4 w-4" /> Public verification
          </p>
          <h1 className="mt-2 text-3xl font-extrabold tracking-tight sm:text-4xl">Verify a certificate</h1>
          <p className="mt-2 max-w-2xl text-navy-100">
            Anyone can check a CredChain certificate. We look it up in CredChain and on the Ethereum blockchain.
          </p>
        </div>
      </section>
      <div className="mx-auto -mt-6 max-w-4xl px-4 pb-16">
        {certHash ? <HashResult certHash={certHash} /> : <VerifyForm />}
      </div>
    </div>
  )
}

function HashResult({ certHash }: { certHash: string }) {
  const query = useQuery({
    queryKey: ['verify', certHash],
    queryFn: ({ signal }) => verifyApi.byHash(certHash, signal),
    retry: false,
    staleTime: 0,
  })

  return (
    <div className="space-y-6">
      {query.isPending && (
        <div className="flex items-center gap-3 rounded-3xl bg-white p-8 text-navy-700 shadow-sm ring-1 ring-slate-200" role="status">
          <Spinner className="h-6 w-6" /> Checking CredChain and the blockchain…
        </div>
      )}
      {query.isError && (
        <div className="space-y-4 rounded-3xl bg-white p-6 shadow-sm ring-1 ring-slate-200">
          <Alert tone="error">{errorMessage(query.error)}</Alert>
          <div className="flex gap-3">
            <Button variant="secondary" onClick={() => query.refetch()}>Try again</Button>
            <Link to="/verify" className="inline-flex items-center rounded-xl px-4 py-2.5 text-sm font-semibold text-navy-700 hover:bg-navy-50">
              Check another certificate
            </Link>
          </div>
        </div>
      )}
      {query.data && (
        <>
          <StatusBanner status={query.data.status} message={query.data.message}>
            <ChainNote checked={query.data.blockchainChecked} checkedAt={query.data.checkedAt} />
          </StatusBanner>
          <CertificateRecord result={query.data} />
          <AgainLink />
        </>
      )}
    </div>
  )
}

function AgainLink() {
  return (
    <div className="flex justify-center">
      <Link to="/verify"
            className="inline-flex items-center gap-2 rounded-xl bg-white px-5 py-2.5 text-sm font-semibold text-navy-700 shadow-sm ring-1 ring-slate-200 hover:bg-navy-50">
        <Icon name="search" className="h-4 w-4" /> Check another certificate
      </Link>
    </div>
  )
}

function VerifyForm() {
  const [params] = useSearchParams()
  const [mode, setMode] = useState<Mode>(params.get('tab') === 'pdf' ? 'pdf' : 'hash')
  const tab = (value: Mode, label: string, icon: 'hash' | 'upload') => (
    <button type="button" role="tab" aria-selected={mode === value} onClick={() => setMode(value)}
            className={`flex flex-1 items-center justify-center gap-2 rounded-xl px-4 py-2.5 text-sm font-semibold transition ${
              mode === value ? 'bg-navy-700 text-white shadow' : 'text-slate-600 hover:bg-slate-100'}`}>
      <Icon name={icon} className="h-4 w-4" /> {label}
    </button>
  )

  return (
    <div className="space-y-6">
      <div className="rounded-3xl bg-white p-6 shadow-xl shadow-navy-900/5 ring-1 ring-slate-200 sm:p-8">
        <div role="tablist" className="flex gap-1 rounded-2xl bg-slate-100 p-1">
          {tab('hash', 'Certificate hash', 'hash')}
          {tab('pdf', 'Upload PDF', 'upload')}
        </div>
        <div className="mt-6">{mode === 'hash' ? <HashForm /> : <PdfUpload />}</div>
      </div>
      <Tips />
    </div>
  )
}

function HashForm() {
  const navigate = useNavigate()
  const [value, setValue] = useState('')
  const submit = (event: FormEvent) => {
    event.preventDefault()
    const hash = extractHash(value)
    if (hash) navigate(`/verify/${encodeURIComponent(hash)}`)
  }
  return (
    <form onSubmit={submit} className="space-y-3">
      <label htmlFor="cert-hash" className="block text-sm font-medium text-slate-700">
        Certificate hash or verification link
      </label>
      <div className="flex flex-col gap-3 sm:flex-row">
        <input id="cert-hash" value={value} onChange={(e) => setValue(e.target.value)} placeholder="0x… or https://…/verify/0x…"
               className="flex-1 rounded-xl border border-slate-300 bg-white px-4 py-3 font-mono text-sm outline-none transition
                          placeholder:text-slate-400 focus:border-navy-600 focus:ring-4 focus:ring-navy-100" />
        <Button type="submit" className="px-6 py-3" disabled={!value.trim()}>
          Verify <Icon name="arrowRight" className="h-4 w-4" />
        </Button>
      </div>
      <p className="text-xs text-slate-500">The hash is printed at the bottom of the certificate, and the QR code opens this check directly.</p>
    </form>
  )
}

function PdfUpload() {
  const inputRef = useRef<HTMLInputElement>(null)
  const [file, setFile] = useState<File | null>(null)
  const [problem, setProblem] = useState<string | null>(null)
  const [dragging, setDragging] = useState(false)
  const upload = useMutation({ mutationFn: verifyApi.byPdf })

  const choose = (picked: File | undefined) => {
    upload.reset()
    setProblem(null)
    if (!picked) return
    const isPdf = picked.type === 'application/pdf' || picked.name.toLowerCase().endsWith('.pdf')
    if (!isPdf) {
      setFile(null)
      setProblem('Choose a PDF file.')
    } else if (picked.size > MAX_PDF_BYTES) {
      setFile(null)
      setProblem('This file is larger than 2 MB. CredChain certificates are much smaller.')
    } else {
      setFile(picked)
    }
  }

  const onDrop = (event: DragEvent) => {
    event.preventDefault()
    setDragging(false)
    choose(event.dataTransfer.files[0])
  }

  const startOver = () => {
    upload.reset()
    setFile(null)
    if (inputRef.current) inputRef.current.value = ''
  }

  if (upload.data) {
    const result = upload.data
    return (
      <div className="space-y-6">
        <StatusBanner status={result.status} message={result.message}>
          {result.record && <ChainNote checked={result.record.blockchainChecked} checkedAt={result.checkedAt} />}
        </StatusBanner>
        <PdfChecks result={result} />
        {result.record && (
          <CertificateRecord result={result.record}
                             heading={result.status === 'FAKE' ? 'Official record (compare with the PDF you have)' : 'Certificate details'} />
        )}
        <div className="flex justify-center">
          <Button variant="secondary" onClick={startOver}><Icon name="upload" className="h-4 w-4" /> Check another PDF</Button>
        </div>
      </div>
    )
  }

  return (
    <div className="space-y-4">
      <label
        onDragOver={(e) => { e.preventDefault(); setDragging(true) }}
        onDragLeave={() => setDragging(false)}
        onDrop={onDrop}
        className={`flex cursor-pointer flex-col items-center justify-center rounded-2xl border-2 border-dashed px-6 py-12 text-center transition ${
          dragging ? 'border-navy-600 bg-navy-50' : 'border-slate-300 hover:border-navy-400 hover:bg-slate-50'}`}
      >
        <span className="flex h-14 w-14 items-center justify-center rounded-2xl bg-navy-50 text-navy-700">
          <Icon name={file ? 'file' : 'upload'} className="h-7 w-7" />
        </span>
        {file ? (
          <>
            <span className="mt-4 max-w-full truncate font-semibold text-slate-900">{file.name}</span>
            <span className="text-sm text-slate-500">{(file.size / 1024).toFixed(0)} KB · click to choose a different file</span>
          </>
        ) : (
          <>
            <span className="mt-4 font-semibold text-slate-900">Drop the certificate PDF here, or click to choose it</span>
            <span className="text-sm text-slate-500">PDF only, up to 2 MB</span>
          </>
        )}
        <input ref={inputRef} type="file" accept="application/pdf,.pdf" className="sr-only" aria-label="Certificate PDF"
               onChange={(e) => choose(e.target.files?.[0])} />
      </label>

      {problem && <Alert tone="error">{problem}</Alert>}
      {upload.isError && <Alert tone="error">{errorMessage(upload.error)}</Alert>}

      <Button className="w-full py-3" disabled={!file} loading={upload.isPending} onClick={() => file && upload.mutate(file)}>
        {upload.isPending ? 'Checking the PDF…' : 'Verify PDF'}
      </Button>
      <p className="text-xs text-slate-500">The file is only checked, not stored.</p>
    </div>
  )
}

function Tips() {
  const tips = [
    { icon: 'qr' as const, title: 'Scan the QR code', text: 'Every CredChain PDF has one. It opens the result for that certificate.' },
    { icon: 'hash' as const, title: 'Paste the hash', text: 'The long code starting with 0x at the bottom of the certificate.' },
    { icon: 'upload' as const, title: 'Upload the PDF', text: 'Catches PDFs that were edited after the college issued them.' },
  ]
  return (
    <div className="grid gap-4 sm:grid-cols-3">
      {tips.map((t) => (
        <div key={t.title} className="rounded-2xl bg-white p-5 ring-1 ring-slate-200">
          <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-gold-100 text-gold-600">
            <Icon name={t.icon} className="h-5 w-5" />
          </span>
          <p className="mt-3 font-semibold text-slate-900">{t.title}</p>
          <p className="mt-1 text-sm text-slate-600">{t.text}</p>
        </div>
      ))}
    </div>
  )
}
