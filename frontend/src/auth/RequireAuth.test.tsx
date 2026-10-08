import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { describe, expect, it } from 'vitest'
import type { UserSummary } from '../api/types'
import { user } from '../test/fixtures'
import { AuthContext, type AuthState } from './AuthContext'
import { RequireAuth } from './RequireAuth'

function renderAt(path: string, current: UserSummary | null) {
  const auth: AuthState = {
    user: current, loading: false,
    login: async () => current!, register: async () => current!, logout: async () => {}, changePassword: async () => {},
  }
  render(
    <AuthContext.Provider value={auth}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/login" element={<p>login page</p>} />
          <Route path="/change-password" element={<p>change password page</p>} />
          <Route path="/student" element={<p>student home</p>} />
          <Route path="/admin" element={<RequireAuth roles={['SUPER_ADMIN']}><p>admin area</p></RequireAuth>} />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>,
  )
}

describe('RequireAuth', () => {
  it('sends a logged-out visitor to the login page', () => {
    renderAt('/admin', null)
    expect(screen.getByText('login page')).toBeInTheDocument()
  })

  it('sends a user with another role to their own dashboard', () => {
    renderAt('/admin', user('STUDENT'))
    expect(screen.getByText('student home')).toBeInTheDocument()
  })

  it('a user with a temporary password must change it first', () => {
    renderAt('/admin', user('SUPER_ADMIN', { mustChangePassword: true }))
    expect(screen.getByText('change password page')).toBeInTheDocument()
  })

  it('shows the page to the right role', () => {
    renderAt('/admin', user('SUPER_ADMIN'))
    expect(screen.getByText('admin area')).toBeInTheDocument()
  })
})
