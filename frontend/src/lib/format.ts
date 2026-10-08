/** Display helpers shared by every page. Dates are shown in Indian English format. */

/** DEGREE -> Degree */
export const titleCase = (value: string) =>
  value.toLowerCase().replace(/_/g, ' ').replace(/^\w/, (c) => c.toUpperCase())

export function formatDate(value: string | null | undefined) {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value
    : date.toLocaleDateString('en-IN', { day: 'numeric', month: 'long', year: 'numeric' })
}

export function formatDateTime(value: string | null | undefined) {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value
    : date.toLocaleString('en-IN', { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' })
}

/** 0x1234…cdef, for long hashes and addresses. */
export const shorten = (value: string, keep = 10) =>
  value.length <= keep * 2 + 1 ? value : `${value.slice(0, keep)}…${value.slice(-keep + 2)}`

/** "3 minutes ago", "2 days ago"; the date itself for anything older than a month. */
export function timeAgo(value: string | null | undefined, now = Date.now()) {
  if (!value) return 'Never'
  const seconds = Math.round((now - new Date(value).getTime()) / 1000)
  if (Number.isNaN(seconds)) return value
  const ago = (n: number, unit: string) => `${n} ${unit}${n === 1 ? '' : 's'} ago`
  if (seconds < 60) return 'Just now'
  if (seconds < 3600) return ago(Math.floor(seconds / 60), 'minute')
  if (seconds < 86400) return ago(Math.floor(seconds / 3600), 'hour')
  if (seconds < 30 * 86400) return ago(Math.floor(seconds / 86400), 'day')
  return formatDate(value)
}
