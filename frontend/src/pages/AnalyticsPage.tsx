import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'
import { analyticsApi, type AnalyticsDays } from '../api/analytics'
import { BarList } from '../components/charts/BarList'
import { DailyBars, SERIES_COLORS } from '../components/charts/DailyBars'
import { PageHeader } from '../components/PageHeader'
import { EmptyState, FilterTabs, LoadError, LoadingRows, Panel, td, th } from '../components/Panel'
import { StatTile } from '../components/StatTile'
import { METHOD_LOOK, RESULT_LOOK } from './admin/labels'

const SCOPES = {
  admin: {
    fetch: analyticsApi.platform,
    description: 'How CredChain is used: certificates issued, public checks and fake attempts across every college.',
    verificationsPage: '/admin/verifications',
  },
  institution: {
    fetch: analyticsApi.institution,
    description: 'Your college at a glance: certificates issued and how often employers check them.',
    verificationsPage: '/institution/verifications',
  },
}

const PERIODS: { value: string; label: string }[] = [
  { value: '7', label: 'Last 7 days' },
  { value: '30', label: 'Last 30 days' },
  { value: '90', label: 'Last 90 days' },
]

/** Analytics dashboard: super admin sees the platform, a college admin their own college. */
export function AnalyticsPage({ scope = 'admin' }: { scope?: keyof typeof SCOPES }) {
  const source = SCOPES[scope]
  const [days, setDays] = useState<AnalyticsDays>(30)
  const query = useQuery({
    queryKey: [scope, 'analytics', days],
    queryFn: () => source.fetch(days),
    placeholderData: (previous) => previous,
  })
  const a = query.data
  const t = a?.totals

  return (
    <div className="animate-fade-up mx-auto max-w-6xl space-y-6">
      <PageHeader icon="chart" title="Analytics" description={source.description} />

      <FilterTabs value={String(days)} onChange={(v) => setDays(Number(v) as AnalyticsDays)} options={PERIODS} />

      {query.isError && !a && <Panel><LoadError error={query.error} onRetry={() => query.refetch()} /></Panel>}
      {query.isPending && <Panel><LoadingRows /></Panel>}

      {a && t && (
        <>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {t.colleges !== null && (
              <StatTile label="Approved colleges" value={t.colleges} icon="building" to="/admin/institutions"
                        hint={t.pendingApplications ? `${t.pendingApplications} waiting for review` : 'No applications waiting'} />
            )}
            <StatTile label="Students" value={t.students} icon="student" tone="slate"
                      hint={`${t.studentsWithAccount} have created an account`} />
            <StatTile label="Certificates issued" value={t.certificatesIssued} icon="certificate" tone="green"
                      hint={t.certificatesRevoked ? `${t.certificatesRevoked} revoked` : 'None revoked'} />
            <StatTile label="Checks in this period" value={t.checksInPeriod} icon="shield" tone="gold"
                      to={source.verificationsPage}
                      hint={t.fakeChecksInPeriod ? `${t.fakeChecksInPeriod} were fake copies` : 'No fake copies'} />
          </div>

          <DailyBars
            title="Certificates issued per day"
            series={[{ label: 'Issued', color: SERIES_COLORS[0] }]}
            days={a.daily.map((d) => ({ date: d.date, values: [d.issued] }))}
            emptyText="No certificates were issued in this period."
          />

          <DailyBars
            title="Checks per day"
            series={[
              { label: 'Genuine', color: SERIES_COLORS[0] },
              { label: 'Revoked or expired', color: SERIES_COLORS[1] },
              { label: 'Fake or not found', color: SERIES_COLORS[2] },
            ]}
            days={a.daily.map((d) => ({ date: d.date, values: [d.valid, d.checks - d.valid - d.failed, d.failed] }))}
            emptyText="Nobody checked a certificate in this period."
          />

          <div className="grid gap-6 md:grid-cols-2">
            <BarList title="What checks answered" rows={(Object.keys(RESULT_LOOK) as (keyof typeof RESULT_LOOK)[])
              .map((r) => ({ label: RESULT_LOOK[r].label, value: a.checksByResult[r] ?? 0 }))} />
            <BarList title="How people checked" rows={(Object.keys(METHOD_LOOK) as (keyof typeof METHOD_LOOK)[])
              .map((m) => ({ label: METHOD_LOOK[m].label, value: a.checksByMethod[m] ?? 0 }))} />
          </div>

          {a.topColleges && (
            <Panel>
              <h2 className="px-5 pt-5 font-semibold text-slate-900">Most active colleges</h2>
              {a.topColleges.length === 0 ? (
                <EmptyState icon="building" title="No college activity in this period" />
              ) : (
                <div className="mt-3 overflow-x-auto">
                  <table className="w-full">
                    <thead className="bg-slate-50">
                      <tr>
                        <th className={th}>College</th>
                        <th className={`${th} text-right`}>Issued</th>
                        <th className={`${th} text-right`}>Checks</th>
                        <th className={`${th} text-right`}>Fake copies</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                      {a.topColleges.map((c) => (
                        <tr key={c.institutionId}>
                          <td className={td}>
                            <Link to={`/admin/institutions/${c.institutionId}`} className="font-medium text-navy-700 hover:underline">
                              {c.name}
                            </Link>
                            <span className="ml-2 text-xs text-slate-400">{c.code}</span>
                          </td>
                          <td className={`${td} text-right tabular-nums`}>{c.issued}</td>
                          <td className={`${td} text-right tabular-nums`}>{c.checks}</td>
                          <td className={`${td} text-right tabular-nums ${c.fakeChecks ? 'font-semibold text-red-700' : ''}`}>
                            {c.fakeChecks}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </Panel>
          )}
        </>
      )}
    </div>
  )
}
