import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Wrench, GitCompare, ArrowRight, Check, X, RotateCcw, FlaskConical } from 'lucide-react'
import { decidePrompt, getPrompt, getRuns } from '../api/client'
import { useLoad } from '../hooks/useLoad'
import { Card, ErrorState, Loading, Notice, StatusPill } from '../components/ui'
import PromptStatus from '../components/PromptStatus'
import { diffLines } from '../utils/diff'
import { formatDate } from '../utils/format'
import { useSettings } from '../state/settings'
import { useReviewerName } from '../state/reviewer'

export default function FixDetail() {
  const { version } = useParams()
  const prompt = useLoad(() => getPrompt(version), [version])
  const base = useLoad(
    () => (prompt.data?.basedOnVersion ? getPrompt(prompt.data.basedOnVersion) : Promise.resolve(null)),
    [prompt.data?.basedOnVersion],
  )
  const runs = useLoad(getRuns, [version], { shouldPoll: (d) => d?.some((r) => r.status === 'running') })

  if (prompt.loading && !prompt.data) return <Loading />
  if (prompt.error) return <ErrorState error={prompt.error} />

  const p = prompt.data
  const retests = (runs.data ?? []).filter((r) => p.retestRuns.includes(r.code))
  return (
    <div className="page">
      <Card>
        <div className="result-title">
          <Wrench size={22} className="card-icon" />
          <h2>System prompt v{p.version}</h2>
          <PromptStatus prompt={p} />
        </div>
        <p className="muted small">
          {p.createdBy.startsWith('agent:') ? `Proposed by the fix agent (${p.createdBy.slice(6)})` : `Created by ${p.createdBy}`}{' '}
          · {formatDate(p.createdAt)}
          {p.sourceRun && (
            <>
              {' '}
              · fixes <Link to={`/runs/${p.sourceRun}`}>{p.sourceRun}</Link>
            </>
          )}
          {p.decidedBy && ` · decided by ${p.decidedBy} on ${formatDate(p.decidedAt)}`}
        </p>
        {p.summary && <p className="fix-summary">{p.summary}</p>}
        {p.rationale && <p className="muted fix-rationale">{p.rationale}</p>}
        {p.decisionNote && <p className="small">Note: “{p.decisionNote}”</p>}
        <Decisions prompt={p} retests={retests} onDone={() => Promise.all([prompt.reload(), runs.reload()])} />
      </Card>

      {retests.length > 0 && (
        <Card title="Retests of this version" icon={FlaskConical}>
          <ul className="plain-list">
            {retests.map((r) => (
              <li key={r.code} className="retest-row">
                <Link to={`/runs/${r.code}`}>{r.code}</Link> <StatusPill status={r.status} />
                {r.status === 'running' ? (
                  <span className="muted small">
                    {r.completedTests} of {r.plannedTests} answers checked
                  </span>
                ) : (
                  <Link className="link" to={`/runs/${r.code}/comparison`}>
                    Before and after <ArrowRight size={16} />
                  </Link>
                )}
              </li>
            ))}
          </ul>
        </Card>
      )}

      <Card title={base.data ? `Changes from v${base.data.version}` : 'Prompt text'} icon={GitCompare}>
        {base.data ? (
          <pre className="diff">
            {diffLines(base.data.text, p.text).map((line, i) => (
              <div key={i} className={`diff-${line.type}`}>
                <span className="diff-sign">{line.type === 'added' ? '+' : line.type === 'removed' ? '−' : ' '}</span>
                {line.text || ' '}
              </div>
            ))}
          </pre>
        ) : (
          <pre className="prompt-text">{p.text}</pre>
        )}
      </Card>
    </div>
  )
}

function Decisions({ prompt, retests, onDone }) {
  const settings = useSettings()
  const name = useReviewerName()
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)
  const [failure, setFailure] = useState(null)
  if (settings?.readOnly) return null

  const decide = async (decision) => {
    setBusy(true)
    setFailure(null)
    try {
      await decidePrompt(prompt.version, decision, name, note || undefined)
      setNote('')
      await onDone()
    } catch (err) {
      setFailure(err.message)
    } finally {
      setBusy(false)
    }
  }

  const finishedRetest = retests.some((r) => r.status === 'completed')
  const retestRunning = retests.some((r) => r.status === 'running')
  return (
    <div className="decisions">
      {prompt.status === 'proposed' && (
        <>
          <p className="small">
            Approving starts a retest at once: the failures, every English question and every control question, with
            this prompt. Nothing changes for real users until the version is adopted.
          </p>
          <textarea
            className="note"
            value={note}
            onChange={(e) => setNote(e.target.value)}
            placeholder="Optional note for the record"
            rows={2}
            maxLength={1000}
          />
          <div className="review-buttons">
            <button className="btn-success" disabled={busy || !name} onClick={() => decide('approve')}>
              <Check size={18} /> Approve and retest
            </button>
            <button className="btn-danger" disabled={busy || !name} onClick={() => decide('reject')}>
              <X size={18} /> Reject
            </button>
          </div>
        </>
      )}
      {prompt.status === 'approved' && retests.length === 0 && (
        <button className="btn-primary" disabled={busy || !name} onClick={() => decide('retest')}>
          <FlaskConical size={18} /> Start the retest
        </button>
      )}
      {prompt.status === 'approved' && (
        <button className="btn-success" disabled={busy || !name || !finishedRetest} onClick={() => decide('adopt')}>
          <Check size={18} /> Adopt v{prompt.version}
        </button>
      )}
      {prompt.status === 'approved' && !finishedRetest && (
        <span className="muted small">{retestRunning ? 'Adopt once the retest finishes.' : 'Adopt after a retest.'}</span>
      )}
      {prompt.status === 'retired' && (
        <button className="btn-secondary" disabled={busy || !name} onClick={() => decide('adopt')}>
          <RotateCcw size={18} /> Roll back to v{prompt.version}
        </button>
      )}
      {!name && prompt.status !== 'active' && prompt.status !== 'rejected' && (
        <p className="small warn">Enter your name at the top right: every decision is signed.</p>
      )}
      {failure && <Notice kind="error">{failure}</Notice>}
    </div>
  )
}
