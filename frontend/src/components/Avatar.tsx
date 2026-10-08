/** Round badge with the person's initials. */
export function Avatar({ name, size = 'md' }: { name: string; size?: 'md' | 'lg' }) {
  const initials = name.split(/\s+/).filter(Boolean).slice(0, 2).map((part) => part[0]!.toUpperCase()).join('')
  const sizes = size === 'lg' ? 'h-16 w-16 text-xl' : 'h-10 w-10 text-sm'
  return (
    <span className={`flex shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-gold-300 to-gold-500
                      font-bold text-navy-900 shadow-md ${sizes}`} aria-hidden="true">
      {initials || '?'}
    </span>
  )
}
