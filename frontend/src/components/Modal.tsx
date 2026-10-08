import { useEffect, useId, type ReactNode } from 'react'
import { Icon } from './Icon'

/** Centered dialog. Escape or the backdrop closes it unless `locked` (e.g. while saving). */
export function Modal({ title, onClose, children, footer, locked = false }: {
  title: string
  onClose: () => void
  children: ReactNode
  footer?: ReactNode
  locked?: boolean
}) {
  const titleId = useId()
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !locked) onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose, locked])

  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center p-4 sm:items-center">
      <div className="absolute inset-0 bg-navy-950/60 backdrop-blur-sm" onClick={() => !locked && onClose()} />
      <div role="dialog" aria-modal="true" aria-labelledby={titleId}
           className="animate-fade-up relative w-full max-w-lg rounded-3xl bg-white shadow-2xl ring-1 ring-slate-200">
        <div className="flex items-start justify-between gap-4 px-6 pt-6">
          <h2 id={titleId} className="text-lg font-bold text-slate-900">{title}</h2>
          <button type="button" onClick={onClose} disabled={locked} aria-label="Close"
                  className="rounded-lg p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-700 disabled:opacity-40">
            <Icon name="x" />
          </button>
        </div>
        <div className="px-6 py-4">{children}</div>
        {footer && <div className="flex flex-wrap justify-end gap-3 rounded-b-3xl bg-slate-50 px-6 py-4">{footer}</div>}
      </div>
    </div>
  )
}
