import { useId, type SelectHTMLAttributes } from 'react'

interface SelectFieldProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label: string
  options: { value: string; label: string }[]
  error?: string
}

/** Labelled dropdown styled like TextField. */
export function SelectField({ label, options, error, className = '', ...rest }: SelectFieldProps) {
  const id = useId()
  return (
    <div className={className}>
      <label htmlFor={id} className="block text-sm font-medium text-slate-700">{label}</label>
      <select id={id} {...rest} aria-invalid={error ? true : undefined}
              className="mt-1.5 block w-full rounded-xl border border-slate-300 bg-white px-3.5 py-2.5 text-sm outline-none transition
                         hover:border-slate-400 focus:border-navy-600 focus:ring-4 focus:ring-navy-100">
        {options.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
      </select>
      {error && <p className="mt-1 text-xs text-red-600">{error}</p>}
    </div>
  )
}
