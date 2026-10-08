import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { extractHash, type PdfVerificationResult, type VerificationResult } from '../../api/verify'
import { json } from '../../test/fixtures'
import { VerifyPage } from './VerifyPage'

const HASH = '0x' + 'ab'.repeat(32)
const fetchMock = vi.fn<typeof fetch>()

beforeEach(() => {
  vi.stubGlobal('fetch', fetchMock)
  fetchMock.mockReset()
})

afterEach(() => {
  vi.unstubAllGlobals()
})

function valid(overrides: Partial<VerificationResult> = {}): VerificationResult {
  return {
    certHash: HASH,
    status: 'VALID',
    message: 'This certificate is genuine and valid.',
    blockchainChecked: true,
    checkedAt: '2026-10-08T10:00:00Z',
    certificate: {
      certificateNumber: 'PIT-2026-0001', type: 'DEGREE', title: 'Bachelor of Technology', program: 'Computer Engineering',
      grade: null, cgpa: '8.50', awardedOn: '2026-06-30', studentName: 'Asha Patil', enrollmentNo: '2022CS001',
      institutionName: 'Pune Institute of Technology', institutionCode: 'PIT', expiresAt: null,
    },
    revocation: null,
    blockchain: {
      chainId: 11155111, contractAddress: '0x1EA8', merkleRoot: '0x' + 'cd'.repeat(32), issuerAddress: '0x' + '12'.repeat(20),
      txHash: '0x' + 'ef'.repeat(32), blockNumber: 6284117, anchoredAt: '2026-07-01T09:00:00Z',
      explorerTxUrl: 'https://sepolia.etherscan.io/tx/0xefef',
    },
    ...overrides,
  }
}

function renderAt(path: string) {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/verify" element={<VerifyPage />} />
          <Route path="/verify/:certHash" element={<VerifyPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

const requestedUrl = (call = 0) => String(fetchMock.mock.calls[call]![0])

describe('VerifyPage', () => {
  it('opening the QR link shows a genuine certificate with its blockchain record', async () => {
    fetchMock.mockResolvedValue(json(valid()))
    renderAt(`/verify/${HASH}`)

    expect(await screen.findByTestId('status-title')).toHaveTextContent('Genuine certificate')
    expect(screen.getByText('Asha Patil')).toBeInTheDocument()
    expect(screen.getByText('PIT-2026-0001')).toBeInTheDocument()
    expect(screen.getByText('Confirmed live on the blockchain')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Etherscan/ })).toHaveAttribute('href', 'https://sepolia.etherscan.io/tx/0xefef')
    expect(requestedUrl()).toContain(`/api/v1/public/verify/${HASH}`)
    expect(fetchMock.mock.calls[0]![1]!.headers).not.toHaveProperty('Authorization')
  })

  it('shows a revoked certificate with the reason', async () => {
    fetchMock.mockResolvedValue(json(valid({
      status: 'REVOKED', message: 'The college revoked this certificate.',
      revocation: { reason: 'ISSUED_IN_ERROR', revokedAt: '2026-08-01T10:00:00Z' },
    })))
    renderAt(`/verify/${HASH}`)

    expect(await screen.findByTestId('status-title')).toHaveTextContent('Revoked by the college')
    expect(screen.getByText(/Reason: Issued in error/)).toBeInTheDocument()
  })

  it('says when the blockchain could not be reached', async () => {
    fetchMock.mockResolvedValue(json(valid({ blockchainChecked: false })))
    renderAt(`/verify/${HASH}`)

    expect(await screen.findByText(/Blockchain not reachable right now/)).toBeInTheDocument()
  })

  it('shows an unknown hash as not found', async () => {
    fetchMock.mockResolvedValue(json({
      certHash: HASH, status: 'NOT_FOUND', message: 'No certificate with this hash was issued on CredChain.',
      blockchainChecked: true, checkedAt: '2026-10-08T10:00:00Z', certificate: null, revocation: null, blockchain: null,
    }))
    renderAt(`/verify/${HASH}`)

    expect(await screen.findByTestId('status-title')).toHaveTextContent('Not found on CredChain')
    expect(screen.getByText('No certificate with this hash was issued on CredChain.')).toBeInTheDocument()
  })

  it('shows the server message for a bad hash or too many checks', async () => {
    fetchMock.mockResolvedValue(json({ status: 429, code: 'RATE_LIMITED', detail: 'Too many checks. Try again in a minute.' }, 429))
    renderAt('/verify/hello')

    expect(await screen.findByRole('alert')).toHaveTextContent('Too many checks. Try again in a minute.')
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument()
  })

  it('a pasted verification link is checked by its hash', async () => {
    fetchMock.mockResolvedValue(json(valid()))
    renderAt('/verify')

    await userEvent.type(screen.getByLabelText('Certificate hash or verification link'), `http://localhost:5173/verify/${HASH}`)
    await userEvent.click(screen.getByRole('button', { name: /Verify/ }))

    expect(await screen.findByTestId('status-title')).toHaveTextContent('Genuine certificate')
    expect(requestedUrl()).toMatch(new RegExp(`/api/v1/public/verify/${HASH}$`))
  })
})

describe('PDF upload', () => {
  const pdf = () => new File(['%PDF-1.4 test'], 'certificate.pdf', { type: 'application/pdf' })

  it('refuses a file that is not a PDF without calling the server', async () => {
    renderAt('/verify?tab=pdf')

    await userEvent.upload(screen.getByLabelText('Certificate PDF'), new File(['hi'], 'photo.png', { type: 'image/png' }),
      { applyAccept: false })

    expect(screen.getByRole('alert')).toHaveTextContent('Choose a PDF file.')
    expect(screen.getByRole('button', { name: 'Verify PDF' })).toBeDisabled()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('an edited PDF is FAKE, with the failed step and the official record to compare', async () => {
    const result: PdfVerificationResult = {
      status: 'FAKE',
      message: 'This PDF was changed after it was issued. Do not accept it; compare it with the official record below.',
      fileSha256: 'f'.repeat(64),
      checkedAt: '2026-10-08T10:00:00Z',
      checks: [
        { name: 'CredChain data inside the PDF', result: 'PASSED', detail: null },
        { name: 'File unchanged since it was issued', result: 'FAILED', detail: 'This file is not the PDF CredChain issued.' },
      ],
      record: valid(),
    }
    fetchMock.mockResolvedValue(json(result))
    renderAt('/verify?tab=pdf')

    await userEvent.upload(screen.getByLabelText('Certificate PDF'), pdf())
    await userEvent.click(screen.getByRole('button', { name: 'Verify PDF' }))

    expect(await screen.findByTestId('status-title')).toHaveTextContent('Fake or edited: do not accept')
    const steps = screen.getByRole('list')
    expect(within(steps).getByText(/File unchanged since it was issued/)).toHaveTextContent('Failed')
    expect(screen.getByText(/Official record/)).toBeInTheDocument()

    const [url, init] = fetchMock.mock.calls[0]!
    expect(String(url)).toContain('/api/v1/public/verify/pdf')
    expect((init!.body as FormData).get('file')).toBeInstanceOf(File)
  })
})

describe('extractHash', () => {
  it('takes the hash out of a QR link and leaves a bare hash alone', () => {
    expect(extractHash(`  https://credchain.example/verify/${HASH}?x=1 `)).toBe(HASH)
    expect(extractHash(` ${HASH} `)).toBe(HASH)
  })
})
