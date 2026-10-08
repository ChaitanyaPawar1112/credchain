import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { storeSession } from '../api/client'
import { AuthProvider } from '../auth/AuthProvider'
import { json, session } from '../test/fixtures'
import { LoginPage } from './LoginPage'

const fetchMock = vi.fn<typeof fetch>()

beforeEach(() => {
  vi.stubGlobal('fetch', fetchMock)
  fetchMock.mockReset()
})

afterEach(() => {
  vi.unstubAllGlobals()
  storeSession(null)
})

function renderLogin() {
  render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter initialEntries={['/login']}>
        <AuthProvider>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/institution" element={<p>college dashboard</p>} />
            <Route path="/change-password" element={<p>change password page</p>} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

async function fillAndSubmit() {
  await userEvent.type(screen.getByLabelText('Email'), 'registrar@college.edu')
  await userEvent.type(screen.getByLabelText('Password'), 'Str0ng@Pass')
  await userEvent.click(screen.getByRole('button', { name: 'Log in' }))
}

describe('LoginPage', () => {
  it('shows the server message for a wrong password', async () => {
    fetchMock.mockResolvedValueOnce(json({ status: 401, code: 'INVALID_CREDENTIALS', detail: 'Invalid email or password' }, 401))
    renderLogin()
    await fillAndSubmit()
    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password')
  })

  it('takes a college admin to the college dashboard', async () => {
    fetchMock.mockResolvedValueOnce(json(session('INSTITUTION_ADMIN')))
    renderLogin()
    await fillAndSubmit()
    expect(await screen.findByText('college dashboard')).toBeInTheDocument()
  })

  it('a temporary password goes to the change-password screen first', async () => {
    const temporary = session('INSTITUTION_ADMIN')
    temporary.user.mustChangePassword = true
    fetchMock.mockResolvedValueOnce(json(temporary))
    renderLogin()
    await fillAndSubmit()
    expect(await screen.findByText('change password page')).toBeInTheDocument()
  })
})
