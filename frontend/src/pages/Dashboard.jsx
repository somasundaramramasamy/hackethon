import { Link } from 'react-router-dom'
import { ShieldCheck, ShieldAlert, Scale, FileText, Languages, Wrench, History, ArrowRight } from 'lucide-react'
import { getDashboard } from '../api/client'
import { useLoad } from '../hooks/useLoad'
import { Card, ChangeCount, ErrorState, Loading, Notice, Progress } from '../components/ui'
import LanguageBars from '../components/LanguageBars'
import RunsTable from '../components/RunsTable'
import { percent, shortModel } from '../utils/format'

export default function Dashboard() {
  const { data, error, loading } = useLoad(getDashboard, [], { shouldPoll: (d) => Boolean(d?.running) })
  if (loading && !data) return <Loading />
  if (error) return <ErrorState error={error} />

  const { latestBaseline: base, latestRetest: retest, running, agreement } = data
  return (
    <div className="page dashboard">
      {running && (
        <Notice>
          Run <Link to={`/runs/${running.code}`}>{running.code}</Link> is in progress: {running.completedTests} of{' '}
          {running.plannedTests} answers checked.
          <Progress done={running.completedTests} total={running.plannedTests} />
        </Notice>
      )}

      {!base ? (
        <Card title="No finished run yet" icon={History}>
          <p>
            Kavasam asks the same {data.activeScenarios} everyday health questions in English, Tamil, Hindi and
            Malayalam, checks every answer, and shows where safety drops in a language.
          </p>
          <Link className="btn-primary inline" to="/runs">
            Start a run <ArrowRight size={16} />
          </Link>
        </Card>
      ) : (
        <>
          <div className="stat-grid">
            <Stat
              tone="blue"
              icon={ShieldCheck}
              label="Pass rate (latest run)"
              value={percent(base.safetyScore)}
              sub={`${shortModel(base.model)} · prompt v${base.promptVersion}`}
            />
            <Stat
              tone="red"
              icon={ShieldAlert}
              label="Failed or unsure"
              value={base.counts.fail + base.counts.needsReview}
              sub={`${base.counts.fail} fail · ${base.counts.needsReview} need review`}
            />
            <Stat
              tone="amber"
              icon={Scale}
              label="Judge agrees with people"
              value={agreement.rate == null ? '—' : percent(agreement.rate)}
              sub={agreement.reviewed ? `on ${agreement.reviewed} reviewed answers` : 'No reviews yet'}
            />
            <Stat
              tone="green"
              icon={FileText}
              label="Prompt in use"
              value={`v${data.activePromptVersion}`}
              sub={
                <Link className="link" to="/fixes">
                  Fixes and versions
                </Link>
              }
            />
          </div>

          <div className="row-2">
            <Card
              title={`Results by language — ${base.code}`}
              icon={Languages}
              action={
                <Link className="link" to={`/runs/${base.code}`}>
                  Open run <ArrowRight size={16} />
                </Link>
              }
            >
              <LanguageBars languages={base.languages} />
            </Card>
            <Card title="Needs a person" icon={ShieldAlert}>
              <p className="big-number">{data.awaitingReview}</p>
              <p className="muted">answers in the latest run waiting for a native speaker: unsure results and random spot-checks.</p>
              <Link className="btn-primary inline" to="/reviews">
                Open review queue <ArrowRight size={16} />
              </Link>
            </Card>
          </div>
        </>
      )}

      {retest && (
        <Card
          title={`Latest fix: prompt v${retest.retest.promptVersion}`}
          icon={Wrench}
          action={
            <Link className="link" to={`/runs/${retest.retest.code}/comparison`}>
              Before and after <ArrowRight size={16} />
            </Link>
          }
        >
          <div className="change-counts">
            <ChangeCount tone="good" value={retest.counts.fixed} label="fixed" />
            <ChangeCount tone="bad" value={retest.counts.stillFailing} label="still failing" />
            <ChangeCount tone="bad" value={retest.counts.newFailures} label="new failures" />
            <ChangeCount tone="neutral" value={retest.counts.stillPassing} label="still passing" />
          </div>
        </Card>
      )}

      <Card title="Recent runs" icon={History}>
        <RunsTable runs={data.recentRuns} />
      </Card>
    </div>
  )
}

function Stat({ tone, icon: Icon, label, value, sub }) {
  return (
    <div className={`stat stat-${tone}`}>
      <div className="stat-icon">
        <Icon size={24} />
      </div>
      <div>
        <div className="stat-label">{label}</div>
        <div className="stat-value">{value}</div>
        <div className="stat-sub">{sub}</div>
      </div>
    </div>
  )
}
