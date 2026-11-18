import { Link, useSearchParams } from 'react-router-dom'
import { ClipboardCheck, Languages, LayoutDashboard, Scale, Stethoscope } from 'lucide-react'
import { getAgreement, getReviewQueue } from '../api/client'
import { useLoad } from '../hooks/useLoad'
import { useIdentity } from '../state/identity'
import { AuditTag, Card, ErrorState, LangChip, Loading, Progress, Verdict } from '../components/ui'
import { formatDate, percent } from '../utils/format'

const LANGUAGE_KEY = 'kavasam.reviewLanguage'

function savedLanguage() {
  try {
    return localStorage.getItem(LANGUAGE_KEY) || ''
  } catch {
    return ''
  }
}

function saveLanguage(language) {
  try {
    if (language) localStorage.setItem(LANGUAGE_KEY, language)
    else localStorage.removeItem(LANGUAGE_KEY)
  } catch {
    // Storage can be unavailable (private window); the filter still works for this visit.
  }
}

function roleLanguage(identity) {
  if (identity?.role === 'reviewer') return identity.language ?? ''
  if (identity?.role === 'clinician') return 'EN'
  return ''
}

export default function ReviewQueue() {
  const [params, setParams] = useSearchParams()
  const run = params.get('run') || ''
  const { identity } = useIdentity()
  // A shared link (?language=HI) wins, then the last choice in this browser, then the person's role:
  // a reviewer's own language, or English for a clinician.
  const language = params.has('language') ? params.get('language') : savedLanguage() || roleLanguage(identity)
  const queue = useLoad(() => getReviewQueue({ run, language }), [run, language])
  const agreement = useLoad(getAgreement, [])

  const choose = (changes) => {
    const next = new URLSearchParams(params)
    Object.entries(changes).forEach(([key, value]) => (value ? next.set(key, value) : next.delete(key)))
    if ('language' in changes) {
      saveLanguage(changes.language)
      if (!changes.language) next.set('language', '')
    }
    setParams(next, { replace: true })
  }

  const page = queue.data
  return (
    <div className="page">
      {page?.runCode && <ReviewDashboard page={page} agreement={agreement} onLanguage={(l) => choose({ language: l })} />}

      <Card title="Waiting for review" icon={ClipboardCheck}>
        <p className="muted card-intro">
          Answers the automated checks could not decide, plus a random spot-check of 20% of failures and 10% of passes
          in each language, so the judge&apos;s mistakes in either direction are measured. Native speakers review
          answers in their own language; clinicians review English answers for medical safety. Open one, read it, and
          mark it safe or unsafe. Any other answer can be reviewed from its own page.
        </p>
        {queue.loading && !page ? (
          <Loading />
        ) : queue.error ? (
          <ErrorState error={queue.error} />
        ) : (
          <>
            <QueueFilters page={page} language={language} onChange={choose} />
            {page.items.length === 0 ? (
              <p className="muted">
                {page.runCode
                  ? 'Nothing waiting here. Every answer that needs a person has been reviewed.'
                  : 'No finished runs yet.'}
              </p>
            ) : (
              <QueueTable items={page.items} loading={queue.loading} />
            )}
          </>
        )}
      </Card>
    </div>
  )
}

function QueueFilters({ page, language, onChange }) {
  const total = page.languages.reduce((sum, l) => sum + l.waiting, 0)
  return (
    <div className="queue-filters">
      <label className="field queue-run">
        <span className="field-label">Run</span>
        <select value={page.runCode ?? ''} onChange={(e) => onChange({ run: e.target.value })}>
          {page.runs.map((r) => (
            <option key={r.code} value={r.code}>
              {r.name} · {r.code} · {r.waiting} waiting
            </option>
          ))}
        </select>
      </label>
      <div className="lang-filter" role="group" aria-label="Language">
        <button type="button" className={!language ? 'active' : ''} aria-pressed={!language} onClick={() => onChange({ language: '' })}>
          All <span className="count">{total}</span>
        </button>
        {page.languages.map((l) => (
          <button
            key={l.language}
            type="button"
            className={language?.toUpperCase() === l.language ? 'active' : ''}
            aria-pressed={language?.toUpperCase() === l.language}
            onClick={() => onChange({ language: l.language })}
          >
            {l.languageName} <span className="count">{l.waiting}</span>
          </button>
        ))}
      </div>
    </div>
  )
}

function QueueTable({ items, loading }) {
  return (
    <div className={`table-wrap${loading ? ' is-loading' : ''}`} aria-busy={loading}>
      <table className="table">
        <thead>
          <tr>
            <th>Answer</th>
            <th>Language</th>
            <th>Automated verdict</th>
            <th>Why</th>
            <th>Asked</th>
          </tr>
        </thead>
        <tbody>
          {items.map((item) => (
            <tr key={item.id}>
              <td>
                <Link className="run-id queue-answer" to={`/results/${item.id}`}>
                  {item.scenarioCode} {item.scenarioTitle}
                </Link>
                <div className="muted small">attempt {item.attempt}</div>
              </td>
              <td>
                <LangChip code={item.language} /> {item.languageName}
              </td>
              <td className="nowrap">
                <Verdict value={item.verdict} />
                {item.reason === 'spot_check' && (
                  <div className="queue-reason">
                    <AuditTag />
                  </div>
                )}
              </td>
              <td className="small why">{item.detail}</td>
              <td className="nowrap small muted">{formatDate(item.askedAt)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

/**
 * What is left to review in the chosen run, and whose job it is: English answers go to a clinician,
 * the others to a native speaker of the language. Language counts set the filter below.
 */
function ReviewDashboard({ page, agreement, onLanguage }) {
  const count = (code) => page.languages.find((l) => l.language === code)?.waiting ?? 0
  const waiting = page.languages.reduce((sum, l) => sum + l.waiting, 0)
  const english = count('EN')
  const native = page.languages.filter((l) => l.language !== 'EN')
  const picked = waiting + page.reviewed
  const runName = page.runs.find((r) => r.code === page.runCode)?.name ?? page.runCode
  return (
    <Card title="Review dashboard" icon={LayoutDashboard} action={<span className="muted small">{runName}</span>}>
      <div className="review-stats">
        <div className="review-stat">
          <span className="review-stat-label">Waiting for review</span>
          <span className="review-stat-value">{waiting}</span>
          <Progress done={page.reviewed} total={picked} />
          <span className="muted small">
            {page.reviewed} of {picked} picked answers reviewed
          </span>
        </div>

        <button type="button" className="review-stat clickable" onClick={() => onLanguage('EN')}>
          <span className="review-stat-label">
            <Stethoscope size={15} /> For a clinician
          </span>
          <span className="review-stat-value">{english}</span>
          <span className="muted small">English answers, checked for medical safety</span>
        </button>

        <div className="review-stat">
          <span className="review-stat-label">
            <Languages size={15} /> For native speakers
          </span>
          <span className="review-stat-value">{waiting - english}</span>
          <span className="review-langs">
            {native.map((l) => (
              <button key={l.language} type="button" onClick={() => onLanguage(l.language)}>
                {l.languageName} <b>{l.waiting}</b>
              </button>
            ))}
          </span>
        </div>

        <div className="review-stat">
          <span className="review-stat-label">Why they are here</span>
          <span className="review-split">
            <span>
              <b>{page.unsure}</b> unsure
            </span>
            <span>
              <b>{page.spotChecks}</b> spot-checks
            </span>
          </span>
          <span className="muted small">Unsure: the checks could not decide. Spot-checks: picked at random.</span>
        </div>

        <div className="review-stat">
          <span className="review-stat-label">
            <Scale size={15} /> Judge agrees with people
          </span>
          <Agreement agreement={agreement} />
        </div>
      </div>
    </Card>
  )
}

function Agreement({ agreement }) {
  if (agreement.loading && !agreement.data) return <Loading />
  if (agreement.error) return <span className="muted small">Could not load the agreement.</span>
  const { overall } = agreement.data
  if (!overall.reviewed) {
    return (
      <>
        <span className="review-stat-value muted">—</span>
        <span className="muted small">Appears after the first reviews, across all tests</span>
      </>
    )
  }
  return (
    <>
      <span className="review-stat-value">{percent(overall.rate)}</span>
      <span className="muted small">
        {overall.agreed} of {overall.reviewed} reviewed, all tests · missed failures{' '}
        <b className={overall.missedFailures ? 'bad' : ''}>{overall.missedFailures}</b> · false alarms{' '}
        <b>{overall.falseAlarms}</b>
      </span>
    </>
  )
}
