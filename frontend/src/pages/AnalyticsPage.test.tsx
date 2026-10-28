import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Analytics } from '../api/analytics'
import { json } from '../test/fixtures'
import { AnalyticsPage } from './AnalyticsPage'

const fetchMock = vi.fn<typeof fetch>()

beforeEach(() => {
  vi.stubGlobal('fetch', fetchMock)
  fetchMock.mockReset()
})

afterEach(() => {
  vi.unstubAllGlobals()
})

function analytics(days: number, overrides: Partial<Analytics> = {}): Analytics {
  const daily = Array.from({ length: days }, (_, i) => ({
    date: new Date(Date.UTC(2026, 9, 28 - (days - 1 - i))).toISOString().slice(0, 10),
    issued: 0, revoked: 0, checks: 0, valid: 0, failed: 0,
  }))
  daily[days - 1] = { ...daily[days - 1]!, issued: 12, checks: 5, valid: 3, failed: 1 }
  return {
    from: daily[0]!.date, to: daily[days - 1]!.date,
    totals: { colleges: 4, pendingApplications: 2, students: 320, studentsWithAccount: 120, certificatesIssued: 250,
      certificatesRevoked: 3, checksInPeriod: 5, fakeChecksInPeriod: 1 },
    daily,
    checksByResult: { VALID: 3, REVOKED: 1, EXPIRED: 0, NOT_FOUND: 0, FAKE: 1 },
    checksByMethod: { HASH: 3, PDF: 1, DOCUMENT: 1 },
    topColleges: [{ institutionId: 'c-1', name: 'Sahyadri Institute of Technology', code: 'SIT', issued: 12, checks: 5, fakeChecks: 1 }],
    ...overrides,
  }
}

function renderPage(scope: 'admin' | 'institution', answer: (days: number) => Analytics) {
  const urls: string[] = []
  fetchMock.mockImplementation(async (input) => {
    const url = new URL(String(input))
    urls.push(url.pathname + url.search)
    return json(answer(Number(url.searchParams.get('days'))))
  })
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter><AnalyticsPage scope={scope} /></MemoryRouter>
    </QueryClientProvider>,
  )
  return urls
}

describe('Analytics dashboard', () => {
  it('super admin: totals, both charts, breakdowns and the most active colleges', async () => {
    const urls = renderPage('admin', (d) => analytics(d))

    expect(await screen.findByText('Approved colleges')).toBeInTheDocument()
    expect(screen.getByText('2 waiting for review')).toBeInTheDocument()
    expect(screen.getByText('1 were fake copies')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Sahyadri Institute of Technology' })).toHaveAttribute('href', '/admin/institutions/c-1')
    expect(urls).toEqual(['/api/v1/admin/analytics?days=30'])

    // the stacked chart has a legend and one labelled column per day
    const checks = screen.getByRole('region', { name: 'Checks per day' })
    expect(within(checks).getByRole('list', { name: 'Legend' })).toHaveTextContent('Fake or not found')
    expect(within(checks).getAllByRole('img')).toHaveLength(30)
    expect(within(checks).getByRole('img', { name: /Genuine 3, Revoked or expired 1, Fake or not found 1/ })).toBeInTheDocument()

    const results = screen.getByRole('region', { name: 'What checks answered' })
    expect(within(results).getByText('Genuine').parentElement).toHaveTextContent(/Genuine\s*3\s*60%/)
  })

  it('hovering a day shows its numbers; the table view lists them as text', async () => {
    renderPage('admin', (d) => analytics(d))
    const issued = await screen.findByRole('region', { name: 'Certificates issued per day' })
    const columns = within(issued).getAllByRole('img')

    await userEvent.hover(columns[columns.length - 1]!)
    expect(within(issued).getByRole('status')).toHaveTextContent('Issued: 12')

    await userEvent.click(within(issued).getByRole('button', { name: 'Show as table' }))
    const rows = within(issued).getAllByRole('row')
    expect(rows).toHaveLength(31)   // header + 30 days, newest first
    expect(rows[1]).toHaveTextContent('12')
  })

  it('changing the period asks the server again; a college sees no platform-only numbers', async () => {
    const urls = renderPage('institution', (d) => analytics(d, {
      topColleges: undefined, totals: { ...analytics(d).totals, colleges: null, pendingApplications: null },
    }))
    await screen.findByText('Certificates issued')
    expect(screen.queryByText('Approved colleges')).not.toBeInTheDocument()
    expect(screen.queryByText('Most active colleges')).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('tab', { name: 'Last 90 days' }))
    expect(await screen.findAllByRole('img')).toHaveLength(180)   // 90 columns in each chart
    expect(urls).toEqual(['/api/v1/institution/analytics?days=30', '/api/v1/institution/analytics?days=90'])
  })

  it('a quiet period says so instead of drawing empty bars', async () => {
    renderPage('institution', (d) => analytics(d, { daily: analytics(d).daily.map((x) => ({ ...x, issued: 0, checks: 0, valid: 0, failed: 0 })) }))
    expect(await screen.findByText('No certificates were issued in this period.')).toBeInTheDocument()
    expect(screen.getByText('Nobody checked a certificate in this period.')).toBeInTheDocument()
  })
})
