import { LangChip } from './ui'

/**
 * One stacked bar per language: passed, failed, needs review and errors. The gap between
 * English and the other languages is what Kavasam exists to show, so English comes first.
 */
export default function LanguageBars({ languages }) {
  if (!languages?.length) return <p className="muted">No results yet.</p>
  return (
    <div className="lang-bars">
      {languages.map(({ language, languageName, counts }) => {
        const checked = counts.pass + counts.fail + counts.needsReview
        const rate = checked ? Math.round((100 * counts.pass) / checked) : null
        const pct = (n) => `${counts.total ? (100 * n) / counts.total : 0}%`
        return (
          <div key={language} className="lang-bar-row">
            <div className="lang-bar-label">
              <LangChip code={language} /> {languageName}
            </div>
            <div
              className="lang-bar"
              role="img"
              aria-label={`${languageName}: ${counts.pass} passed, ${counts.fail} failed, ${counts.needsReview} need review, ${counts.error} errors`}
            >
              <span className="seg seg-pass" style={{ width: pct(counts.pass) }} />
              <span className="seg seg-review" style={{ width: pct(counts.needsReview) }} />
              <span className="seg seg-fail" style={{ width: pct(counts.fail) }} />
              <span className="seg seg-error" style={{ width: pct(counts.error) }} />
            </div>
            <div className="lang-bar-rate">{rate == null ? '—' : `${rate}%`}</div>
            <div className="lang-bar-counts muted small">
              {counts.pass} pass · {counts.fail} fail · {counts.needsReview} review
              {counts.error > 0 && ` · ${counts.error} error`}
            </div>
          </div>
        )
      })}
      <div className="lang-bar-legend small muted">
        <span>
          <i className="seg-pass" /> Pass
        </span>
        <span>
          <i className="seg-review" /> Needs review
        </span>
        <span>
          <i className="seg-fail" /> Fail
        </span>
        <span>
          <i className="seg-error" /> Error
        </span>
        <span>Rate = passed ÷ checked</span>
      </div>
    </div>
  )
}
