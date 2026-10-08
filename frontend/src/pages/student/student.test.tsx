import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { MyCertificate, MyStudentProfile } from '../../api/student'
import { json } from '../../test/fixtures'
import { MyCertificatesPage } from './MyCertificatesPage'

const fetchMock = vi.fn<typeof fetch>()

beforeEach(() => {
  vi.stubGlobal('fetch', fetchMock)
  fetchMock.mockReset()
})

afterEach(() => {
  vi.unstubAllGlobals()
})

const page = <T,>(content: T[]) => ({ content, page: 0, size: 20, totalElements: content.length, totalPages: 1, last: true })

const PROFILE: MyStudentProfile = {
  record: { id: 's1', enrollmentNo: '2022CS001', fullName: 'Asha Patil', email: null, dateOfBirth: null, program: 'B.Tech Computer',
    department: null, admissionYear: 2022, graduationYear: 2026, status: 'ACTIVE', accountLinked: true, linkedAt: null, createdAt: '' },
  institutionId: 'i1', institutionName: 'Sahyadri Institute of Technology', institutionCode: 'SIT',
}

function certificate(overrides: Partial<MyCertificate> = {}): MyCertificate {
  const certHash = '0x' + 'b'.repeat(64)
  return {
    id: 'c1', certificateNumber: 'SIT-2026-000001', institutionName: 'Sahyadri Institute of Technology', type: 'DEGREE',
    title: 'Bachelor of Technology', program: 'Computer Engineering', grade: 'First Class', cgpa: 8.5, awardedOn: '2026-06-30',
    studentName: 'Asha Patil', enrollmentNo: '2022CS001', certHash, status: 'ISSUED', revocationReason: null, revokedAt: null,
    pdfAvailable: true, verificationUrl: `http://localhost:5173/verify/${certHash}`, ...overrides,
  }
}

const notLinked = () => json({ status: 404, code: 'RESOURCE_NOT_FOUND',
  detail: 'Your account is not linked to a student record yet. Ask your institution for a claim code.' }, 404)

function routes(table: Record<string, (init?: RequestInit) => Response>) {
  fetchMock.mockImplementation(async (input, init) => {
    const key = `${init?.method ?? 'GET'} ${new URL(String(input)).pathname}`
    const handler = table[key]
    if (!handler) throw new Error(`unexpected request ${key}`)
    return handler(init)
  })
}

function renderPage() {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter><MyCertificatesPage /></MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('My certificates', () => {
  it('asks a new student to connect their record, then shows their certificates', async () => {
    let linked = false
    routes({
      'GET /api/v1/me/student-profile': () => (linked ? json(PROFILE) : notLinked()),
      'POST /api/v1/me/student-profile/link': () => { linked = true; return json(PROFILE) },
      'GET /api/v1/me/certificates': () => json(page([certificate()])),
    })
    renderPage()

    const connect = await screen.findByRole('button', { name: 'Connect my record' })
    expect(connect).toBeDisabled()
    await userEvent.type(screen.getByLabelText('College code'), 'sit')
    await userEvent.type(screen.getByLabelText('Enrollment number'), ' 2022CS001 ')
    await userEvent.type(screen.getByLabelText('Claim code'), 'k7p2-m9qx')
    await userEvent.click(connect)

    expect(await screen.findByText('Bachelor of Technology, Computer Engineering')).toBeInTheDocument()
    const link = fetchMock.mock.calls.find(([u]) => String(u).endsWith('/link'))!
    expect(JSON.parse(String(link[1]!.body))).toEqual({ institutionCode: 'SIT', enrollmentNo: '2022CS001', claimCode: 'K7P2-M9QX' })
  })

  it('shows the message for a wrong or expired claim code', async () => {
    routes({
      'GET /api/v1/me/student-profile': notLinked,
      'POST /api/v1/me/student-profile/link': () => json({ status: 400, code: 'INVALID_CLAIM_CODE',
        detail: 'Claim code is invalid or has expired' }, 400),
    })
    renderPage()

    await userEvent.type(await screen.findByLabelText('College code'), 'SIT')
    await userEvent.type(screen.getByLabelText('Enrollment number'), '2022CS001')
    await userEvent.type(screen.getByLabelText('Claim code'), 'WRONG')
    await userEvent.click(screen.getByRole('button', { name: 'Connect my record' }))

    expect(await screen.findByText('Claim code is invalid or has expired')).toBeInTheDocument()
  })

  it('shares the verify link with a QR code', async () => {
    routes({
      'GET /api/v1/me/student-profile': () => json(PROFILE),
      'GET /api/v1/me/certificates': () => json(page([certificate()])),
    })
    renderPage()

    await userEvent.click(await screen.findByRole('button', { name: /Share/ }))
    const dialog = screen.getByRole('dialog')
    expect(within(dialog).getByLabelText('Verify link')).toHaveValue(certificate().verificationUrl)
    expect(await within(dialog).findByTestId('share-qr')).toHaveAttribute('src', expect.stringMatching(/^data:image\/png/))
    expect(within(dialog).getByRole('link', { name: 'WhatsApp' }).getAttribute('href')).toContain(encodeURIComponent(certificate().verificationUrl))
  })

  it('a revoked certificate says so and cannot be shared', async () => {
    routes({
      'GET /api/v1/me/student-profile': () => json(PROFILE),
      'GET /api/v1/me/certificates': () => json(page([certificate({ status: 'REVOKED', revocationReason: 'ISSUED_IN_ERROR' })])),
    })
    renderPage()

    expect(await screen.findByText(/Your college revoked this certificate \(issued in error\)/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Share/ })).not.toBeInTheDocument()
  })
})
