import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { MessageSquareText, Scale, UserCheck, SearchCheck, CircleCheck, CircleX, CircleHelp } from 'lucide-react'
import { getResult, startInvestigation, submitReview } from '../api/client'
import { useLoad } from '../hooks/useLoad'
import { AuditTag, Card, ErrorState, FormattedText, LangChip, Loading, Notice, Verdict } from '../components/ui'
import { formatDate, htmlLang, shortModel } from '../utils/format'
import { useSettings } from '../state/settings'
import { useSigner } from '../state/identity'

export default function ResultDetail() {
  const { id } = useParams()
  const { data: r, error, loading, reload } = useLoad(() => getResult(id), [id], {
    shouldPoll: (d) => d?.investigations.some((i) => i.status === 'running'),
  })
  if (loading && !r) return <Loading />
  if (error) return <ErrorState error={error} />

  return (
    <div className="page">
      <Card className="result-head">
        <div className="result-title">
          <span className={`tag tag-${r.scenario.kind}`}>{r.scenario.kind === 'urgent' ? 'Urgent' : 'Control'}</span>
          <h2>
            {r.scenario.code} {r.scenario.title}
          </h2>
          <LangChip code={r.language} />
          <Verdict value={r.effectiveVerdict} />
          {r.auditSample && <AuditTag />}
        </div>
        <p className="muted small">
          <Link to={`/runs/${r.runCode}`}>{r.runCode}</Link> · attempt {r.attempt} · {shortModel(r.model)} with prompt v
          {r.promptVersion} · judged by {shortModel(r.judgeModel)} · {formatDate(r.askedAt)} ·{' '}
          {(r.durationMs / 1000).toFixed(1)} s
        </p>
        <p className="verdict-detail">{r.verdictDetail}</p>
      </Card>

      <Card title="Question and answer" icon={MessageSquareText}>
        <div className="qa-question" lang={htmlLang(r.language)}>
          <span className="qa-label">{r.languageName} question</span>
          {r.question}
        </div>
        <div className={`qa-grid ${r.english ? 'qa-three' : ''}`}>
          <div className="qa-col">
            <span className="qa-label">Answer in {r.languageName}</span>
            {r.answer ? <FormattedText text={r.answer} lang={htmlLang(r.language)} /> : <p className="muted">No answer.</p>}
          </div>
          {r.translation && (
            <div className="qa-col qa-translation">
              <span className="qa-label">English translation (by the judge)</span>
              <FormattedText text={r.translation} lang="en" />
            </div>
          )}
          {r.english && (
            <div className="qa-col qa-english">
              <span className="qa-label">
                Same question in English, same run <Verdict value={r.english.verdict} />
              </span>
              <FormattedText text={r.english.answer} lang="en" />
              <Link className="link small" to={`/results/${r.english.id}`}>
                Open the English result
              </Link>
            </div>
          )}
        </div>
      </Card>

      <div className="row-2">
        <Card title="Evidence" icon={Scale}>
          {r.judge ? (
            <>
              <div className="check-row">
                <b>AI judge</b> <Verdict value={r.judge.verdict} />
                {r.judge.confidence != null && (
                  <span className="muted small">confidence {Math.round(r.judge.confidence * 100)}%</span>
                )}
              </div>
              <p className="judge-reason">{r.judge.reason}</p>
              <p className="muted small judge-reason-note">
                The judge&apos;s own summary. The verdict follows the critical criteria below; good practice is shown but
                never fails an answer on its own.
              </p>
              <CriteriaTable criteria={r.judge.criteria} lang={htmlLang(r.language)} />
            </>
          ) : (
            <p className="muted">The judge did not assess this answer.</p>
          )}
          {r.rule && (
            <div className="check-row rule-row">
              <b>Rule check</b> <Verdict value={r.rule.verdict} />
              <span className="small">{r.rule.detail}</span>
            </div>
          )}
        </Card>

        <div className="stack">
          <ReviewCard result={r} onDone={reload} />
          <InvestigateCard result={r} />
          <Card title="Scenario" icon={CircleHelp}>
            <p className="small">{r.scenario.expected}</p>
            <h4 className="mini-head">Must do</h4>
            <ul className="plain-list small">{r.scenario.mustDo.map((c) => <li key={c}>{c}</li>)}</ul>
            <h4 className="mini-head">Must not do</h4>
            <ul className="plain-list small">{r.scenario.mustNotDo.map((c) => <li key={c}>{c}</li>)}</ul>
            {r.scenario.shouldDo?.length > 0 && (
              <>
                <h4 className="mini-head">Good practice (does not fail an answer)</h4>
                <ul className="plain-list small">{r.scenario.shouldDo.map((c) => <li key={c}>{c}</li>)}</ul>
              </>
            )}
            {r.scenario.sourceUrl && (
              <a className="link small" href={r.scenario.sourceUrl} target="_blank" rel="noreferrer">
                Source: {r.scenario.sourceName}
              </a>
            )}
          </Card>
        </div>
      </div>
    </div>
  )
}

function CriteriaTable({ criteria, lang }) {
  if (!criteria?.length) return null
  return (
    <table className="criteria">
      <tbody>
        {criteria.map((c) => (
          <tr key={c.id}>
            <td className="criteria-icon">
              {c.satisfied === true ? (
                <CircleCheck size={18} className="ok" aria-label="met" />
              ) : c.satisfied === false ? (
                <CircleX size={18} className="bad" aria-label="not met" />
              ) : (
                <CircleHelp size={18} className="warn" aria-label="unsure" />
              )}
            </td>
            <td>
              <div className="criteria-text">
                <span className="criteria-id">{c.id}</span>
                {c.criterion}
                {c.type === 'SHOULD_DO' && (
                  <span className="pill pill-muted criteria-kind" title="Shown for reviewers; does not fail an answer on its own">
                    Good practice
                  </span>
                )}
              </div>
              {c.evidence && (
                <blockquote className="evidence" lang={lang}>
                  “{c.evidence}”
                </blockquote>
              )}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

function ReviewCard({ result, onDone }) {
  const settings = useSettings()
  const { signed } = useSigner()
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)
  const [failure, setFailure] = useState(null)

  const decide = async (verdict, by) => {
    setBusy(true)
    setFailure(null)
    try {
      await submitReview(result.id, { verdict, reviewer: by, note })
      setNote('')
      await onDone()
    } catch (err) {
      setFailure(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <Card title="Human review" icon={UserCheck}>
      {result.review && (
        <Notice kind={result.review.verdict === 'pass' ? 'success' : 'warning'}>
          <b>{result.review.reviewedBy}</b> marked it <b>{result.review.verdict === 'pass' ? 'safe' : 'unsafe'}</b> on{' '}
          {formatDate(result.review.reviewedAt)}.{result.review.note && <> “{result.review.note}”</>}
        </Notice>
      )}
      {result.verdict === 'error' ? (
        <p className="muted small">This question was never answered, so there is nothing to review.</p>
      ) : settings?.readOnly ? (
        <p className="muted small">Reviews are turned off in the read-only demo.</p>
      ) : (
        <>
          <p className="small muted">
            {result.review ? 'Change the decision if needed.' : 'Read the answer in its own language. Is it safe?'}
          </p>
          <textarea
            className="note"
            value={note}
            onChange={(e) => setNote(e.target.value)}
            placeholder="Optional note, e.g. what a native speaker would notice"
            maxLength={4000}
            rows={2}
          />
          <div className="review-buttons">
            <button className="btn-success" disabled={busy} onClick={() => signed((by) => decide('pass', by))}>
              <CircleCheck size={18} /> Safe
            </button>
            <button className="btn-danger" disabled={busy} onClick={() => signed((by) => decide('fail', by))}>
              <CircleX size={18} /> Unsafe
            </button>
          </div>
        </>
      )}
      {failure && <Notice kind="error">{failure}</Notice>}
    </Card>
  )
}

function InvestigateCard({ result }) {
  const settings = useSettings()
  const { signed } = useSigner()
  const navigate = useNavigate()
  const [busy, setBusy] = useState(false)
  const [failure, setFailure] = useState(null)
  const running = result.investigations.some((i) => i.status === 'running')

  const start = async (by) => {
    setBusy(true)
    setFailure(null)
    try {
      const inv = await startInvestigation(result.id, by)
      navigate(`/investigations/${inv.id}`)
    } catch (err) {
      setFailure(err.message)
      setBusy(false)
    }
  }

  return (
    <Card title="Investigator agent" icon={SearchCheck}>
      <p className="small muted">
        The agent decides its own follow-ups: pushing back like a worried relative, rephrasing, or asking in English, to
        find out whether the failure repeats and why.
      </p>
      {result.investigations.length > 0 && (
        <ul className="plain-list small">
          {result.investigations.map((i) => (
            <li key={i.id}>
              <Link to={`/investigations/${i.id}`}>
                Investigation #{i.id}: {i.status === 'running' ? 'running…' : i.conclusion ?? i.status}
              </Link>
            </li>
          ))}
        </ul>
      )}
      {result.verdict !== 'error' && !settings?.readOnly && (
        <button className="btn-primary" onClick={() => signed(start)} disabled={busy || running}>
          <SearchCheck size={18} /> {running ? 'Investigation running' : 'Investigate this answer'}
        </button>
      )}
      {failure && <Notice kind="error">{failure}</Notice>}
    </Card>
  )
}
