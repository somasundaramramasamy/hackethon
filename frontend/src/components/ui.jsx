import { CircleCheck, TriangleAlert, Loader2 } from 'lucide-react'

export function LangChips({ languages }) {
  return (
    <span className="lang-chips">
      {languages.map((l) => (
        <span key={l} className={`lang-chip lang-${l.toLowerCase()}`}>
          {l}
        </span>
      ))}
    </span>
  )
}

export function StatusPill({ status }) {
  if (status === 'needs_review') {
    return (
      <span className="pill pill-warning">
        <TriangleAlert size={14} /> Needs Review
      </span>
    )
  }
  return (
    <span className="pill pill-success">
      <CircleCheck size={14} /> Completed
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

export function Loading() {
  return (
    <div className="loading">
      <Loader2 size={22} className="spin" /> Loading…
    </div>
  )
}

export function ErrorState({ message }) {
  return <div className="error-state">Something went wrong: {message}</div>
}
