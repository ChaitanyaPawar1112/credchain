import { useParams } from 'react-router'
import { Alert } from '../components/Alert'

/** Placeholder until step 6.2 builds the real verify page. The QR code on every PDF opens /verify/{hash}. */
export function VerifyPage() {
  const { certHash } = useParams()
  return (
    <div className="mx-auto max-w-3xl px-4 py-12">
      <h1 className="text-2xl font-bold text-slate-900">Verify a certificate</h1>
      {certHash && <p className="mt-2 break-all font-mono text-sm text-slate-600">{certHash}</p>}
      <div className="mt-6">
        <Alert tone="info">The verification result and PDF upload arrive in step 6.2.</Alert>
      </div>
    </div>
  )
}
