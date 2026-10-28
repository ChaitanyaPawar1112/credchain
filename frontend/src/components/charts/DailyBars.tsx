import { useState } from 'react'
import { th, td } from '../Panel'

/** Categorical slots of the chart palette (blue, aqua, orange): validated together for colour-blind readers. */
export const SERIES_COLORS = ['#2a78d6', '#1baf7a', '#eb6834'] as const

export interface BarSeries {
  label: string
  color: string
}

export interface BarDay {
  date: string
  /** One value per series, bottom segment first. */
  values: number[]
}

const shortDate = (iso: string) =>
  new Date(`${iso}T00:00:00`).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' })

/** 0..max rounded up to a friendly number, with 4 gridline steps. */
function niceMax(max: number) {
  if (max <= 4) return 4
  const step = 10 ** Math.floor(Math.log10(max / 4))
  const nice = [1, 2, 2.5, 5, 10].map((m) => m * step * 4).find((v) => v >= max)
  return nice ?? max
}

/**
 * A day-by-day column chart, stacked when there are several series. Hover or focus a day for its numbers;
 * "Show as table" gives the same numbers as text (for screen readers and the light aqua colour).
 */
export function DailyBars({ title, series, days, emptyText }: {
  title: string
  series: BarSeries[]
  days: BarDay[]
  emptyText: string
}) {
  const [active, setActive] = useState<number | null>(null)
  const [asTable, setAsTable] = useState(false)
  const totals = days.map((d) => d.values.reduce((a, b) => a + b, 0))
  const top = niceMax(Math.max(0, ...totals))
  const empty = totals.every((t) => t === 0)
  const ticks = [top, (top * 3) / 4, top / 2, top / 4, 0]
  const labelEvery = Math.ceil(days.length / 5)

  return (
    <section className="rounded-2xl bg-white p-5 shadow-sm ring-1 ring-slate-200" aria-label={title}>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="font-semibold text-slate-900">{title}</h2>
        <div className="flex items-center gap-4">
          {series.length > 1 && (
            <ul className="flex flex-wrap gap-3 text-xs text-slate-600" aria-label="Legend">
              {series.map((s) => (
                <li key={s.label} className="flex items-center gap-1.5">
                  <span className="h-2.5 w-2.5 rounded-sm" style={{ background: s.color }} /> {s.label}
                </li>
              ))}
            </ul>
          )}
          <button type="button" onClick={() => setAsTable((t) => !t)}
                  className="whitespace-nowrap text-xs font-semibold text-navy-700 hover:underline">
            {asTable ? 'Show as chart' : 'Show as table'}
          </button>
        </div>
      </div>

      {asTable ? (
        <div className="mt-4 max-h-80 overflow-y-auto">
          <table className="w-full">
            <thead className="sticky top-0 bg-white">
              <tr>
                <th className={th}>Day</th>
                {series.map((s) => <th key={s.label} className={`${th} text-right`}>{s.label}</th>)}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {[...days].reverse().map((d) => (
                <tr key={d.date}>
                  <td className={td}>{shortDate(d.date)}</td>
                  {d.values.map((v, i) => <td key={i} className={`${td} text-right tabular-nums`}>{v}</td>)}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="mt-5 flex gap-2">
          {/* y axis */}
          <div className="flex h-52 flex-col justify-between pb-0 text-right text-[11px] tabular-nums text-slate-400" aria-hidden>
            {ticks.map((t, i) => <span key={i} className="-translate-y-1/2 first:translate-y-0 last:translate-y-0">{Math.round(t)}</span>)}
          </div>
          <div className="min-w-0 flex-1">
            <div className="relative h-52" onMouseLeave={() => setActive(null)}>
              {/* gridlines */}
              {ticks.map((_, i) => (
                <div key={i} className={`absolute inset-x-0 border-t ${i === ticks.length - 1 ? 'border-slate-300' : 'border-slate-100'}`}
                     style={{ top: `${(i / (ticks.length - 1)) * 100}%` }} />
              ))}
              {empty && (
                <p className="absolute inset-0 flex items-center justify-center text-sm text-slate-400">{emptyText}</p>
              )}
              <div className="absolute inset-0 flex items-end gap-[2px]">
                {days.map((d, i) => (
                  <div key={d.date} tabIndex={0} role="img"
                       aria-label={`${shortDate(d.date)}: ${series.map((s, j) => `${s.label} ${d.values[j]}`).join(', ')}`}
                       onMouseEnter={() => setActive(i)} onFocus={() => setActive(i)} onBlur={() => setActive(null)}
                       className={`flex h-full min-w-0 flex-1 cursor-default flex-col-reverse gap-[2px] rounded-t outline-none
                                   ${active === i ? 'bg-slate-100/70' : ''} focus-visible:ring-2 focus-visible:ring-navy-400`}>
                    {d.values.map((v, j) => v > 0 && (
                      <div key={j} style={{ height: `calc(${(v / top) * 100}% - 2px)`, background: series[j]!.color }}
                           className={`min-h-[2px] w-full ${d.values.slice(j + 1).every((x) => x === 0) ? 'rounded-t' : ''}`} />
                    ))}
                  </div>
                ))}
              </div>
              {active !== null && days[active] && (
                <div role="status"
                     className="pointer-events-none absolute top-1 z-10 -translate-x-1/2 rounded-lg bg-slate-900/95
                                px-3 py-2 text-xs whitespace-nowrap text-white shadow-lg"
                     style={{ left: `${Math.min(90, Math.max(10, ((active + 0.5) / days.length) * 100))}%` }}>
                  <p className="font-semibold">{shortDate(days[active].date)}</p>
                  {series.map((s, j) => (
                    <p key={s.label} className="mt-0.5 flex items-center gap-1.5">
                      <span className="h-2 w-2 rounded-sm" style={{ background: s.color }} />
                      {s.label}: <span className="font-semibold tabular-nums">{days[active]!.values[j]}</span>
                    </p>
                  ))}
                </div>
              )}
            </div>
            {/* x axis: a few dates */}
            <div className="relative mt-2 h-4 text-[11px] text-slate-400" aria-hidden>
              {days.map((d, i) => {
                const last = i === days.length - 1
                // every few days, plus the last one (skipping a label that would touch it)
                if (!last && (i % labelEvery !== 0 || days.length - 1 - i < labelEvery)) return null
                return (
                  <span key={d.date} className="absolute -translate-x-1/2 whitespace-nowrap"
                        style={{ left: `${((i + 0.5) / days.length) * 100}%` }}>
                    {shortDate(d.date)}
                  </span>
                )
              })}
            </div>
          </div>
        </div>
      )}
    </section>
  )
}
