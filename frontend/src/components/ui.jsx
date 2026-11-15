import { CircleCheck, CircleX, TriangleAlert, Loader2, Info, CircleDashed, Eye } from 'lucide-react'

const VERDICTS = {
  pass: { label: 'Pass', className: 'pill-success', Icon: CircleCheck },
  fail: { label: 'Fail', className: 'pill-danger', Icon: CircleX },
  needs_review: { label: 'Needs review', className: 'pill-warning', Icon: TriangleAlert },
  error: { label: 'Error', className: 'pill-muted', Icon: CircleDashed },
  pending: { label: 'Pending', className: 'pill-muted', Icon: CircleDashed },
}

/** A verdict as a coloured pill: pass, fail, needs_review, error or pending. */
export function Verdict({ value, label }) {
  const v = VERDICTS[value] ?? VERDICTS.pending
  return (
    <span className={`pill ${v.className}`}>
      <v.Icon size={14} /> {label ?? v.label}
    </span>
  )
}

export function StatusPill({ status }) {
  if (status === 'running') {
    return (
      <span className="pill pill-info">
        <Loader2 size={14} className="spin" /> Running
      </span>
    )
  }
  if (status === 'failed') {
    return (
      <span className="pill pill-danger">
        <CircleX size={14} /> Failed
      </span>
    )
  }
  return (
    <span className="pill pill-success">
      <CircleCheck size={14} /> Completed
    </span>
  )
}

export function LangChip({ code }) {
  return <span className={`lang-chip lang-${code.toLowerCase()}`}>{code}</span>
}

/** Marks a passed result that was picked at random for a human spot-check. */
export function AuditTag() {
  return (
    <span className="tag tag-purple" title="A passed answer picked at random for a human spot-check">
      <Eye size={12} /> Audit
    </span>
  )
}

export function Card({ title, icon: Icon, action, className = '', children }) {
  return (
    <section className={`card ${className}`}>
      {(title || action) && (
        <header className="card-head">
          {Icon && <Icon size={20} className="card-icon" />}
          <h3>{title}</h3>
          {action && <div className="card-action">{action}</div>}
        </header>
      )}
      {children}
    </section>
  )
}

export function Notice({ kind = 'info', children }) {
  const Icon = kind === 'error' ? CircleX : kind === 'warning' ? TriangleAlert : Info
  return (
    <div className={`notice notice-${kind}`} role={kind === 'error' ? 'alert' : 'status'}>
      <Icon size={18} />
      <div>{children}</div>
    </div>
  )
}

export function Loading() {
  return (
    <div className="loading">
      <Loader2 size={22} className="spin" /> Loading…
    </div>
  )
}

export function ErrorState({ error }) {
  return <Notice kind="error">{error?.message ?? String(error)}</Notice>
}

export function Progress({ done, total }) {
  const pct = total ? Math.round((100 * done) / total) : 0
  return (
    <div className="progress" aria-label={`${done} of ${total} done`}>
      <div className="progress-fill" style={{ width: `${pct}%` }} />
    </div>
  )
}

/**
 * Renders model output as text: keeps line breaks, turns **bold** into <strong> and leading
 * "* " / "- " into bullets. Never injects HTML, so model output cannot run scripts.
 */
export function FormattedText({ text, lang }) {
  if (!text) return null
  return (
    <div className="formatted" lang={lang}>
      {text.split('\n').map((line, i) => {
        const bullet = /^\s*[*-]\s+/.test(line)
        const content = bullet ? line.replace(/^\s*[*-]\s+/, '') : line
        const parts = content.split('**').map((part, j) => (j % 2 === 1 ? <strong key={j}>{part}</strong> : part))
        if (!content.trim()) return <div key={i} className="formatted-gap" />
        return (
          <p key={i} className={bullet ? 'formatted-bullet' : undefined}>
            {parts}
          </p>
        )
      })}
    </div>
  )
}

/** Before/after count for a fix, e.g. "3 fixed". */
export function ChangeCount({ tone, value, label }) {
  return (
    <div className={`change-count change-${tone}`}>
      <span className="change-value">{value}</span>
      <span className="change-label">{label}</span>
    </div>
  )
}
