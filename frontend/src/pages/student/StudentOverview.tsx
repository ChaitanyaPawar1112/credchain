import { useQuery } from '@tanstack/react-query'
import { studentApi } from '../../api/student'
import { Badge } from '../../components/Badge'
import { Icon } from '../../components/Icon'
import { LoadError, LoadingRows, Panel } from '../../components/Panel'
import { StatTile } from '../../components/StatTile'
import { LinkAccountCard } from './LinkAccountCard'
import { notLinked } from './MyCertificatesPage'

/** Top of the student overview: the linking form until the account is linked, then the college record and certificate count. */
export function StudentOverview() {
  const profile = useQuery({ queryKey: ['student', 'profile'], queryFn: studentApi.profile, retry: false })
  const certificates = useQuery({
    queryKey: ['student', 'certificates', 0],
    queryFn: () => studentApi.certificates(0),
    enabled: profile.isSuccess,
  })

  if (profile.isPending) return <div className="mt-8"><Panel><LoadingRows /></Panel></div>
  if (profile.isError) {
    return (
      <div className="mt-8">
        {notLinked(profile.error) ? <LinkAccountCard /> : <Panel><LoadError error={profile.error} onRetry={() => profile.refetch()} /></Panel>}
      </div>
    )
  }

  const { record, institutionName, institutionCode } = profile.data
  const all = certificates.data?.content
  const valid = all?.filter((c) => c.status === 'ISSUED').length

  return (
    <div className="mt-8 grid gap-4 lg:grid-cols-3">
      <section className="rounded-2xl bg-white p-6 shadow-sm ring-1 ring-slate-200 lg:col-span-2">
        <div className="flex items-start justify-between gap-3">
          <div className="flex items-start gap-4">
            <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-emerald-50 text-emerald-600">
              <Icon name="building" className="h-5 w-5" />
            </span>
            <div>
              <h2 className="font-bold text-slate-900">{institutionName}</h2>
              <p className="text-sm text-slate-500">College code {institutionCode}</p>
            </div>
          </div>
          <Badge tone="green" dot>Record connected</Badge>
        </div>
        <dl className="mt-5 grid gap-4 border-t border-slate-100 pt-5 text-sm sm:grid-cols-3">
          <div><dt className="text-slate-500">Enrollment number</dt><dd className="font-medium">{record.enrollmentNo}</dd></div>
          <div><dt className="text-slate-500">Programme</dt><dd className="font-medium">{record.program}</dd></div>
          <div><dt className="text-slate-500">Batch</dt>
            <dd className="font-medium">{record.admissionYear}{record.graduationYear ? `–${record.graduationYear}` : ''}</dd></div>
        </dl>
      </section>
      <StatTile label="My certificates" value={certificates.data?.totalElements} icon="certificate" tone="gold" to="/student/certificates"
                hint={valid !== undefined && all && all.length > valid ? `${valid} valid` : undefined} />
    </div>
  )
}
