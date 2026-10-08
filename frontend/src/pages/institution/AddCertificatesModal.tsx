import { useMutation, useQuery } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { institutionApi, type CertificateType, type Student } from '../../api/institution'
import { Alert } from '../../components/Alert'
import { Button } from '../../components/Button'
import { Modal } from '../../components/Modal'
import { SearchBox } from '../../components/SearchBox'
import { SelectField } from '../../components/SelectField'
import { Spinner } from '../../components/Spinner'
import { TextField } from '../../components/TextField'
import { errorMessage, fieldErrors } from '../../components/errors'
import { CERTIFICATE_TYPE, options } from './labels'

interface Pick { student: Student; cgpa: string; grade: string }

const today = () => new Date().toLocaleDateString('en-CA')

/** Adds the same certificate (e.g. "B.Tech in Computer Engineering") for several students, each with their own CGPA and grade. */
export function AddCertificatesModal({ batchId, alreadyIn, onClose, onAdded }: {
  batchId: string
  alreadyIn: Set<string>
  onClose: () => void
  onAdded: (count: number) => void
}) {
  const [type, setType] = useState<CertificateType>('DEGREE')
  const [title, setTitle] = useState('')
  const [program, setProgram] = useState('')
  const [awardedOn, setAwardedOn] = useState(today())
  const [search, setSearch] = useState('')
  const [picked, setPicked] = useState<Map<string, Pick>>(new Map())

  const students = useQuery({
    queryKey: ['institution', 'students', search, 'picker'],
    queryFn: () => institutionApi.students(search, 0, 50),
    placeholderData: (previous) => previous,
  })

  const add = useMutation({
    mutationFn: () => institutionApi.addCertificates(batchId, {
      type,
      title: title.trim(),
      program: program.trim() || undefined,
      awardedOn,
      items: [...picked.values()].map((p) => ({
        studentId: p.student.id,
        cgpa: p.cgpa.trim() ? Number(p.cgpa) : undefined,
        grade: p.grade.trim() || undefined,
      })),
    }),
    onSuccess: (created) => onAdded(created.length),
  })
  const errors = add.isError ? fieldErrors(add.error) : {}

  const toggle = (student: Student) => setPicked((current) => {
    const next = new Map(current)
    if (next.has(student.id)) next.delete(student.id)
    else next.set(student.id, { student, cgpa: '', grade: '' })
    return next
  })
  const edit = (id: string, field: 'cgpa' | 'grade', value: string) => setPicked((current) => {
    const next = new Map(current)
    next.set(id, { ...next.get(id)!, [field]: value })
    return next
  })

  const badCgpa = [...picked.values()].some((p) => p.cgpa.trim() !== '' && !/^(10(\.0{1,2})?|\d(\.\d{1,2})?)$/.test(p.cgpa.trim()))
  const ready = title.trim() !== '' && awardedOn !== '' && picked.size > 0 && !badCgpa

  const submit = (event: FormEvent) => {
    event.preventDefault()
    if (ready) add.mutate()
  }

  const available = students.data?.content.filter((s) => !alreadyIn.has(s.id)) ?? []

  return (
    <Modal title="Add certificates" onClose={onClose} locked={add.isPending}
           footer={<>
             <Button variant="secondary" onClick={onClose} disabled={add.isPending}>Cancel</Button>
             <Button type="submit" form="add-certificates" disabled={!ready} loading={add.isPending}>
               Add {picked.size || ''} {picked.size === 1 ? 'certificate' : 'certificates'}
             </Button>
           </>}>
      <form id="add-certificates" onSubmit={submit} noValidate className="space-y-4">
        {add.isError && <Alert tone="error">{errorMessage(add.error)}</Alert>}
        <div className="grid gap-4 sm:grid-cols-2">
          <SelectField label="Type" value={type} onChange={(e) => setType(e.target.value as CertificateType)}
                       options={options(CERTIFICATE_TYPE)} />
          <TextField label="Awarded on" type="date" required max={today()} value={awardedOn}
                     onChange={(e) => setAwardedOn(e.target.value)} error={errors.awardedOn} />
        </div>
        <TextField label="Certificate title" required value={title} onChange={(e) => setTitle(e.target.value)} error={errors.title}
                   placeholder="Bachelor of Technology" maxLength={200} />
        <TextField label="Programme (optional)" value={program} onChange={(e) => setProgram(e.target.value)} error={errors.program}
                   placeholder="Computer Engineering" maxLength={150} />

        <div>
          <p className="text-sm font-medium text-slate-700">Students</p>
          <p className="text-xs text-slate-500">Tick each student who gets this certificate. CGPA (0–10) and grade are optional.</p>
          <div className="mt-2"><SearchBox placeholder="Search students" onSearch={setSearch} /></div>
          <div className="mt-2 max-h-64 overflow-y-auto rounded-xl ring-1 ring-slate-200">
            {students.isPending && <div className="flex justify-center p-6"><Spinner className="h-5 w-5 text-navy-600" /></div>}
            {students.isError && <p className="p-4 text-sm text-red-600">{errorMessage(students.error)}</p>}
            {students.data && available.length === 0 && (
              <p className="p-4 text-sm text-slate-500">
                {search ? 'No student matches your search.' : 'No students left to add. Add students on the Students page first.'}
              </p>
            )}
            <ul className="divide-y divide-slate-100">
              {available.map((s) => {
                const pick = picked.get(s.id)
                return (
                  <li key={s.id} className={`px-3 py-2.5 ${pick ? 'bg-navy-50/60' : ''}`}>
                    <label className="flex cursor-pointer items-center gap-3">
                      <input type="checkbox" checked={!!pick} onChange={() => toggle(s)}
                             className="h-4 w-4 rounded border-slate-300 accent-navy-700" />
                      <span className="min-w-0 flex-1">
                        <span className="block truncate text-sm font-semibold text-slate-900">{s.fullName}</span>
                        <span className="block truncate text-xs text-slate-500">{s.enrollmentNo} · {s.program}</span>
                      </span>
                    </label>
                    {pick && (
                      <div className="mt-2 flex gap-2 pl-7">
                        <input aria-label={`CGPA for ${s.fullName}`} inputMode="decimal" placeholder="CGPA" value={pick.cgpa}
                               onChange={(e) => edit(s.id, 'cgpa', e.target.value)}
                               className="w-24 rounded-lg border border-slate-300 px-2.5 py-1.5 text-sm outline-none focus:border-navy-600" />
                        <input aria-label={`Grade for ${s.fullName}`} placeholder="Grade, e.g. First Class" value={pick.grade} maxLength={100}
                               onChange={(e) => edit(s.id, 'grade', e.target.value)}
                               className="min-w-0 flex-1 rounded-lg border border-slate-300 px-2.5 py-1.5 text-sm outline-none focus:border-navy-600" />
                      </div>
                    )}
                  </li>
                )
              })}
            </ul>
          </div>
          {badCgpa && <p className="mt-1 text-xs text-red-600">CGPA must be a number from 0 to 10 with at most 2 decimals.</p>}
        </div>
      </form>
    </Modal>
  )
}
