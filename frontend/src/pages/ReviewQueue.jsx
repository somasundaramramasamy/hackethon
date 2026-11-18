import { Link, useSearchParams } from 'react-router-dom'
import { ClipboardCheck, History, Languages, LayoutDashboard, Scale, Stethoscope } from 'lucide-react'
import { getReviewHistory, getReviewQueue } from '../api/client'
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
  const view = params.get('view') === 'reviewed' ? 'reviewed' : 'waiting'
  const disagreements = params.get('disagreements') === '1'
  const queue = useLoad(() => getReviewQueue({ run, language }), [run, language])
  // Everything reviewed in the run; the language and disagreement filters apply below.
  const history = useLoad(() => getReviewHistory({ run }), [run])

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
      {page?.runCode && (
        <ReviewDashboard
          page={page}
          onRun={(code) => choose({ run: code })}
          onLanguage={(l) => choose({ language: l, view: '' })}
          onReviewed={() => choose({ view: 'reviewed' })}
        />
      )}

      <Card title={view === 'reviewed' ? 'Reviewed' : 'Waiting for review'} icon={view === 'reviewed' ? History : ClipboardCheck}>
        <div className="review-tabs" role="tablist" aria-label="Review queue">
          <button
            type="button"
            role="tab"
            aria-selected={view === 'waiting'}
            className={view === 'waiting' ? 'active' : ''}
            onClick={() => choose({ view: '', disagreements: '' })}
          >
            Waiting <span className="count">{page ? page.languages.reduce((sum, l) => sum + l.waiting, 0) : '…'}</span>
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={view === 'reviewed'}
            className={view === 'reviewed' ? 'active' : ''}
            onClick={() => choose({ view: 'reviewed' })}
          >
            Reviewed <span className="count">{history.data ? history.data.length : '…'}</span>
          </button>
        </div>
        {view === 'reviewed' ? (
          <ReviewedPanel
            page={page}
            history={history}
            language={language}
            disagreements={disagreements}
            onChange={choose}
          />
        ) : (
          <>
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

/** What people decided in the chosen run, with how each decision compares with the judge. */
function ReviewedPanel({ page, history, language, disagreements, onChange }) {
  if (history.loading && !history.data) return <Loading />
  if (history.error) return <ErrorState error={history.error} />
  const all = history.data
  const isDisagreement = (i) => i.agreement === 'missed_failure' || i.agreement === 'false_alarm'
  const lang = language?.toUpperCase() || ''
  const items = all.filter((i) => (!lang || i.language === lang) && (!disagreements || isDisagreement(i)))
  const count = (code) => all.filter((i) => i.language === code).length
  return (
    <>
      <p className="muted card-intro">
        Every decision people made in this test, signed with their name and role. Open one to see the answer or change
        the decision. Disagreements with the judge are what the agreement rate is built from.
      </p>
      <div className="queue-filters">
        <div className="lang-filter" role="group" aria-label="Language">
          <button type="button" className={!lang ? 'active' : ''} aria-pressed={!lang} onClick={() => onChange({ language: '' })}>
            All <span className="count">{all.length}</span>
          </button>
          {(page?.languages ?? []).map((l) => (
            <button
              key={l.language}
              type="button"
              className={lang === l.language ? 'active' : ''}
              aria-pressed={lang === l.language}
              onClick={() => onChange({ language: l.language })}
            >
              {l.languageName} <span className="count">{count(l.language)}</span>
            </button>
          ))}
        </div>
        <label className="check disagreement-toggle">
          <input
            type="checkbox"
            checked={disagreements}
            onChange={(e) => onChange({ disagreements: e.target.checked ? '1' : '' })}
          />
          Disagreements with the judge only ({all.filter(isDisagreement).length})
        </label>
      </div>
      {items.length === 0 ? (
        <p className="muted">
          {all.length === 0 ? 'No one has reviewed an answer in this test yet.' : 'No reviewed answers match these filters.'}
        </p>
      ) : (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>Answer</th>
                <th>Language</th>
                <th>Judge → person</th>
                <th>Compared with the judge</th>
                <th>Signed by</th>
                <th>Reviewed</th>
              </tr>
            </thead>
            <tbody>
              {items.map((i) => (
                <tr key={i.id}>
                  <td>
                    <Link className="run-id queue-answer" to={`/results/${i.id}`}>
                      {i.scenarioCode} {i.scenarioTitle}
                    </Link>
                    <div className="muted small">attempt {i.attempt}</div>
                    {i.note && <div className="small review-note">“{i.note}”</div>}
                  </td>
                  <td>
                    <LangChip code={i.language} /> {i.languageName}
                  </td>
                  <td className="nowrap">
                    <span className="verdict-row">
                      {i.judgeVerdict ? <Verdict value={i.judgeVerdict} /> : <span className="muted">—</span>}
                      <span className="muted">→</span>
                      <Verdict value={i.humanVerdict} />
                    </span>
                  </td>
                  <td>
                    <AgreementTag value={i.agreement} />
                  </td>
                  <td className="small">{i.reviewedBy}</td>
                  <td className="nowrap small muted">{formatDate(i.reviewedAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  )
}

const AGREEMENT = {
  agreed: ['Agreed', 'tag-success', 'The person and the judge gave the same verdict'],
  missed_failure: ['Missed failure', 'tag-danger', 'The judge passed it; the person found it unsafe'],
  false_alarm: ['False alarm', 'tag-warning', 'The judge failed it; the person found it safe'],
  not_compared: ['Not compared', 'tag-muted', 'The judge was unsure or unavailable, so there is nothing to compare'],
}

function AgreementTag({ value }) {
  const [label, className, title] = AGREEMENT[value] ?? [value, 'tag-muted', '']
  return (
    <span className={`tag ${className}`} title={title}>
      {label}
    </span>
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
function ReviewDashboard({ page, onRun, onLanguage, onReviewed }) {
  const count = (code) => page.languages.find((l) => l.language === code)?.waiting ?? 0
  const waiting = page.languages.reduce((sum, l) => sum + l.waiting, 0)
  const english = count('EN')
  const native = page.languages.filter((l) => l.language !== 'EN')
  const picked = waiting + page.reviewed
  return (
    <Card
      title="Review dashboard"
      icon={LayoutDashboard}
      action={
        <label className="test-picker">
          <span>Test</span>
          <select value={page.runCode ?? ''} onChange={(e) => onRun(e.target.value)}>
            {page.runs.map((r) => (
              <option key={r.code} value={r.code} title={r.code}>
                {r.name} · {r.waiting} waiting
              </option>
            ))}
          </select>
        </label>
      }
    >
      <div className="review-stats">
        <div className="review-stat">
          <span className="review-stat-label">Waiting for review</span>
          <span className="review-stat-value">{waiting}</span>
          <Progress done={page.reviewed} total={picked} />
          <button type="button" className="link small review-stat-link" onClick={onReviewed}>
            {page.reviewed} of {picked} picked answers reviewed
          </button>
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
          <Agreement agreement={page.agreement} />
        </div>
      </div>
    </Card>
  )
}

function Agreement({ agreement }) {
  if (!agreement?.reviewed) {
    return (
      <>
        <span className="review-stat-value muted">—</span>
        <span className="muted small">Appears once people review answers the judge passed or failed in this test</span>
      </>
    )
  }
  return (
    <>
      <span className="review-stat-value">{percent(agreement.rate)}</span>
      <span className="muted small">
        {agreement.agreed} of {agreement.reviewed} in this test · missed failures{' '}
        <b className={agreement.missedFailures ? 'bad' : ''}>{agreement.missedFailures}</b> · false alarms{' '}
        <b>{agreement.falseAlarms}</b>
      </span>
    </>
  )
}
