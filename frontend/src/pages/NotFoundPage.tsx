import { Link } from 'react-router'

export function NotFoundPage() {
  return (
    <div className="mx-auto max-w-md px-4 py-24 text-center">
      <p className="text-sm font-semibold text-gold-500">404</p>
      <h1 className="mt-2 text-2xl font-bold text-slate-900">Page not found</h1>
      <Link to="/" className="mt-6 inline-block text-sm font-medium text-navy-700 hover:underline">Back to home</Link>
    </div>
  )
}
