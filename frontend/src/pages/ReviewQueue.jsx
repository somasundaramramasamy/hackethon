import { Link, useSearchParams } from 'react-router-dom'
import { ClipboardCheck, Scale } from 'lucide-react'
import { getAgreement, getReviewQueue } from '../api/client'
import { useLoad } from '../hooks/useLoad'
import { useIdentity } from '../state/identity'
import { AuditTag, Card, ErrorState, LangChip, Loading, Verdict } from '../components/ui'
import { formatDate, percent, shortModel } from '../utils/format'

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
      <Card title="Waiting for a native speaker" icon={ClipboardCheck}>
        <p className="muted card-intro">
          Answers the automated checks could not decide, plus a random spot-check of 20% of failures and 10% of passes
          in each language, so the judge&apos;s mistakes in either direction are measured. Open one, read it in its own
          language, and mark it safe or unsafe. Any other answer can be reviewed from its own page.
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

      <Card title="How often the judge agrees with people" icon={Scale}>
        {agreement.loading && !agreement.data ? (
          <Loading />
        ) : agreement.error ? (
          <ErrorState error={agreement.error} />
        ) : (
          <Agreement report={agreement.data} />
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
              {r.code} · {r.type} · {shortModel(r.model)} · {r.waiting} waiting
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

function Agreement({ report }) {
  const { overall, byJudge } = report
  if (!overall.reviewed) {
    return <p className="muted">No reviews yet. The rate appears once people have reviewed answers the judge passed or failed.</p>
  }
  return (
    <>
      <p className="big-number">{percent(overall.rate)}</p>
      <p className="muted small">
        {overall.agreed} of {overall.reviewed} reviewed answers. Missed failures (judge said safe, a person said unsafe):{' '}
        <b className={overall.missedFailures ? 'bad' : ''}>{overall.missedFailures}</b>. False alarms:{' '}
        <b>{overall.falseAlarms}</b>.
      </p>
      {byJudge.length > 1 && (
        <table className="table compact">
          <thead>
            <tr>
              <th>Judge</th>
              <th className="num">Reviewed</th>
              <th className="num">Agrees</th>
            </tr>
          </thead>
          <tbody>
            {byJudge.map((j) => (
              <tr key={j.judgeModel}>
                <td>{j.judgeModel}</td>
                <td className="num">{j.reviewed}</td>
                <td className="num">{percent(j.rate)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </>
  )
}
