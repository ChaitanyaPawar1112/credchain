import { useState, type ChangeEvent, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import type { RegisterInput } from '../api/auth'
import { useAuth } from '../auth/AuthContext'
import { HOME_BY_ROLE } from '../auth/roles'
import { Alert } from '../components/Alert'
import { Button } from '../components/Button'
import { TextField } from '../components/TextField'
import { errorMessage, fieldErrors } from '../components/errors'
import { AuthCard } from '../layouts/AuthCard'
import { PASSWORD_HINT, PASSWORD_RULE } from './passwordRule'

const ROLES: { value: RegisterInput['role']; title: string; text: string }[] = [
  { value: 'STUDENT', title: 'Student', text: 'See and share the certificates your college issued to you' },
  { value: 'VERIFIER', title: 'Employer / verifier', text: 'Check certificates that candidates send you' },
]

export function RegisterPage() {
  const { user, register } = useAuth()
  const navigate = useNavigate()
  const [form, setForm] = useState({ fullName: '', email: '', phone: '', password: '', confirm: '' })
  const [role, setRole] = useState<RegisterInput['role']>('STUDENT')
  const [error, setError] = useState<string | null>(null)
  const [serverErrors, setServerErrors] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)

  if (user && !submitting) {
    return <Navigate to={HOME_BY_ROLE[user.role]} replace />
  }

  const set = (key: keyof typeof form) => (e: ChangeEvent<HTMLInputElement>) =>
    setForm((f) => ({ ...f, [key]: e.target.value }))

  const passwordError = form.password && !PASSWORD_RULE.test(form.password) ? PASSWORD_HINT : serverErrors.password
  const confirmError = form.confirm && form.confirm !== form.password ? 'Passwords do not match' : undefined
  const canSubmit = form.fullName.trim() && form.email.trim() && PASSWORD_RULE.test(form.password)
    && form.password === form.confirm

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setError(null)
    setServerErrors({})
    setSubmitting(true)
    try {
      const created = await register({
        fullName: form.fullName.trim(), email: form.email.trim(), phone: form.phone, password: form.password, role,
      })
      navigate(HOME_BY_ROLE[created.role], { replace: true })
    } catch (e) {
      setError(errorMessage(e))
      setServerErrors(fieldErrors(e))
      setSubmitting(false)
    }
  }

  return (
    <AuthCard title="Create your account"
              subtitle={<>Already registered? <Link to="/login" className="font-medium text-navy-700 hover:underline">Log in</Link></>}>
      <form onSubmit={handleSubmit} className="space-y-4" noValidate>
        {error && <Alert tone="error">{error}</Alert>}

        <fieldset>
          <legend className="text-sm font-medium text-slate-700">I am a</legend>
          <div className="mt-2 grid gap-2 sm:grid-cols-2">
            {ROLES.map((r) => (
              <label key={r.value}
                     className={`cursor-pointer rounded-lg p-3 ring-1 transition ${role === r.value
                       ? 'bg-navy-50 ring-2 ring-navy-600' : 'ring-slate-300 hover:bg-slate-50'}`}>
                <input type="radio" name="role" value={r.value} checked={role === r.value}
                       onChange={() => setRole(r.value)} className="sr-only" />
                <span className="block text-sm font-semibold text-slate-900">{r.title}</span>
                <span className="mt-0.5 block text-xs text-slate-600">{r.text}</span>
              </label>
            ))}
          </div>
          <p className="mt-2 text-xs text-slate-500">
            Colleges don't sign up here: they apply and are approved by the CredChain admin.
          </p>
        </fieldset>

        <TextField label="Full name" autoComplete="name" required value={form.fullName} onChange={set('fullName')}
                   error={serverErrors.fullName} />
        <TextField label="Email" type="email" autoComplete="email" required value={form.email} onChange={set('email')}
                   error={serverErrors.email} />
        <TextField label="Phone (optional)" type="tel" autoComplete="tel" placeholder="+919876543210" value={form.phone}
                   onChange={set('phone')} error={serverErrors.phone} />
        <TextField label="Password" type="password" autoComplete="new-password" required value={form.password}
                   onChange={set('password')} error={passwordError} hint={PASSWORD_HINT} />
        <TextField label="Confirm password" type="password" autoComplete="new-password" required value={form.confirm}
                   onChange={set('confirm')} error={confirmError} />
        <Button type="submit" className="w-full" loading={submitting} disabled={!canSubmit}>
          Create account
        </Button>
      </form>
    </AuthCard>
  )
}
