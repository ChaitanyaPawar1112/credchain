import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Institution } from '../../api/admin'
import { timeAgo } from '../../lib/format'
import { json } from '../../test/fixtures'
import { ApplyCollegePage } from '../ApplyCollegePage'
import { InstitutionDetailPage } from './InstitutionDetailPage'
import { InstitutionsPage } from './InstitutionsPage'
import { VerificationLogPage } from './VerificationLogPage'
import { describeBrowser } from './labels'

const fetchMock = vi.fn<typeof fetch>()
const ID = '11111111-1111-1111-1111-111111111111'

beforeEach(() => {
  vi.stubGlobal('fetch', fetchMock)
  fetchMock.mockReset()
})

afterEach(() => {
  vi.unstubAllGlobals()
})

function institution(overrides: Partial<Institution> = {}): Institution {
  return {
    id: ID, name: 'Sahyadri Institute of Technology', code: 'SIT', registrationNumber: 'AISHE-C-45678', type: 'COLLEGE',
    email: 'office@sit.edu.in', phone: null, website: null, addressLine: null, city: 'Pune', state: 'Maharashtra',
    country: 'IN', postalCode: null, contactPersonName: 'Dr. Anil Deshmukh', contactPersonEmail: 'registrar@sit.edu.in',
    walletAddress: null, status: 'PENDING', rejectionReason: null, reviewedAt: null, createdAt: '2026-10-08T03:00:00Z',
    ...overrides,
  }
}

const page = <T,>(content: T[]) => ({ content, page: 0, size: 20, totalElements: content.length, totalPages: 1, last: true })

/** Answers each request by "METHOD path" (query string ignored); unknown requests fail the test. */
function routes(table: Record<string, (init?: RequestInit) => Response>) {
  fetchMock.mockImplementation(async (input, init) => {
    const url = new URL(String(input))
    const key = `${init?.method ?? 'GET'} ${url.pathname}`
    const handler = table[key]
    if (!handler) throw new Error(`unexpected request ${key}`)
    return handler(init)
  })
}

const calls = (fragment: string) => fetchMock.mock.calls.map(([u]) => String(u)).filter((u) => u.includes(fragment))

function renderAt(path: string, element: React.ReactNode, pattern = path.split('?')[0]!) {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter initialEntries={[path]}>
        <Routes><Route path={pattern} element={element} /></Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('College applications', () => {
  it('lists pending applications first, and the filter asks the server for that status', async () => {
    routes({ 'GET /api/v1/admin/institutions': () => json(page([institution()])) })
    renderAt('/admin/institutions', <InstitutionsPage />)

    expect(await screen.findByText('Sahyadri Institute of Technology')).toBeInTheDocument()
    expect(calls('/admin/institutions?').some((u) => u.includes('status=PENDING'))).toBe(true)

    await userEvent.click(screen.getByRole('tab', { name: /Approved/ }))
    expect(calls('status=APPROVED').length).toBeGreaterThan(0)
  })

  it('approving shows the temporary password once', async () => {
    let current = institution()
    routes({
      ['GET /api/v1/admin/institutions/' + ID]: () => json(current),
      ['GET /api/v1/admin/institutions/' + ID + '/wallet']: () => json({ institutionId: ID, address: '0x' + '1'.repeat(40),
        custody: 'CUSTODIAL', status: 'PENDING_ACTIVATION', canIssue: false, balanceEth: null, fundingTxHash: null, grantTxHash: null,
        revokeTxHash: null, activatedAt: null, attempts: 0, nextAttemptAt: null, lastError: null, explorerUrl: null }),
      ['POST /api/v1/admin/institutions/' + ID + '/approve']: () => {
        current = institution({ status: 'APPROVED' })
        return json({ institution: current, adminAccount: { userId: 'u1', email: 'registrar@sit.edu.in',
          temporaryPassword: 'Tmp#Pass1234', note: 'share it' } })
      },
    })
    renderAt(`/admin/institutions/${ID}`, <InstitutionDetailPage />, '/admin/institutions/:id')

    await userEvent.click(await screen.findByRole('button', { name: /Approve/ }))
    const dialog = screen.getByRole('dialog')
    expect(within(dialog).getByText('registrar@sit.edu.in')).toBeInTheDocument()
    await userEvent.click(within(dialog).getByRole('button', { name: 'Approve' }))

    expect(await screen.findByTestId('temporary-password')).toHaveTextContent('Tmp#Pass1234')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Suspend' })).toBeInTheDocument()
    expect(await screen.findByText('Being activated')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: /I've shared it/ }))
    expect(screen.queryByTestId('temporary-password')).not.toBeInTheDocument()
  })

  it('rejecting needs a reason of at least 10 characters and sends it', async () => {
    routes({
      ['GET /api/v1/admin/institutions/' + ID]: () => json(institution()),
      ['POST /api/v1/admin/institutions/' + ID + '/reject']: () =>
        json(institution({ status: 'REJECTED', rejectionReason: 'AISHE number does not exist.' })),
    })
    renderAt(`/admin/institutions/${ID}`, <InstitutionDetailPage />, '/admin/institutions/:id')

    await userEvent.click(await screen.findByRole('button', { name: 'Reject' }))
    const confirm = screen.getByRole('button', { name: 'Reject application' })
    await userEvent.type(screen.getByLabelText(/Reason/), 'too short')
    expect(confirm).toBeDisabled()
    await userEvent.clear(screen.getByLabelText(/Reason/))
    await userEvent.type(screen.getByLabelText(/Reason/), 'AISHE number does not exist.')
    await userEvent.click(confirm)

    expect(await screen.findByText('The application was rejected.')).toBeInTheDocument()
    expect(screen.getByText(/AISHE number does not exist\./, { selector: 'div' })).toBeInTheDocument()
    const rejectCall = fetchMock.mock.calls.find(([u]) => String(u).endsWith('/reject'))!
    expect(JSON.parse(String(rejectCall[1]!.body))).toEqual({ reason: 'AISHE number does not exist.' })
  })
})

describe('Verification log', () => {
  it('shows totals per result and filters to FAKE checks', async () => {
    routes({
      'GET /api/v1/admin/verifications': () => json({
        totalChecks: 3, byResult: { VALID: 2, FAKE: 1, REVOKED: 0, EXPIRED: 0, NOT_FOUND: 0 },
        checks: page([{ id: 'v1', checkedAt: new Date().toISOString(), method: 'PDF', result: 'FAKE', blockchainChecked: false,
          certHash: null, certificateId: null, certificateNumber: null, studentName: null, institutionId: null,
          userAgent: 'Mozilla/5.0 (Windows NT 10.0) Chrome/129.0' }]),
      }),
    })
    renderAt('/admin/verifications', <VerificationLogPage />)

    expect(await screen.findByText(/1 fake or edited certificate was checked/)).toBeInTheDocument()
    expect(screen.getByText('Chrome on Windows')).toBeInTheDocument()
    expect(screen.getByText('Not a CredChain certificate')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('tab', { name: /Fake/ }))
    expect(calls('result=FAKE').length).toBeGreaterThan(0)
  })
})

describe('Apply as a college', () => {
  async function fillRequired() {
    const type = (label: string, value: string) => userEvent.type(screen.getByLabelText(label, { exact: true }), value)
    await type('Institution name', 'Sahyadri Institute of Technology')
    await type('Short code', 'SIT-AUR')
    await type('Registration / AISHE number', 'AISHE-C-45678')
    await type('Official email', 'office@sit.edu.in')
    await type('City', 'Pune')
    await type('State', 'Maharashtra')
    await type('Full name', 'Dr. Anil Deshmukh')
    await type('Email', 'registrar@sit.edu.in')
  }

  it('sends only the filled-in fields and shows the confirmation', async () => {
    routes({ 'POST /api/v1/institutions/applications': () => json(institution(), 201) })
    renderAt('/apply', <ApplyCollegePage />)

    const submit = screen.getByRole('button', { name: 'Submit application' })
    expect(submit).toBeDisabled()
    await fillRequired()
    await userEvent.click(submit)

    expect(await screen.findByText('Application received')).toBeInTheDocument()
    const body = JSON.parse(String(fetchMock.mock.calls[0]![1]!.body))
    expect(body).toMatchObject({ code: 'SIT-AUR', type: 'COLLEGE', contactPersonEmail: 'registrar@sit.edu.in' })
    expect(body).not.toHaveProperty('phone')
    expect(body).not.toHaveProperty('website')
    expect(fetchMock.mock.calls[0]![1]!.headers).not.toHaveProperty('Authorization')
  })

  it('shows the server message next to the wrong field', async () => {
    routes({ 'POST /api/v1/institutions/applications': () => json({ status: 400, code: 'VALIDATION_FAILED',
      detail: 'Some fields are invalid', errors: { code: 'must be 3-20 letters, digits or hyphens' } }, 400) })
    renderAt('/apply', <ApplyCollegePage />)

    await fillRequired()
    await userEvent.click(screen.getByRole('button', { name: 'Submit application' }))

    expect(await screen.findByText('must be 3-20 letters, digits or hyphens')).toBeInTheDocument()
  })
})

describe('helpers', () => {
  it('timeAgo reads naturally', () => {
    const now = Date.parse('2026-10-08T12:00:00Z')
    expect(timeAgo('2026-10-08T11:59:30Z', now)).toBe('Just now')
    expect(timeAgo('2026-10-08T11:57:00Z', now)).toBe('3 minutes ago')
    expect(timeAgo('2026-10-08T11:00:00Z', now)).toBe('1 hour ago')
    expect(timeAgo('2026-10-06T12:00:00Z', now)).toBe('2 days ago')
    expect(timeAgo(null, now)).toBe('Never')
  })

  it('describeBrowser turns a user agent into something readable', () => {
    expect(describeBrowser('Mozilla/5.0 (Linux; Android 14) AppleWebKit Chrome/129 Mobile Safari/537')).toBe('Chrome on Android')
    expect(describeBrowser('Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) Version/17 Safari/605')).toBe('Safari on Mac')
    expect(describeBrowser(null)).toBe('Unknown')
  })
})
