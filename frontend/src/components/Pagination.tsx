import type { PageResponse } from '../api/types'

/** "Showing 21–40 of 75" with previous / next buttons. Hidden when everything fits on one page. */
export function Pagination({ data, onPage }: { data: PageResponse<unknown>; onPage: (page: number) => void }) {
  if (data.totalPages <= 1) return null
  const from = data.page * data.size + 1
  const to = data.page * data.size + data.content.length
  const button = 'rounded-lg px-3 py-1.5 text-sm font-medium ring-1 ring-slate-300 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40'
  return (
    <nav className="flex items-center justify-between gap-4 border-t border-slate-100 px-5 py-3 text-sm text-slate-600" aria-label="Pages">
      <span>Showing {from}–{to} of {data.totalElements}</span>
      <div className="flex gap-2">
        <button type="button" className={button} disabled={data.page === 0} onClick={() => onPage(data.page - 1)}>Previous</button>
        <button type="button" className={button} disabled={data.last} onClick={() => onPage(data.page + 1)}>Next</button>
      </div>
    </nav>
  )
}
