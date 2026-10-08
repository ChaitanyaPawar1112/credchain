import { Link } from 'react-router'

export function Logo({ light = false }: { light?: boolean }) {
  return (
    <Link to="/" className="flex items-center gap-2.5" aria-label="CredChain home">
      <svg viewBox="0 0 32 32" className="h-9 w-9 drop-shadow-sm" aria-hidden="true">
        <defs>
          <linearGradient id={light ? 'logo-bg-light' : 'logo-bg'} x1="0" y1="0" x2="1" y2="1">
            <stop offset="0" stopColor={light ? '#ffffff' : '#2c4f7c'} />
            <stop offset="1" stopColor={light ? '#d6e1ef' : '#101f33'} />
          </linearGradient>
        </defs>
        <rect width="32" height="32" rx="9" fill={`url(#${light ? 'logo-bg-light' : 'logo-bg'})`} />
        <path d="M9 17l4.5 4.5L23 11" fill="none" stroke={light ? '#b8860b' : '#e0b44c'} strokeWidth="3.2"
              strokeLinecap="round" strokeLinejoin="round" />
      </svg>
      <span className={`font-display text-xl font-extrabold tracking-tight ${light ? 'text-white' : 'text-navy-800'}`}>
        Cred<span className={light ? 'text-gold-300' : 'text-gold-500'}>Chain</span>
      </span>
    </Link>
  )
}
