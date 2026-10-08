import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type ChangeEvent, type FormEvent } from 'react'
import { institutionApi, saveFile, type ClaimCode, type NewStudent, type Student, type StudentImportResult } from '../../api/institution'
import { Alert } from '../../components/Alert'
import { Badge } from '../../components/Badge'
import { Button } from '../../components/Button'
import { Icon } from '../../components/Icon'
import { Modal } from '../../components/Modal'
import { PageHeader } from '../../components/PageHeader'
import { Pagination } from '../../components/Pagination'
import { EmptyState, LoadError, LoadingRows, Panel, td, th } from '../../components/Panel'
import { SearchBox } from '../../components/SearchBox'
import { TextField } from '../../components/TextField'
import { errorMessage, fieldErrors } from '../../components/errors'
import { formatDateTime } from '../../lib/format'

const STATUS_TONE = { ACTIVE: 'blue', GRADUATED: 'green', WITHDRAWN: 'slate' } as const

/** College admin: the student register. Add one, import a CSV, or give a student a claim code for their account. */
export function StudentsPage() {
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)
  const [dialog, setDialog] = useState<'add' | 'import' | null>(null)
  const [claimFor, setClaimFor] = useState<Student | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const students = useQuery({
    queryKey: ['institution', 'students', search, page],
    queryFn: () => institutionApi.students(search, page),
    placeholderData: (previous) => previous,
  })

  const onSearch = (value: string) => {
    setSearch(value)
    setPage(0)
  }

  return (
    <div className="animate-fade-up mx-auto max-w-6xl space-y-6">
      <PageHeader icon="student" title="Students"
                  description="Every student must be here before you can issue them a certificate."
                  actions={<>
                    <Button variant="secondary" onClick={() => setDialog('import')}><Icon name="upload" className="h-4 w-4" /> Import CSV</Button>
                    <Button onClick={() => setDialog('add')}>Add student</Button>
                  </>} />

      {notice && <Alert tone="success">{notice}</Alert>}

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <SearchBox placeholder="Search by name or enrollment number" onSearch={onSearch} />
        {students.data && <p className="text-sm text-slate-500">{students.data.totalElements} students</p>}
      </div>

      <Panel>
        {students.isPending && <LoadingRows />}
        {students.isError && <LoadError error={students.error} onRetry={() => students.refetch()} />}
        {students.data && students.data.content.length === 0 && (
          <EmptyState icon="student" title={search ? 'No student matches your search' : 'No students yet'}
                      text={search ? undefined : 'Add them one by one, or import your whole register from a CSV file.'} />
        )}
        {students.data && students.data.content.length > 0 && (
          <>
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-slate-100">
                <thead className="bg-slate-50/80">
                  <tr>
                    <th className={th}>Student</th>
                    <th className={th}>Programme</th>
                    <th className={th}>Batch</th>
                    <th className={th}>Status</th>
                    <th className={th}>CredChain account</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {students.data.content.map((s) => (
                    <tr key={s.id} className="transition hover:bg-slate-50/60">
                      <td className={td}>
                        <p className="font-semibold text-slate-900">{s.fullName}</p>
                        <p className="text-xs text-slate-500">{s.enrollmentNo}{s.email ? ` · ${s.email}` : ''}</p>
                      </td>
                      <td className={td}>
                        <p>{s.program}</p>
                        {s.department && <p className="text-xs text-slate-500">{s.department}</p>}
                      </td>
                      <td className={`${td} whitespace-nowrap`}>{s.admissionYear}{s.graduationYear ? `–${s.graduationYear}` : ''}</td>
                      <td className={td}><Badge tone={STATUS_TONE[s.status]}>{s.status.charAt(0) + s.status.slice(1).toLowerCase()}</Badge></td>
                      <td className={td}>
                        {s.accountLinked
                          ? <Badge tone="green" dot>Linked</Badge>
                          : <button type="button" onClick={() => setClaimFor(s)}
                                    className="inline-flex items-center gap-1 whitespace-nowrap rounded-lg px-2.5 py-1.5 text-sm font-semibold text-navy-700 hover:bg-navy-50">
                              <Icon name="lock" className="h-4 w-4" /> Get claim code
                            </button>}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination data={students.data} onPage={setPage} />
          </>
        )}
      </Panel>

      {dialog === 'add' && <AddStudentModal onClose={() => setDialog(null)}
                                            onAdded={(s) => { setDialog(null); setNotice(`${s.fullName} (${s.enrollmentNo}) was added.`) }} />}
      {dialog === 'import' && <ImportModal onClose={() => setDialog(null)}
                                           onImported={(r) => { setDialog(null); setNotice(`${r.imported} ${r.imported === 1 ? 'student' : 'students'} imported.${r.failed ? ` ${r.failed} ${r.failed === 1 ? 'row was' : 'rows were'} skipped.` : ''}`) }} />}
      {claimFor && <ClaimCodeModal student={claimFor} onClose={() => setClaimFor(null)} />}
    </div>
  )
}

const EMPTY_STUDENT = { enrollmentNo: '', fullName: '', email: '', dateOfBirth: '', program: '', department: '',
  admissionYear: String(new Date().getFullYear() - 4), graduationYear: '' }

function AddStudentModal({ onClose, onAdded }: { onClose: () => void; onAdded: (s: Student) => void }) {
  const queryClient = useQueryClient()
  const [form, setForm] = useState(EMPTY_STUDENT)
  const add = useMutation({
    mutationFn: () => {
      const optional = (v: string) => (v.trim() ? v.trim() : undefined)
      const body: NewStudent = {
        enrollmentNo: form.enrollmentNo.trim(), fullName: form.fullName.trim(), program: form.program.trim(),
        admissionYear: Number(form.admissionYear), email: optional(form.email), dateOfBirth: optional(form.dateOfBirth),
        department: optional(form.department), graduationYear: form.graduationYear ? Number(form.graduationYear) : undefined,
      }
      return institutionApi.addStudent(body)
    },
    onSuccess: (student) => {
      void queryClient.invalidateQueries({ queryKey: ['institution', 'students'] })
      onAdded(student)
    },
  })
  const errors = add.isError ? fieldErrors(add.error) : {}
  const set = (key: keyof typeof form) => (e: ChangeEvent<HTMLInputElement>) => setForm((f) => ({ ...f, [key]: e.target.value }))
  const complete = form.enrollmentNo.trim() && form.fullName.trim() && form.program.trim() && form.admissionYear

  const submit = (event: FormEvent) => {
    event.preventDefault()
    add.mutate()
  }

  return (
    <Modal title="Add a student" onClose={onClose} locked={add.isPending}
           footer={<>
             <Button variant="secondary" onClick={onClose} disabled={add.isPending}>Cancel</Button>
             <Button type="submit" form="add-student" disabled={!complete} loading={add.isPending}>Add student</Button>
           </>}>
      <form id="add-student" onSubmit={submit} className="space-y-4" noValidate>
        {add.isError && <Alert tone="error">{errorMessage(add.error)}</Alert>}
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Enrollment number" required value={form.enrollmentNo} onChange={set('enrollmentNo')}
                     error={errors.enrollmentNo} placeholder="2022CS001" />
          <TextField label="Full name" required value={form.fullName} onChange={set('fullName')} error={errors.fullName} />
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Programme" required value={form.program} onChange={set('program')} error={errors.program}
                     placeholder="B.Tech Computer Engineering" />
          <TextField label="Department (optional)" value={form.department} onChange={set('department')} error={errors.department} />
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Admission year" type="number" required value={form.admissionYear} onChange={set('admissionYear')}
                     error={errors.admissionYear} />
          <TextField label="Graduation year (optional)" type="number" value={form.graduationYear} onChange={set('graduationYear')}
                     error={errors.graduationYear} />
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField label="Email (optional)" type="email" value={form.email} onChange={set('email')} error={errors.email} />
          <TextField label="Date of birth (optional)" type="date" value={form.dateOfBirth} onChange={set('dateOfBirth')}
                     error={errors.dateOfBirth} />
        </div>
      </form>
    </Modal>
  )
}

function ImportModal({ onClose, onImported }: { onClose: () => void; onImported: (r: StudentImportResult) => void }) {
  const queryClient = useQueryClient()
  const [file, setFile] = useState<File | null>(null)
  const preview = useMutation({ mutationFn: (f: File) => institutionApi.importStudents(f, true) })
  const save = useMutation({
    mutationFn: (f: File) => institutionApi.importStudents(f, false),
    onSuccess: (result) => {
      void queryClient.invalidateQueries({ queryKey: ['institution', 'students'] })
      onImported(result)
    },
  })
  const template = useMutation({
    mutationFn: institutionApi.studentTemplate,
    onSuccess: (blob) => saveFile(blob, 'students-template.csv'),
  })

  const choose = (picked: File | undefined) => {
    preview.reset()
    save.reset()
    setFile(picked ?? null)
    if (picked) preview.mutate(picked)
  }

  const result = preview.data
  const busy = preview.isPending || save.isPending

  return (
    <Modal title="Import students from a CSV file" onClose={onClose} locked={save.isPending}
           footer={<>
             <Button variant="secondary" onClick={onClose} disabled={save.isPending}>Cancel</Button>
             <Button disabled={!file || !result || result.valid === 0 || busy} loading={save.isPending}
                     onClick={() => file && save.mutate(file)}>
               {result && result.valid > 0 ? `Import ${result.valid} students` : 'Import'}
             </Button>
           </>}>
      <div className="space-y-4">
        <p className="text-sm text-slate-600">
          Columns: <span className="font-mono text-xs">enrollmentNo, fullName, email, dateOfBirth, program, department, admissionYear, graduationYear</span>.
          Only enrollmentNo, fullName, program and admissionYear are required. Dates can be 2004-03-18 or 18-03-2004.
        </p>
        <button type="button" onClick={() => template.mutate()} disabled={template.isPending}
                className="inline-flex items-center gap-1.5 text-sm font-semibold text-navy-700 hover:underline">
          <Icon name="file" className="h-4 w-4" /> Download the template
        </button>

        <label className="flex cursor-pointer items-center gap-3 rounded-2xl border-2 border-dashed border-slate-300 px-4 py-5 transition hover:border-navy-400 hover:bg-slate-50">
          <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-navy-50 text-navy-700"><Icon name="upload" /></span>
          <span className="min-w-0 text-sm">
            <span className="block truncate font-semibold text-slate-900">{file ? file.name : 'Choose a CSV file'}</span>
            <span className="text-slate-500">We check it first and show you any problems before saving.</span>
          </span>
          <input type="file" accept=".csv,text/csv" className="sr-only" aria-label="Students CSV file" onChange={(e) => choose(e.target.files?.[0])} />
        </label>

        {preview.isPending && <p className="text-sm text-slate-500">Checking the file…</p>}
        {preview.isError && <Alert tone="error">{errorMessage(preview.error)}</Alert>}
        {save.isError && <Alert tone="error">{errorMessage(save.error)}</Alert>}
        {result && (
          <div className="space-y-3">
            <div className="grid grid-cols-3 gap-2 text-center">
              <div className="rounded-xl bg-slate-50 p-3"><p className="text-xl font-bold">{result.totalRows}</p><p className="text-xs text-slate-500">rows</p></div>
              <div className="rounded-xl bg-emerald-50 p-3"><p className="text-xl font-bold text-emerald-700">{result.valid}</p><p className="text-xs text-emerald-700">ready</p></div>
              <div className="rounded-xl bg-red-50 p-3"><p className="text-xl font-bold text-red-700">{result.failed}</p><p className="text-xs text-red-700">with problems</p></div>
            </div>
            {result.errors.length > 0 && (
              <div className="max-h-48 overflow-y-auto rounded-xl ring-1 ring-red-200">
                <ul className="divide-y divide-red-100 text-sm">
                  {result.errors.map((e) => (
                    <li key={e.row} className="px-3 py-2">
                      <span className="font-semibold text-red-800">Row {e.row}{e.enrollmentNo ? ` (${e.enrollmentNo})` : ''}:</span>{' '}
                      <span className="text-red-700">{e.messages.join('; ')}</span>
                    </li>
                  ))}
                </ul>
              </div>
            )}
            {result.failed > 0 && result.valid > 0 && (
              <p className="text-xs text-slate-500">Rows with problems are skipped. Fix them and import the file again later; rows already added are recognised.</p>
            )}
          </div>
        )}
      </div>
    </Modal>
  )
}

function ClaimCodeModal({ student, onClose }: { student: Student; onClose: () => void }) {
  const [code, setCode] = useState<ClaimCode | null>(null)
  const profile = useQuery({ queryKey: ['institution', 'profile'], queryFn: institutionApi.profile })
  const generate = useMutation({ mutationFn: () => institutionApi.claimCode(student.id), onSuccess: setCode })
  return (
    <Modal title={`Claim code for ${student.fullName}`} onClose={onClose}
           footer={code
             ? <Button onClick={onClose}>Done</Button>
             : <>
                 <Button variant="secondary" onClick={onClose}>Cancel</Button>
                 <Button loading={generate.isPending} onClick={() => generate.mutate()}>Generate code</Button>
               </>}>
      {!code && (
        <p className="text-sm text-slate-600">
          The student signs up on CredChain and enters this one-time code to see their certificates. Generating a new code
          cancels any earlier one.
        </p>
      )}
      {generate.isError && <div className="mt-3"><Alert tone="error">{errorMessage(generate.error)}</Alert></div>}
      {code && (
        <div className="space-y-3 text-center">
          <p className="text-sm text-slate-600">Give these to <strong>{student.fullName}</strong> privately. They enter all three after signing up:</p>
          <p className="rounded-2xl bg-navy-50 py-5 font-mono text-3xl font-bold tracking-[0.3em] text-navy-800" data-testid="claim-code">{code.claimCode}</p>
          <dl className="grid grid-cols-2 gap-2 text-left text-sm">
            <div className="rounded-xl bg-slate-50 p-3"><dt className="text-xs text-slate-500">College code</dt><dd className="font-semibold">{profile.data?.code ?? '…'}</dd></div>
            <div className="rounded-xl bg-slate-50 p-3"><dt className="text-xs text-slate-500">Enrollment number</dt><dd className="font-semibold">{code.enrollmentNo}</dd></div>
          </dl>
          <p className="text-xs text-slate-500">Valid until {formatDateTime(code.expiresAt)}. It works once.</p>
        </div>
      )}
    </Modal>
  )
}
