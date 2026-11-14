import { CircleCheck, CircleX, TriangleAlert, Loader2 } from 'lucide-react'

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

/** Every run is labelled, so simulated results are never mistaken for real ones. */
export function SourceTag({ source }) {
  return source === 'live' ? (
    <span className="tag tag-live" title="Real answers from the assistant under test">Live</span>
  ) : (
    <span className="tag tag-sample" title="Simulated sample data, not real findings">Sample</span>
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
