import { useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { HOME_BY_ROLE } from '../auth/roles'
import { Alert } from '../components/Alert'
import { Button } from '../components/Button'
import { TextField } from '../components/TextField'
import { errorMessage } from '../components/errors'
import { AuthCard } from '../layouts/AuthCard'

interface LoginState {
  from?: string
  notice?: string
}

export function LoginPage() {
  const { user, login } = useAuth()
  const navigate = useNavigate()
  const state = (useLocation().state ?? {}) as LoginState
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  if (user && !submitting) {
    return <Navigate to={user.mustChangePassword ? '/change-password' : HOME_BY_ROLE[user.role]} replace />
  }

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      const loggedIn = await login(email.trim(), password)
      const target = loggedIn.mustChangePassword ? '/change-password' : state.from ?? HOME_BY_ROLE[loggedIn.role]
      navigate(target, { replace: true })
    } catch (e) {
      setError(errorMessage(e))
      setSubmitting(false)
    }
  }

  return (
    <AuthCard title="Log in" subtitle={<>New here? <Link to="/register" className="font-medium text-navy-700 hover:underline">Create an account</Link></>}>
      <form onSubmit={handleSubmit} className="space-y-4" noValidate>
        {state.notice && <Alert tone="success">{state.notice}</Alert>}
        {error && <Alert tone="error">{error}</Alert>}
        <TextField label="Email" type="email" autoComplete="email" required value={email}
                   onChange={(e) => setEmail(e.target.value)} />
        <TextField label="Password" type="password" autoComplete="current-password" required value={password}
                   onChange={(e) => setPassword(e.target.value)} />
        <Button type="submit" className="w-full" loading={submitting} disabled={!email || !password}>
          Log in
        </Button>
      </form>
    </AuthCard>
  )
}
