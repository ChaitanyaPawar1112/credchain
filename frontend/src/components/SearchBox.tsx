import { useEffect, useState } from 'react'
import { Icon } from './Icon'

/** Search input that reports its value after the person stops typing for a moment. */
export function SearchBox({ placeholder, onSearch, delay = 300 }: { placeholder: string; onSearch: (value: string) => void; delay?: number }) {
  const [value, setValue] = useState('')
  useEffect(() => {
    const timer = setTimeout(() => onSearch(value.trim()), delay)
    return () => clearTimeout(timer)
  }, [value, delay, onSearch])
  return (
    <div className="relative w-full sm:max-w-xs">
      <span className="pointer-events-none absolute inset-y-0 left-3 flex items-center text-slate-400"><Icon name="search" className="h-4 w-4" /></span>
      <input type="search" value={value} onChange={(e) => setValue(e.target.value)} placeholder={placeholder} aria-label={placeholder}
             className="w-full rounded-xl border border-slate-300 bg-white py-2.5 pl-9 pr-3 text-sm outline-none transition
                        focus:border-navy-600 focus:ring-4 focus:ring-navy-100" />
    </div>
  )
}
