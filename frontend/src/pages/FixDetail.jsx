import { Link, useParams } from 'react-router-dom'
import { Wrench, GitCompare, ArrowRight, FlaskConical } from 'lucide-react'
import { getComparison, getPrompt, getRuns } from '../api/client'
import { useLoad } from '../hooks/useLoad'
import { Card, ErrorState, Loading, StatusPill } from '../components/ui'
import PromptStatus from '../components/PromptStatus'
import FixDecisions from '../components/FixDecisions'
import { diffLines } from '../utils/diff'
import { formatDate } from '../utils/format'

export default function FixDetail() {
  const { version } = useParams()
  const prompt = useLoad(() => getPrompt(version), [version])
  const base = useLoad(
    () => (prompt.data?.basedOnVersion ? getPrompt(prompt.data.basedOnVersion) : Promise.resolve(null)),
    [prompt.data?.basedOnVersion],
  )
  const runs = useLoad(getRuns, [version], { shouldPoll: (d) => d?.some((r) => r.status === 'running') })
  const latestRetest = (runs.data ?? []).find((r) => prompt.data?.retestRuns.includes(r.code))
  const comparison = useLoad(
    () => (latestRetest?.status === 'completed' ? getComparison(latestRetest.code) : Promise.resolve(null)),
    [latestRetest?.code, latestRetest?.status],
  )

  if (prompt.loading && !prompt.data) return <Loading />
  if (prompt.error) return <ErrorState error={prompt.error} />

  const p = prompt.data
  const retests = (runs.data ?? []).filter((r) => p.retestRuns.includes(r.code))
  const newFailures = comparison.data?.counts.newFailures ?? 0
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
        <FixDecisions
          prompt={p}
          retests={retests}
          newFailures={newFailures}
          onDone={() => Promise.all([prompt.reload(), runs.reload()])}
        />
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
