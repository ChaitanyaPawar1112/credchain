import { Link } from 'react-router'

export function Logo({ light = false }: { light?: boolean }) {
  return (
    <Link to="/" className="flex items-center gap-2" aria-label="CredChain home">
      <svg viewBox="0 0 32 32" className="h-8 w-8" aria-hidden="true">
        <rect width="32" height="32" rx="7" fill={light ? '#ffffff' : '#1f3a5f'} />
        <path d="M9 17l4.5 4.5L23 11" fill="none" stroke="#e0b44c" strokeWidth="3.2" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
      <span className={`text-lg font-bold tracking-tight ${light ? 'text-white' : 'text-navy-700'}`}>CredChain</span>
    </Link>
  )
}
