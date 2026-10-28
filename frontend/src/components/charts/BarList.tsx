/** Horizontal bars with the label and number written out: for "how many of each" breakdowns. */
export function BarList({ title, rows, color = '#2a78d6' }: {
  title: string
  rows: { label: string; value: number; hint?: string }[]
  color?: string
}) {
  const max = Math.max(1, ...rows.map((r) => r.value))
  const total = rows.reduce((a, r) => a + r.value, 0)
  return (
    <section className="rounded-2xl bg-white p-5 shadow-sm ring-1 ring-slate-200" aria-label={title}>
      <h2 className="font-semibold text-slate-900">{title}</h2>
      <ul className="mt-4 space-y-3">
        {rows.map((r) => (
          <li key={r.label} title={r.hint}>
            <div className="flex items-baseline justify-between text-sm">
              <span className="text-slate-700">{r.label}</span>
              <span className="tabular-nums text-slate-900">
                <span className="font-semibold">{r.value}</span>
                {total > 0 && <span className="ml-1.5 text-xs text-slate-400">{Math.round((r.value / total) * 100)}%</span>}
              </span>
            </div>
            <div className="mt-1.5 h-2 rounded-full bg-slate-100">
              {r.value > 0 && (
                <div className="h-2 rounded-full" style={{ width: `${(r.value / max) * 100}%`, background: color }} />
              )}
            </div>
          </li>
        ))}
      </ul>
    </section>
  )
}
