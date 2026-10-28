import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { AppNotification } from '../api/notifications'
import { json } from '../test/fixtures'
import { NotificationBell } from './NotificationBell'

const fetchMock = vi.fn<typeof fetch>()

beforeEach(() => {
  vi.stubGlobal('fetch', fetchMock)
  fetchMock.mockReset()
})

afterEach(() => {
  vi.unstubAllGlobals()
})

function notification(overrides: Partial<AppNotification> = {}): AppNotification {
  return {
    id: 'n-1', type: 'CERTIFICATE_ISSUED', title: 'New certificate: Bachelor of Technology',
    body: 'Sahyadri Institute of Technology issued your certificate SIT-2026-000001.', link: '/student/certificates',
    createdAt: new Date().toISOString(), read: false, ...overrides,
  }
}

/** Answers by "METHOD path"; unknown requests fail the test. Returns the list of requests made. */
function server(items: AppNotification[]) {
  const requests: string[] = []
  fetchMock.mockImplementation(async (input, init) => {
    const key = `${init?.method ?? 'GET'} ${new URL(String(input)).pathname}`
    requests.push(key)
    const unread = items.filter((n) => !n.read).length
    if (key === 'GET /api/v1/notifications/unread-count') return json({ unreadCount: unread })
    if (key === 'GET /api/v1/notifications') {
      return json({ unreadCount: unread, notifications: { content: items, page: 0, size: 10, totalElements: items.length, totalPages: 1, last: true } })
    }
    if (key.startsWith('POST /api/v1/notifications/')) {
      items = key.endsWith('/read-all') ? items.map((n) => ({ ...n, read: true }))
        : items.map((n) => (key.includes(n.id) ? { ...n, read: true } : n))
      return new Response(null, { status: 204 })
    }
    throw new Error(`unexpected request ${key}`)
  })
  return requests
}

function renderBell() {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter initialEntries={['/student']}>
        <Routes>
          <Route path="/student" element={<NotificationBell />} />
          <Route path="/student/certificates" element={<p>My certificates page</p>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('Notification bell', () => {
  it('shows the unread count, and clicking a notification marks it read and opens its page', async () => {
    const requests = server([notification(), notification({ id: 'n-2', type: 'WELCOME', title: 'Welcome', read: true, link: null })])
    renderBell()

    const bell = await screen.findByRole('button', { name: 'Notifications, 1 unread' })
    await userEvent.click(bell)
    expect(await screen.findByText('New certificate: Bachelor of Technology')).toBeInTheDocument()
    expect(screen.getByText('Welcome')).toBeInTheDocument()

    await userEvent.click(screen.getByText('New certificate: Bachelor of Technology'))
    expect(await screen.findByText('My certificates page')).toBeInTheDocument()
    expect(requests).toContain('POST /api/v1/notifications/n-1/read')
  })

  it('"Mark all as read" clears the count; an empty list says so', async () => {
    const requests = server([notification(), notification({ id: 'n-2' })])
    renderBell()

    await userEvent.click(await screen.findByRole('button', { name: 'Notifications, 2 unread' }))
    await userEvent.click(await screen.findByRole('button', { name: 'Mark all as read' }))
    expect(await screen.findByRole('button', { name: 'Notifications' })).toBeInTheDocument()
    expect(requests).toContain('POST /api/v1/notifications/read-all')
    expect(screen.queryByRole('button', { name: 'Mark all as read' })).not.toBeInTheDocument()
  })

  it('says when there is nothing yet', async () => {
    server([])
    renderBell()
    await userEvent.click(await screen.findByRole('button', { name: 'Notifications' }))
    expect(await screen.findByText('You have no notifications yet.')).toBeInTheDocument()
  })
})
