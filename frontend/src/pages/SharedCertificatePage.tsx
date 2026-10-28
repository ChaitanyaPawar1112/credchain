import { useMutation, useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router'
import { saveFile } from '../api/institution'
import { shareApi } from '../api/share'
import { Alert } from '../components/Alert'
import { Button } from '../components/Button'
import { Icon } from '../components/Icon'
import { Spinner } from '../components/Spinner'
import { errorMessage } from '../components/errors'
import { formatDate } from '../lib/format'
import { CertificateRecord, ChainNote, StatusBanner } from './verify/ResultViews'

/** /s/{token}: a certificate a student shared with you. Shows the live check result and offers the PDF. */
export function SharedCertificatePage() {
  const { token = '' } = useParams()
  const query = useQuery({
    queryKey: ['share', token],
    queryFn: ({ signal }) => shareApi.open(token, signal),
    retry: false,
    staleTime: Infinity,   // each load counts as a view: don't re-open the link on window focus
    refetchOnWindowFocus: false,
  })
  const shared = query.data
  const result = shared?.verification
  const pdf = useMutation({
    mutationFn: () => shareApi.pdf(token),
    onSuccess: (blob) => saveFile(blob, `${result?.certificate?.certificateNumber ?? 'certificate'}.pdf`),
  })

  return (
    <div className="min-h-full bg-slate-50">
      <section className="bg-brand text-white">
        <div className="mx-auto max-w-4xl px-4 py-10 sm:py-14">
          <p className="inline-flex items-center gap-2 text-sm font-semibold uppercase tracking-wider text-gold-300">
            <Icon name="shield" className="h-4 w-4" /> Shared certificate
          </p>
          <h1 className="mt-2 text-3xl font-extrabold tracking-tight sm:text-4xl">
            {result?.certificate ? `${result.certificate.studentName}'s certificate` : 'A certificate shared with you'}
          </h1>
          <p className="mt-2 max-w-2xl text-navy-100">
            Checked live in CredChain and on the Ethereum blockchain when you opened this page.
          </p>
        </div>
      </section>

      <div className="mx-auto -mt-6 max-w-4xl space-y-6 px-4 pb-16">
        {query.isPending && (
          <div className="flex items-center gap-3 rounded-3xl bg-white p-8 text-navy-700 shadow-sm ring-1 ring-slate-200" role="status">
            <Spinner className="h-6 w-6" /> Checking CredChain and the blockchain…
          </div>
        )}
        {query.isError && (
          <div className="space-y-4 rounded-3xl bg-white p-6 shadow-sm ring-1 ring-slate-200">
            <Alert tone="error">{errorMessage(query.error)}</Alert>
            <p className="text-sm text-slate-600">
              Ask the student for a new link, or check the certificate yourself with its PDF or the QR code on it.
            </p>
            <Link to="/verify" className="inline-flex items-center gap-2 text-sm font-semibold text-navy-700 hover:underline">
              <Icon name="search" className="h-4 w-4" /> Verify a certificate
            </Link>
          </div>
        )}
        {shared && result && (
          <>
            <StatusBanner status={result.status} message={result.message}>
              <ChainNote checked={result.blockchainChecked} checkedAt={result.checkedAt} />
            </StatusBanner>
            <CertificateRecord result={result} />
            <div className="flex flex-col items-center justify-between gap-3 rounded-2xl bg-white p-5 shadow-sm ring-1 ring-slate-200 sm:flex-row">
              <p className="text-sm text-slate-600">
                This link was shared by the student and works until <strong>{formatDate(shared.linkExpiresAt)}</strong>.
              </p>
              {shared.pdfAvailable && (
                <Button variant="secondary" loading={pdf.isPending} onClick={() => pdf.mutate()}>
                  <Icon name="file" className="h-4 w-4" /> Download PDF
                </Button>
              )}
            </div>
            {pdf.isError && <Alert tone="error">{errorMessage(pdf.error)}</Alert>}
          </>
        )}
      </div>
    </div>
  )
}
