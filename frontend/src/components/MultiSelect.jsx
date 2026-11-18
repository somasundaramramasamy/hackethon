import { useEffect, useId, useRef, useState } from 'react'
import { ChevronDown } from 'lucide-react'

/**
 * A dropdown with a checkbox per option, styled like the other selects. The closed box lists what is
 * chosen. Closes on a click outside or Escape.
 */
export default function MultiSelect({ options, value, onChange, placeholder = 'Choose…', label, allLabel = '' }) {
  const [open, setOpen] = useState(false)
  const ref = useRef(null)
  const listId = useId()

  useEffect(() => {
    if (!open) return undefined
    const close = (e) => {
      if (!ref.current?.contains(e.target)) setOpen(false)
    }
    const escape = (e) => e.key === 'Escape' && setOpen(false)
    document.addEventListener('mousedown', close)
    document.addEventListener('keydown', escape)
    return () => {
      document.removeEventListener('mousedown', close)
      document.removeEventListener('keydown', escape)
    }
  }, [open])

  const toggle = (v) => onChange(value.includes(v) ? value.filter((x) => x !== v) : [...value, v])
  const chosen = options.filter((o) => value.includes(o.value))
  const summary =
    chosen.length === 0
      ? placeholder
      : chosen.length === options.length
        ? `All ${options.length} ${allLabel}`
        : chosen.map((o) => o.label).join(', ')

  return (
    <div className="multi-select" ref={ref}>
      <button
        type="button"
        className="select-like"
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={listId}
        aria-label={label ? `${label}: ${summary}` : undefined}
        onClick={() => setOpen((o) => !o)}
      >
        <span className={chosen.length ? '' : 'muted'}>{summary}</span>
        <ChevronDown size={16} />
      </button>
      {open && (
        <div className="multi-select-menu" id={listId} role="listbox" aria-multiselectable="true">
          {options.map((o) => (
            <label key={o.value} className="multi-select-option" role="option" aria-selected={value.includes(o.value)}>
              <input type="checkbox" checked={value.includes(o.value)} onChange={() => toggle(o.value)} />
              {o.label}
            </label>
          ))}
        </div>
      )}
    </div>
  )
}
