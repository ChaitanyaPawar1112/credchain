import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState, type ChangeEvent, type FormEvent } from 'react'
import { studentApi, type LinkRequest } from '../../api/student'
import { Alert } from '../../components/Alert'
import { Button } from '../../components/Button'
import { Icon } from '../../components/Icon'
import { TextField } from '../../components/TextField'
import { errorMessage, fieldErrors } from '../../components/errors'

const EMPTY: LinkRequest = { institutionCode: '', enrollmentNo: '', claimCode: '' }

/** A new student account connects to its college record with the three details the college gave them. */
export function LinkAccountCard() {
  const queryClient = useQueryClient()
  const [form, setForm] = useState<LinkRequest>(EMPTY)
  const link = useMutation({
    mutationFn: () => studentApi.link({
      institutionCode: form.institutionCode.trim().toUpperCase(),
      enrollmentNo: form.enrollmentNo.trim(),
      claimCode: form.claimCode.trim().toUpperCase(),
    }),
    onSuccess: (profile) => {
      queryClient.setQueryData(['student', 'profile'], profile)
      void queryClient.invalidateQueries({ queryKey: ['student'] })
    },
  })
  const errors = link.isError ? fieldErrors(link.error) : {}
  const set = (key: keyof LinkRequest) => (e: ChangeEvent<HTMLInputElement>) => setForm((f) => ({ ...f, [key]: e.target.value }))
  const complete = Object.values(form).every((v) => v.trim() !== '')

  const submit = (event: FormEvent) => {
    event.preventDefault()
    if (complete) link.mutate()
  }

  return (
    <section className="overflow-hidden rounded-2xl bg-white shadow-sm ring-1 ring-slate-200">
      <div className="grid md:grid-cols-5">
        <div className="bg-gradient-to-br from-navy-700 to-navy-900 p-6 text-white md:col-span-2 sm:p-8">
          <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-white/10 text-gold-300"><Icon name="lock" className="h-5 w-5" /></span>
          <h2 className="mt-4 text-xl font-bold">Connect your college record</h2>
          <p className="mt-2 text-sm leading-relaxed text-navy-100">
            Your college's office gives you three things: the college code, your enrollment number and a one-time claim code.
            Enter them once and your certificates show up here.
          </p>
          <p className="mt-4 text-xs text-navy-200">No claim code? Ask your college's exam or registrar office for one. It is valid for 7 days.</p>
        </div>
        <form onSubmit={submit} noValidate className="space-y-4 p-6 md:col-span-3 sm:p-8">
          {link.isError && <Alert tone="error">{errorMessage(link.error)}</Alert>}
          <TextField label="College code" required value={form.institutionCode} onChange={set('institutionCode')}
                     error={errors.institutionCode} placeholder="SIT-AUR" autoCapitalize="characters" maxLength={20} />
          <TextField label="Enrollment number" required value={form.enrollmentNo} onChange={set('enrollmentNo')}
                     error={errors.enrollmentNo} placeholder="2022CS001" maxLength={50} />
          <TextField label="Claim code" required value={form.claimCode} onChange={set('claimCode')} error={errors.claimCode}
                     placeholder="K7P2-M9QX" autoCapitalize="characters" autoComplete="off" maxLength={20}
                     className="[&_input]:font-mono [&_input]:tracking-widest" />
          <Button type="submit" className="w-full py-3" disabled={!complete} loading={link.isPending}>Connect my record</Button>
        </form>
      </div>
    </section>
  )
}
