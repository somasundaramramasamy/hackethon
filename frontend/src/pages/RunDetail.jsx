import { useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { Languages, ListChecks, Wand2, ArrowRight, Info, Pencil } from 'lucide-react'
import { getRun, proposeFix, renameRun } from '../api/client'
import { useLoad } from '../hooks/useLoad'
import { AuditTag, Card, ErrorState, LangChip, Loading, Notice, Progress, StatusPill, Verdict } from '../components/ui'
import LanguageBars from '../components/LanguageBars'
import Stepper from '../components/Stepper'
import { formatDate, percent, shortModel } from '../utils/format'
import { useSettings } from '../state/settings'
import { useSigner } from '../state/identity'

const LANGS = ['EN', 'TA', 'HI', 'ML']

export default function RunDetail() {
  const { code } = useParams()
  const location = useLocation()
  const { data, error, loading, reload } = useLoad(() => getRun(code), [code], {
    shouldPoll: (d) => d?.run.status === 'running',
  })
  if (loading && !data) return <Loading />
  if (error) return <ErrorState error={error} />

  const { run, scenarios } = data
  const languages = LANGS.filter((l) => run.languages.some((x) => x.language === l))
  return (
    <div className="page">
      <Card className="run-header-card">
        {(location.state?.fromNewTest || data.run.status === 'running') && data.run.type === 'baseline' && (
          <Stepper current={data.run.status === 'running' ? 3 : 4} />
        )}
        <div className="run-summary">
          <div>
            <RunName run={run} onRenamed={reload} />
            <p className="muted">
              {run.code} · {run.type === 'retest' ? `Retest of ${run.baselineRun} with prompt v${run.promptVersion}` : run.suite}
            </p>
          </div>
          <dl className="facts">
            <div>
              <dt>Assistant</dt>
              <dd>{shortModel(run.model)}</dd>
            </div>
            <div>
              <dt>Judge</dt>
              <dd>{shortModel(run.judgeModel)}</dd>
            </div>
            <div>
              <dt>Prompt</dt>
              <dd>
                <Link to={`/fixes/${run.promptVersion}`}>v{run.promptVersion}</Link>
              </dd>
            </div>
            <div>
              <dt>Pass rate</dt>
              <dd>{run.status === 'running' ? '…' : percent(run.safetyScore)}</dd>
            </div>
            <div>
              <dt>Started</dt>
              <dd>
                {formatDate(run.startedAt)}
                {run.startedBy && <span className="muted"> by {run.startedBy}</span>}
              </dd>
            </div>
            {run.rescoredAt && (
              <div>
                <dt>Re-scored</dt>
                <dd title="Verdicts recomputed from the judge's stored findings after the criteria changed; nothing was asked again">
                  {formatDate(run.rescoredAt)}
                </dd>
              </div>
            )}
          </dl>
        </div>
        {run.status === 'running' && (
          <div className="run-progress">
            <span>
              {run.completedTests} of {run.plannedTests} answers checked
            </span>
            <Progress done={run.completedTests} total={run.plannedTests} />
          </div>
        )}
        <RunActions run={run} />
      </Card>

      <div className="row-2">
        <Card title="Results by language" icon={Languages}>
          <LanguageBars languages={run.languages} />
        </Card>
        <Card title="What the verdicts mean" icon={Info}>
          <ul className="plain-list small">
            <li>
              <Verdict value="pass" /> the rule check and the AI judge both found it safe.
            </li>
            <li>
              <Verdict value="fail" /> the judge found a must-do missed or a must-not-do done.
            </li>
            <li>
              <Verdict value="needs_review" /> the checks disagree or the judge is unsure.
            </li>
            <li>
              <AuditTag /> picked at random for a person to check: 20% of failures and 10% of passes, per language.
            </li>
            <li>Nothing is a confirmed finding until a native speaker reviews it.</li>
          </ul>
        </Card>
      </div>

      <Card title="Every answer" icon={ListChecks}>
        <div className="table-wrap">
          <table className="table result-grid">
            <thead>
              <tr>
                <th>Scenario</th>
                {languages.map((l) => (
                  <th key={l}>
                    <LangChip code={l} />
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {scenarios.map((s) => (
                <tr key={s.code}>
                  <td>
                    <div className="scenario-cell">
                      <span className={`tag tag-${s.kind}`}>{s.kind === 'urgent' ? 'Urgent' : 'Control'}</span>
                      <span>
                        <b>{s.code}</b> {s.title}
                      </span>
                    </div>
                  </td>
                  {languages.map((l) => {
                    const cell = s.cells.find((c) => c.language === l)
                    return (
                      <td key={l}>
                        <div className="cell-results">
                          {cell?.results.map((r) => (
                            <Link key={r.id} to={`/results/${r.id}`} className="cell-link" title={`Attempt ${r.attempt}`}>
                              <Verdict value={r.effectiveVerdict} label={r.humanVerdict ? `${label(r.effectiveVerdict)} ✓` : undefined} />
                              {r.auditSample && !r.humanVerdict && <AuditTag />}
                            </Link>
                          )) ?? <span className="muted">—</span>}
                        </div>
                      </td>
                    )
                  })}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <p className="muted small table-note">✓ = confirmed by a reviewer. Click any verdict to see the answer and the evidence.</p>
      </Card>
    </div>
  )
}

const label = (v) => ({ pass: 'Pass', fail: 'Fail', needs_review: 'Review', error: 'Error' })[v] ?? v

function RunActions({ run }) {
  const settings = useSettings()
  const { signed } = useSigner()
  const navigate = useNavigate()
  const [busy, setBusy] = useState(false)
  const [failure, setFailure] = useState(null)
  if (run.status === 'running') return null

  const failing = run.counts.fail + run.counts.needsReview
  const propose = async (by) => {
    setBusy(true)
    setFailure(null)
    try {
      const proposal = await proposeFix(run.code, by)
      navigate(`/fixes/${proposal.version}`)
    } catch (err) {
      setFailure(err.message)
      setBusy(false)
    }
  }

  return (
    <div className="run-actions">
      {run.type === 'retest' && (
        <Link className="btn-secondary" to={`/runs/${run.code}/comparison`}>
          Before and after <ArrowRight size={16} />
        </Link>
      )}
      {failing > 0 && (
        <button className="btn-primary" onClick={() => signed(propose)} disabled={busy || settings?.readOnly}>
          <Wand2 size={18} /> {busy ? 'The agent is writing a fix (about 30 s)…' : 'Ask the agent for a fix'}
        </button>
      )}
      {run.awaitingReview > 0 && (
        <span className="muted small">
          {run.awaitingReview} answers wait for review. Reviewing first gives the agent better evidence.
        </span>
      )}
      {failure && <Notice kind="error">{failure}</Notice>}
    </div>
  )
}

/** The run's name, which people can change; the code, and every link, stays the same. */
function RunName({ run, onRenamed }) {
  const settings = useSettings()
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState(run.name)
  const [busy, setBusy] = useState(false)
  const [failure, setFailure] = useState(null)

  const save = async (e) => {
    e.preventDefault()
    setBusy(true)
    setFailure(null)
    try {
      await renameRun(run.code, draft)
      await onRenamed()
      setEditing(false)
    } catch (err) {
      setFailure(err.message)
    } finally {
      setBusy(false)
    }
  }

  if (editing) {
    return (
      <form className="run-rename" onSubmit={save}>
        <input
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          maxLength={120}
          aria-label="Test name"
          autoFocus
          onKeyDown={(e) => e.key === 'Escape' && setEditing(false)}
        />
        <button className="btn-primary" type="submit" disabled={busy || !draft.trim()}>
          Save
        </button>
        <button className="btn-link" type="button" onClick={() => setEditing(false)}>
          Cancel
        </button>
        {failure && <Notice kind="error">{failure}</Notice>}
      </form>
    )
  }
  return (
    <div className="run-title">
      <h2>{run.name}</h2> <StatusPill status={run.status} />
      {!settings?.readOnly && (
        <button
          className="icon-btn"
          onClick={() => {
            setDraft(run.name)
            setEditing(true)
          }}
          aria-label="Rename this test"
          title="Rename this test"
        >
          <Pencil size={16} />
        </button>
      )}
    </div>
  )
}
