import { useMutation } from '@tanstack/react-query'
import { institutionApi, saveFile, type Certificate } from '../../api/institution'
import { Icon } from '../../components/Icon'
import { errorMessage } from '../../components/errors'

/** Downloads the issued certificate PDF (with its QR code) as "<certificate number>.pdf". */
export function DownloadPdfButton({ certificate, download = institutionApi.certificatePdf }: {
  certificate: Pick<Certificate, 'id' | 'certificateNumber'>
  download?: (id: string) => Promise<Blob>
}) {
  const pdf = useMutation({
    mutationFn: () => download(certificate.id),
    onSuccess: (blob) => saveFile(blob, `${certificate.certificateNumber}.pdf`),
  })
  return (
    <button type="button" onClick={() => pdf.mutate()} disabled={pdf.isPending}
            title={pdf.isError ? errorMessage(pdf.error) : 'Download the PDF'}
            className={`inline-flex items-center gap-1 rounded-lg px-2.5 py-1.5 text-sm font-semibold transition disabled:opacity-50 ${
              pdf.isError ? 'text-red-600 hover:bg-red-50' : 'text-navy-700 hover:bg-navy-50'}`}>
      <Icon name="file" className="h-4 w-4" /> {pdf.isPending ? 'Downloading…' : pdf.isError ? 'Try again' : 'PDF'}
    </button>
  )
}
