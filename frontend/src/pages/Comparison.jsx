import { Link, useParams } from 'react-router-dom'
import { GitCompare, Languages, ListChecks } from 'lucide-react'
import { getComparison } from '../api/client'
import { useLoad } from '../hooks/useLoad'
import { Card, ChangeCount, ErrorState, LangChip, Loading, Notice, Verdict } from '../components/ui'

const CHANGES = {
  fixed: ['Fixed', 'tag-success'],
  still_failing: ['Still failing', 'tag-danger'],
  new_failure: ['New failure', 'tag-danger'],
  still_passing: ['Still passing', 'tag-muted'],
}

export default function Comparison() {
  const { code } = useParams()
  const { data, error, loading } = useLoad(() => getComparison(code), [code], {
    shouldPoll: (d) => d?.retest.status === 'running',
  })
  if (loading && !data) return <Loading />
  if (error) return <ErrorState error={error} />

  const { baseline, retest, counts, cells } = data
  const order = { new_failure: 0, still_failing: 1, fixed: 2, still_passing: 3 }
  const sorted = [...cells].sort((a, b) => order[a.change] - order[b.change])
  return (
    <div className="page">
      <Card title={`Prompt v${baseline.promptVersion} → v${retest.promptVersion}`} icon={GitCompare}>
        <p className="muted small">
          <Link to={`/runs/${baseline.code}`}>{baseline.code}</Link> compared with its retest{' '}
          <Link to={`/runs/${retest.code}`}>{retest.code}</Link>. The retest asks every question that did not pass,
          plus every English and control question, so a fix cannot hide a regression.
        </p>
        {retest.status === 'running' && <Notice>The retest is still running; the numbers will update.</Notice>}
        <div className="change-counts">
          <ChangeCount tone="good" value={counts.fixed} label="fixed" />
          <ChangeCount tone="bad" value={counts.stillFailing} label="still failing" />
          <ChangeCount tone="bad" value={counts.newFailures} label="new failures" />
          <ChangeCount tone="neutral" value={counts.stillPassing} label="still passing" />
        </div>
        {counts.newFailures > 0 && (
          <Notice kind="warning">
            The fix broke {counts.newFailures} answer{counts.newFailures > 1 ? 's' : ''} that passed before. Ask the
            agent for a revised fix from the retest run before adopting it.
          </Notice>
        )}
      </Card>

      <Card title="Pass rate by language" icon={Languages}>
        <table className="table compact">
          <thead>
            <tr>
              <th>Language</th>
              <th className="num">Before (v{baseline.promptVersion})</th>
              <th className="num">After (v{retest.promptVersion})</th>
            </tr>
          </thead>
          <tbody>
            {retest.languages.map((l) => {
              const before = baseline.languages.find((b) => b.language === l.language)
              return (
                <tr key={l.language}>
                  <td>
                    <LangChip code={l.language} /> {l.languageName}
                  </td>
                  <td className="num">{rate(before?.counts)}</td>
                  <td className="num">
                    <b>{rate(l.counts)}</b>
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
        <p className="muted small table-note">
          Before covers every question in the original run; after covers only the questions retested.
        </p>
      </Card>

      <Card title="Every retested question" icon={ListChecks}>
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>Scenario</th>
                <th>Language</th>
                <th>Before</th>
                <th>After</th>
                <th>Change</th>
              </tr>
            </thead>
            <tbody>
              {sorted.map((c) => {
                const [label, className] = CHANGES[c.change]
                return (
                  <tr key={`${c.scenarioCode}-${c.language}`}>
                    <td>
                      <b>{c.scenarioCode}</b> {c.scenarioTitle}
                    </td>
                    <td>
                      <LangChip code={c.language} />
                    </td>
                    <td>
                      <span className="verdict-row">
                        {c.before.map((r) => (
                          <Link key={r.id} to={`/results/${r.id}`} className="cell-link" title={`Attempt ${r.attempt}`}>
                            <Verdict value={r.effectiveVerdict} />
                          </Link>
                        ))}
                      </span>
                    </td>
                    <td>
                      <span className="verdict-row">
                        {c.after.map((r) => (
                          <Link key={r.id} to={`/results/${r.id}`} className="cell-link" title={`Attempt ${r.attempt}`}>
                            <Verdict value={r.effectiveVerdict} />
                          </Link>
                        ))}
                      </span>
                    </td>
                    <td>
                      <span className={`tag ${className}`}>{label}</span>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  )
}

function rate(counts) {
  if (!counts) return '—'
  const checked = counts.pass + counts.fail + counts.needsReview
  return checked ? `${Math.round((100 * counts.pass) / checked)}%` : '—'
}
