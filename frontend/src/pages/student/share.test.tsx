import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ShareLink, SharedCertificate } from '../../api/share'
import type { MyCertificate } from '../../api/student'
import { json } from '../../test/fixtures'
import { SharedCertificatePage } from '../SharedCertificatePage'
import { ShareLinksPage } from './ShareLinksPage'
import { ShareModal } from './ShareModal'

const fetchMock = vi.fn<typeof fetch>()
const TOKEN = 'a'.repeat(43)

beforeEach(() => {
  vi.stubGlobal('fetch', fetchMock)
  fetchMock.mockReset()
})

afterEach(() => {
  vi.unstubAllGlobals()
})

const page = <T,>(content: T[]) => ({ content, page: 0, size: 20, totalElements: content.length, totalPages: 1, last: true })

/** Answers by "METHOD path"; records each request with its body. */
function routes(table: Record<string, (init?: RequestInit) => Response>) {
  const requests: { key: string; body: unknown }[] = []
  fetchMock.mockImplementation(async (input, init) => {
    const key = `${init?.method ?? 'GET'} ${new URL(String(input)).pathname}`
    requests.push({ key, body: init?.body ? JSON.parse(String(init.body)) : undefined })
    const handler = table[key]
    if (!handler) throw new Error(`unexpected request ${key}`)
    return handler(init)
  })
  return requests
}

function renderAt(path: string, pattern: string, element: React.ReactNode) {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter initialEntries={[path]}><Routes><Route path={pattern} element={element} /></Routes></MemoryRouter>
    </QueryClientProvider>,
  )
}

const CERTIFICATE: MyCertificate = {
  id: 'c1', certificateNumber: 'SIT-2026-000001', institutionName: 'Sahyadri Institute of Technology', type: 'DEGREE',
  title: 'Bachelor of Technology', program: null, grade: null, cgpa: 8.5, awardedOn: '2026-06-30', studentName: 'Asha Patil',
  enrollmentNo: '2022CS001', certHash: '0x' + 'b'.repeat(64), status: 'ISSUED', revocationReason: null, revokedAt: null,
  pdfAvailable: true, verificationUrl: 'http://localhost:5173/verify/0xbbbb',
}

function link(overrides: Partial<ShareLink> = {}): ShareLink {
  return {
    id: 'l1', name: 'Infosys HR', url: `http://localhost:5173/s/${TOKEN}`, certificateId: 'c1',
    certificateNumber: 'SIT-2026-000001', certificateTitle: 'Bachelor of Technology', status: 'ACTIVE',
    createdAt: '2026-10-20T10:00:00Z', expiresAt: '2026-11-19T10:00:00Z', revokedAt: null, viewCount: 3,
    lastViewedAt: new Date().toISOString(), ...overrides,
  }
}

const SHARED: SharedCertificate = {
  linkExpiresAt: '2026-11-19T10:00:00Z',
  pdfAvailable: true,
  verification: {
    certHash: '0x' + 'b'.repeat(64), status: 'VALID', message: 'This certificate is genuine and valid.',
    blockchainChecked: true, checkedAt: '2026-10-28T10:00:00Z', revocation: null, blockchain: null,
    certificate: {
      certificateNumber: 'SIT-2026-000001', type: 'DEGREE', title: 'Bachelor of Technology', program: null, grade: null,
      cgpa: '8.50', awardedOn: '2026-06-30', studentName: 'Asha Patil', enrollmentNo: '2022CS001',
      institutionName: 'Sahyadri Institute of Technology', institutionCode: 'SIT', expiresAt: null,
    },
  },
}

describe('Share links', () => {
  it('the share window creates a named link and then shows that link instead of the verify link', async () => {
    const requests = routes({ 'POST /api/v1/me/share-links': () => json(link({ viewCount: 0, lastViewedAt: null }), 201) })
    renderAt('/', '/', <ShareModal certificate={CERTIFICATE} onClose={() => undefined} />)

    const dialog = screen.getByRole('dialog')
    expect(within(dialog).getByLabelText('Verify link')).toHaveValue(CERTIFICATE.verificationUrl)
    expect(within(dialog).getByRole('button', { name: 'Create link' })).toBeDisabled()

    await userEvent.type(within(dialog).getByLabelText('Who is it for?'), '  Infosys HR ')
    await userEvent.selectOptions(within(dialog).getByLabelText('Works for'), '90')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create link' }))

    expect(await within(dialog).findByLabelText('Share link')).toHaveValue(`http://localhost:5173/s/${TOKEN}`)
    expect(within(dialog).getByRole('status')).toHaveTextContent('Your link for Infosys HR works until')
    expect(requests[0]).toEqual({ key: 'POST /api/v1/me/share-links',
      body: { certificateId: 'c1', name: 'Infosys HR', expiresInDays: 90 } })
  })

  it('the links page shows status and views, and turns a link off', async () => {
    let revoked = false
    const requests = routes({
      'GET /api/v1/me/share-links': () => json(page([
        link({ status: revoked ? 'REVOKED' : 'ACTIVE' }),
        link({ id: 'l2', name: 'Old employer', status: 'EXPIRED', viewCount: 1 }),
      ])),
      'POST /api/v1/me/share-links/l1/revoke': () => {
        revoked = true
        return json(link({ status: 'REVOKED' }))
      },
    })
    renderAt('/student/share-links', '/student/share-links', <ShareLinksPage />)

    const row = (await screen.findByText('Infosys HR')).closest('tr')!
    expect(row).toHaveTextContent('Active')
    expect(row).toHaveTextContent('3 times')
    expect(screen.getByText('Old employer').closest('tr')).toHaveTextContent('Expired')
    expect(screen.getByText('Old employer').closest('tr')).toHaveTextContent('1 time')

    await userEvent.click(within(row).getByRole('button', { name: 'Turn off' }))
    expect(await within(row).findByText('Turned off')).toBeInTheDocument()
    expect(requests.map((r) => r.key)).toContain('POST /api/v1/me/share-links/l1/revoke')
    expect(within(row).queryByRole('button', { name: 'Copy' })).not.toBeInTheDocument()
  })

  it('someone opening a link sees the live result and can download the PDF', async () => {
    routes({ [`GET /api/v1/public/share/${TOKEN}`]: () => json(SHARED) })
    renderAt(`/s/${TOKEN}`, '/s/:token', <SharedCertificatePage />)

    expect(await screen.findByRole('heading', { name: "Asha Patil's certificate" })).toBeInTheDocument()
    expect(screen.getByText('This certificate is genuine and valid.')).toBeInTheDocument()
    expect(screen.getByText(/works until/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Download PDF/ })).toBeInTheDocument()
    expect(screen.queryByText('Infosys HR')).not.toBeInTheDocument()
  })

  it('an expired or turned-off link explains what to do', async () => {
    routes({ [`GET /api/v1/public/share/${TOKEN}`]: () => json({ status: 404, code: 'SHARE_LINK_UNAVAILABLE',
      detail: 'This link does not exist, has expired, or was turned off by the student' }, 404) })
    renderAt(`/s/${TOKEN}`, '/s/:token', <SharedCertificatePage />)

    expect(await screen.findByRole('alert')).toHaveTextContent('has expired, or was turned off')
    expect(screen.getByRole('link', { name: /Verify a certificate/ })).toHaveAttribute('href', '/verify')
  })
})
