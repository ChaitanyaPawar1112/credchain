import { useMutation } from '@tanstack/react-query'
import { useState, type ChangeEvent, type FormEvent } from 'react'
import { Link } from 'react-router'
import { applicationApi, type InstitutionApplication } from '../api/admin'
import { Alert } from '../components/Alert'
import { Button } from '../components/Button'
import { Icon } from '../components/Icon'
import { TextField } from '../components/TextField'
import { errorMessage, fieldErrors } from '../components/errors'
import { INSTITUTION_TYPE } from './admin/labels'

/** Every field as typed text; `type` holds an InstitutionType. */
type Form = Record<keyof InstitutionApplication, string>

const EMPTY: Form = {
  name: '', code: '', registrationNumber: '', type: 'COLLEGE', email: '', phone: '', website: '', addressLine: '',
  city: '', state: '', postalCode: '', contactPersonName: '', contactPersonEmail: '',
}

const REQUIRED: (keyof Form)[] = ['name', 'code', 'registrationNumber', 'email', 'city', 'state', 'contactPersonName', 'contactPersonEmail']

/** Public: a college applies to issue certificates on CredChain. The super admin reviews it. */
export function ApplyCollegePage() {
  const [form, setForm] = useState<Form>(EMPTY)
  const apply = useMutation({
    mutationFn: () => {
      // optional fields are left out when empty: the backend checks their format only when present
      const body = Object.fromEntries(Object.entries(form).map(([k, v]) => [k, v.trim()]).filter(([, v]) => v !== ''))
      return applicationApi.apply(body as unknown as InstitutionApplication)
    },
  })
  const errors = apply.isError ? fieldErrors(apply.error) : {}
  const set = (key: keyof Form) => (e: ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setForm((f) => ({ ...f, [key]: e.target.value }))
  const complete = REQUIRED.every((key) => form[key].trim() !== '')

  const submit = (event: FormEvent) => {
    event.preventDefault()
    apply.mutate()
  }

  if (apply.isSuccess) {
    return (
      <div className="mx-auto max-w-2xl px-4 py-14">
        <div className="animate-fade-up rounded-3xl bg-white p-8 text-center shadow-xl shadow-navy-900/5 ring-1 ring-slate-200 sm:p-12">
          <span className="mx-auto flex h-16 w-16 items-center justify-center rounded-2xl bg-emerald-600 text-white shadow-lg">
            <Icon name="checkCircle" className="h-9 w-9" />
          </span>
          <h1 className="mt-6 text-2xl font-extrabold text-slate-900">Application received</h1>
          <p className="mt-2 text-slate-600">
            Thank you. The CredChain team will check <strong>{apply.data.name}</strong>. Once it is approved, the admin login
            for <strong>{apply.data.contactPersonEmail}</strong> is shared with you.
          </p>
          <p className="mt-6 inline-block rounded-xl bg-slate-50 px-4 py-2 text-xs text-slate-500 ring-1 ring-slate-200">
            Application reference: <span className="font-mono">{apply.data.id}</span>
          </p>
          <div className="mt-8"><Link to="/" className="text-sm font-semibold text-navy-700 hover:underline">Back to home</Link></div>
        </div>
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-3xl px-4 py-10 sm:py-14">
      <div className="text-center">
        <p className="text-sm font-semibold uppercase tracking-wider text-gold-500">For colleges</p>
        <h1 className="mt-2 text-3xl font-extrabold tracking-tight text-slate-900">Apply to issue certificates on CredChain</h1>
        <p className="mx-auto mt-2 max-w-xl text-slate-600">
          Tell us about your institution. After approval you get an admin account and a blockchain wallet, ready to issue.
        </p>
      </div>

      <form onSubmit={submit} noValidate
            className="animate-fade-up mt-10 space-y-8 rounded-3xl bg-white p-6 shadow-xl shadow-navy-900/5 ring-1 ring-slate-200 sm:p-10">
        {apply.isError && <Alert tone="error">{errorMessage(apply.error)}</Alert>}

        <fieldset className="space-y-4">
          <legend className="text-lg font-bold text-slate-900">Institution</legend>
          <TextField label="Institution name" required value={form.name} onChange={set('name')} error={errors.name}
                     placeholder="Sahyadri Institute of Technology" />
          <div className="grid gap-4 sm:grid-cols-2">
            <TextField label="Short code" required value={form.code} onChange={set('code')} error={errors.code}
                       placeholder="SIT-AUR" hint="3–20 letters, digits or hyphens. Printed on certificate numbers." />
            <div>
              <label htmlFor="inst-type" className="block text-sm font-medium text-slate-700">Type</label>
              <select id="inst-type" value={form.type} onChange={set('type')}
                      className="mt-1.5 block w-full rounded-xl border border-slate-300 bg-white px-3.5 py-2.5 text-sm outline-none focus:border-navy-600 focus:ring-4 focus:ring-navy-100">
                {Object.entries(INSTITUTION_TYPE).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
              </select>
            </div>
          </div>
          <TextField label="Registration / AISHE number" required value={form.registrationNumber} onChange={set('registrationNumber')}
                     error={errors.registrationNumber} placeholder="AISHE-C-45678" />
          <div className="grid gap-4 sm:grid-cols-2">
            <TextField label="Official email" type="email" required value={form.email} onChange={set('email')} error={errors.email}
                       placeholder="office@college.edu.in" />
            <TextField label="Phone (optional)" type="tel" value={form.phone} onChange={set('phone')} error={errors.phone}
                       placeholder="+912402345678" />
          </div>
          <TextField label="Website (optional)" type="url" value={form.website} onChange={set('website')} error={errors.website}
                     placeholder="https://college.edu.in" />
        </fieldset>

        <fieldset className="space-y-4">
          <legend className="text-lg font-bold text-slate-900">Address</legend>
          <TextField label="Street address (optional)" value={form.addressLine} onChange={set('addressLine')} error={errors.addressLine} />
          <div className="grid gap-4 sm:grid-cols-3">
            <TextField label="City" required value={form.city} onChange={set('city')} error={errors.city} />
            <TextField label="State" required value={form.state} onChange={set('state')} error={errors.state} />
            <TextField label="PIN code (optional)" value={form.postalCode} onChange={set('postalCode')} error={errors.postalCode} />
          </div>
        </fieldset>

        <fieldset className="space-y-4">
          <legend className="text-lg font-bold text-slate-900">Contact person</legend>
          <p className="-mt-2 text-sm text-slate-500">Usually the registrar. This person becomes the college admin and logs in with this email.</p>
          <div className="grid gap-4 sm:grid-cols-2">
            <TextField label="Full name" required value={form.contactPersonName} onChange={set('contactPersonName')}
                       error={errors.contactPersonName} placeholder="Dr. Anil Deshmukh" />
            <TextField label="Email" type="email" required value={form.contactPersonEmail} onChange={set('contactPersonEmail')}
                       error={errors.contactPersonEmail} placeholder="registrar@college.edu.in" />
          </div>
        </fieldset>

        <Button type="submit" className="w-full py-3" disabled={!complete} loading={apply.isPending}>Submit application</Button>
        <p className="text-center text-sm text-slate-500">
          Already approved? <Link to="/login" className="font-semibold text-navy-700 hover:underline">Log in</Link>
        </p>
      </form>
    </div>
  )
}
