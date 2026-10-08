import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { InstitutionWallet } from '../../api/admin'
import type { Batch, Certificate, Student } from '../../api/institution'
import { json } from '../../test/fixtures'
import { VerificationLogPage } from '../admin/VerificationLogPage'
import { BatchDetailPage } from './BatchDetailPage'
import { CertificatesPage } from './CertificatesPage'
import { StudentsPage } from './StudentsPage'

const fetchMock = vi.fn<typeof fetch>()
const BATCH = '22222222-2222-2222-2222-222222222222'
const BASE = '/api/v1/institution'

beforeEach(() => {
  vi.stubGlobal('fetch', fetchMock)
  fetchMock.mockReset()
})

afterEach(() => {
  vi.unstubAllGlobals()
})

const page = <T,>(content: T[]) => ({ content, page: 0, size: 20, totalElements: content.length, totalPages: 1, last: true })

function student(overrides: Partial<Student> = {}): Student {
  return {
    id: 's1', enrollmentNo: '2022CS001', fullName: 'Riya Kulkarni', email: null, dateOfBirth: null, program: 'B.Tech Computer',
    department: null, admissionYear: 2022, graduationYear: 2026, status: 'ACTIVE', accountLinked: false, linkedAt: null,
    createdAt: '2026-10-01T00:00:00Z', ...overrides,
  }
}

function batch(overrides: Partial<Batch> = {}): Batch {
  return {
    id: BATCH, title: 'Convocation 2026', status: 'DRAFT', certificateCount: 1, expiresAt: null, merkleRoot: null, issuerAddress: null,
    txHash: null, blockNumber: null, queuedAt: null, anchoredAt: null, attempts: 0, lastError: null, explorerTxUrl: null, ...overrides,
  }
}

function certificate(overrides: Partial<Certificate> = {}): Certificate {
  return {
    id: 'c1', batchId: BATCH, studentId: 's1', certificateNumber: 'SIT-2026-000001', type: 'DEGREE', title: 'Bachelor of Technology',
    program: 'Computer Engineering', grade: null, cgpa: 8.5, awardedOn: '2026-06-30', studentName: 'Riya Kulkarni',
    enrollmentNo: '2022CS001', certHash: null, status: 'DRAFT', revocationReason: null, revokedAt: null, pdfAvailable: false, ...overrides,
  }
}

const wallet = (canIssue: boolean): InstitutionWallet => ({
  institutionId: 'i1', address: '0x' + '1'.repeat(40), custody: 'CUSTODIAL', status: canIssue ? 'ACTIVE' : 'PENDING_ACTIVATION', canIssue,
  balanceEth: null, fundingTxHash: null, grantTxHash: null, revokeTxHash: null, activatedAt: null, attempts: 0, nextAttemptAt: null,
  lastError: null, explorerUrl: null,
})

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

const bodyOf = (fragment: string) => {
  const call = fetchMock.mock.calls.find(([u, init]) => String(u).includes(fragment) && init?.method === 'POST')!
  return JSON.parse(String(call[1]!.body))
}

function renderAt(path: string, element: React.ReactNode, pattern = path) {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter initialEntries={[path]}>
        <Routes><Route path={pattern} element={element} /></Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('Students', () => {
  it('adds a student and leaves out the optional fields that are empty', async () => {
    routes({
      [`GET ${BASE}/students`]: () => json(page([])),
      [`POST ${BASE}/students`]: () => json(student(), 201),
    })
    renderAt('/institution/students', <StudentsPage />)

    await userEvent.click(await screen.findByRole('button', { name: 'Add student' }))
    const dialog = screen.getByRole('dialog')
    await userEvent.type(within(dialog).getByLabelText('Enrollment number'), '2022CS001')
    await userEvent.type(within(dialog).getByLabelText('Full name'), 'Riya Kulkarni')
    await userEvent.type(within(dialog).getByLabelText('Programme'), 'B.Tech Computer')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Add student' }))

    expect(await screen.findByText('Riya Kulkarni (2022CS001) was added.')).toBeInTheDocument()
    const body = bodyOf(`${BASE}/students`)
    expect(body).toMatchObject({ enrollmentNo: '2022CS001', fullName: 'Riya Kulkarni', program: 'B.Tech Computer' })
    expect(body).not.toHaveProperty('email')
    expect(body).not.toHaveProperty('graduationYear')
  })

  it('checks a CSV first, then imports only after confirmation', async () => {
    routes({
      [`GET ${BASE}/students`]: () => json(page([])),
      [`POST ${BASE}/students/import`]: () => json({ totalRows: 3, valid: 2, imported: 0, failed: 1, dryRun: true,
        errors: [{ row: 4, enrollmentNo: 'X1', messages: ['program is required'] }] }),
    })
    renderAt('/institution/students', <StudentsPage />)

    await userEvent.click(await screen.findByRole('button', { name: /Import CSV/ }))
    await userEvent.upload(screen.getByLabelText('Students CSV file'), new File(['a,b'], 'students.csv', { type: 'text/csv' }))

    expect(await screen.findByText(/program is required/)).toBeInTheDocument()
    expect(String(fetchMock.mock.calls.at(-1)![0])).toContain('dryRun=true')
    expect(screen.getByRole('button', { name: 'Import 2 students' })).toBeEnabled()
  })

  it('shows the claim code with the college code and enrollment number', async () => {
    routes({
      [`GET ${BASE}/students`]: () => json(page([student()])),
      [`GET ${BASE}/profile`]: () => json({ code: 'SIT' }),
      [`POST ${BASE}/students/s1/claim-code`]: () => json({ studentId: 's1', enrollmentNo: '2022CS001', claimCode: 'K7Q2M9XA',
        expiresAt: '2026-10-15T00:00:00Z', note: '' }),
    })
    renderAt('/institution/students', <StudentsPage />)

    await userEvent.click(await screen.findByRole('button', { name: /Get claim code/ }))
    await userEvent.click(screen.getByRole('button', { name: 'Generate code' }))

    expect(await screen.findByTestId('claim-code')).toHaveTextContent('K7Q2M9XA')
    expect(await screen.findByText('SIT')).toBeInTheDocument()
  })
})

describe('Batch', () => {
  it('adds certificates for the ticked students with their CGPA', async () => {
    routes({
      [`GET ${BASE}/certificate-batches/${BATCH}`]: () => json({ batch: batch({ certificateCount: 0 }), certificates: [] }),
      [`GET ${BASE}/wallet`]: () => json(wallet(true)),
      [`GET ${BASE}/students`]: () => json(page([student(), student({ id: 's2', fullName: 'Omkar Jadhav', enrollmentNo: '2022CS002' })])),
      [`POST ${BASE}/certificate-batches/${BATCH}/certificates/bulk`]: () => json([certificate()], 201),
    })
    renderAt(`/institution/batches/${BATCH}`, <BatchDetailPage />, '/institution/batches/:id')

    await userEvent.click(await screen.findByRole('button', { name: 'Add certificates' }))
    const dialog = screen.getByRole('dialog')
    await userEvent.type(within(dialog).getByLabelText('Certificate title'), 'Bachelor of Technology')
    await userEvent.click(await within(dialog).findByRole('checkbox', { name: /Riya Kulkarni/ }))
    await userEvent.type(within(dialog).getByLabelText('CGPA for Riya Kulkarni'), '11')
    expect(within(dialog).getByText(/CGPA must be a number from 0 to 10/)).toBeInTheDocument()
    await userEvent.clear(within(dialog).getByLabelText('CGPA for Riya Kulkarni'))
    await userEvent.type(within(dialog).getByLabelText('CGPA for Riya Kulkarni'), '8.5')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Add 1 certificate' }))

    expect(await screen.findByText('1 certificate added.')).toBeInTheDocument()
    expect(bodyOf('/bulk')).toMatchObject({ type: 'DEGREE', title: 'Bachelor of Technology', items: [{ studentId: 's1', cgpa: 8.5 }] })
  })

  it('does not let a college issue before its wallet is ready', async () => {
    routes({
      [`GET ${BASE}/certificate-batches/${BATCH}`]: () => json({ batch: batch(), certificates: [certificate()] }),
      [`GET ${BASE}/wallet`]: () => json(wallet(false)),
    })
    renderAt(`/institution/batches/${BATCH}`, <BatchDetailPage />, '/institution/batches/:id')

    expect(await screen.findByText(/wallet is not ready yet, so this batch can't be issued/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /Issue on blockchain/ }))
    expect(within(screen.getByRole('dialog')).getByRole('button', { name: 'Issue 1 certificate' })).toBeDisabled()
  })

  it('shows the blockchain record once issued and hides the draft actions', async () => {
    routes({
      [`GET ${BASE}/certificate-batches/${BATCH}`]: () => json({
        batch: batch({ status: 'ANCHORED', txHash: '0x' + 'a'.repeat(64), blockNumber: 6284117, anchoredAt: '2026-10-08T05:00:00Z',
          explorerTxUrl: 'https://sepolia.etherscan.io/tx/0xaaa' }),
        certificates: [certificate({ status: 'ISSUED', certHash: '0x' + 'b'.repeat(64) })],
      }),
      [`GET ${BASE}/wallet`]: () => json(wallet(true)),
    })
    renderAt(`/institution/batches/${BATCH}`, <BatchDetailPage />, '/institution/batches/:id')

    expect(await screen.findByText('#6284117')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /View on Etherscan/ })).toHaveAttribute('href', 'https://sepolia.etherscan.io/tx/0xaaa')
    expect(screen.queryByRole('button', { name: 'Add certificates' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Remove' })).not.toBeInTheDocument()
  })
})

describe('Certificates', () => {
  it('revokes an issued certificate with the reason and note', async () => {
    const issued = certificate({ status: 'ISSUED', certHash: '0x' + 'b'.repeat(64) })
    routes({
      [`GET ${BASE}/certificates`]: () => json(page([issued])),
      [`POST ${BASE}/certificates/c1/revoke`]: () => json({ ...issued, status: 'REVOCATION_PENDING' }),
    })
    renderAt('/institution/certificates', <CertificatesPage />)

    await userEvent.click(await screen.findByRole('button', { name: 'Revoke' }))
    const dialog = screen.getByRole('dialog')
    await userEvent.selectOptions(within(dialog).getByLabelText('Reason'), 'SUPERSEDED')
    await userEvent.type(within(dialog).getByLabelText(/Internal note/), 'Name spelling corrected')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Revoke certificate' }))

    expect(await screen.findByText(/SIT-2026-000001 is being revoked/)).toBeInTheDocument()
    expect(bodyOf('/revoke')).toEqual({ reason: 'SUPERSEDED', note: 'Name spelling corrected' })
  })

  it('the status filter asks the server for that status', async () => {
    routes({ [`GET ${BASE}/certificates`]: () => json(page([])) })
    renderAt('/institution/certificates', <CertificatesPage />)

    await userEvent.click(await screen.findByRole('tab', { name: 'Revoked' }))
    expect(fetchMock.mock.calls.some(([u]) => String(u).includes('status=REVOKED'))).toBe(true)
  })
})

describe('Verification activity', () => {
  it("reads the college's own checks", async () => {
    routes({
      [`GET ${BASE}/verifications`]: () => json({ totalChecks: 0, byResult: { VALID: 0, FAKE: 0, REVOKED: 0, EXPIRED: 0, NOT_FOUND: 0 },
        checks: page([]) }),
    })
    renderAt('/institution/verifications', <VerificationLogPage scope="institution" />)

    expect(await screen.findByRole('heading', { name: 'Verification activity' })).toBeInTheDocument()
    expect(await screen.findByText('No checks yet')).toBeInTheDocument()
  })
})
