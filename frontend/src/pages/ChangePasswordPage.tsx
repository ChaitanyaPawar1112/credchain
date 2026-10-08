import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { Alert } from '../components/Alert'
import { Button } from '../components/Button'
import { TextField } from '../components/TextField'
import { errorMessage, fieldErrors } from '../components/errors'
import { AuthCard } from '../layouts/AuthCard'
import { PASSWORD_HINT, PASSWORD_RULE } from './passwordRule'

/** Also the forced first step for accounts created with a temporary password (college admins). */
export function ChangePasswordPage() {
  const { user, changePassword } = useAuth()
  const navigate = useNavigate()
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [serverErrors, setServerErrors] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)

  const nextError = next && !PASSWORD_RULE.test(next) ? PASSWORD_HINT : serverErrors.newPassword
  const confirmError = confirm && confirm !== next ? 'Passwords do not match' : undefined
  const canSubmit = current && PASSWORD_RULE.test(next) && next === confirm

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setError(null)
    setServerErrors({})
    setSubmitting(true)
    try {
      await changePassword(current, next)
      navigate('/login', { replace: true, state: { notice: 'Password changed. Please log in with your new password.' } })
    } catch (e) {
      setError(errorMessage(e))
      setServerErrors(fieldErrors(e))
      setSubmitting(false)
    }
  }

  return (
    <AuthCard title="Set a new password"
              subtitle={user?.mustChangePassword
                ? 'Your account was created with a temporary password. Choose your own password to continue.'
                : 'You will be logged out on all devices and asked to log in again.'}>
      <form onSubmit={handleSubmit} className="space-y-4" noValidate>
        {error && <Alert tone="error">{error}</Alert>}
        <TextField label="Current password" type="password" autoComplete="current-password" required value={current}
                   onChange={(e) => setCurrent(e.target.value)} error={serverErrors.currentPassword} />
        <TextField label="New password" type="password" autoComplete="new-password" required value={next}
                   onChange={(e) => setNext(e.target.value)} error={nextError} hint={PASSWORD_HINT} />
        <TextField label="Confirm new password" type="password" autoComplete="new-password" required value={confirm}
                   onChange={(e) => setConfirm(e.target.value)} error={confirmError} />
        <Button type="submit" className="w-full" loading={submitting} disabled={!canSubmit}>
          Change password
        </Button>
      </form>
    </AuthCard>
  )
}
