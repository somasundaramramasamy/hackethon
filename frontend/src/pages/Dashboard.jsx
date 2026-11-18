import { Link } from 'react-router-dom'
import { ClipboardCheck, FileText, FlaskConical, History, ArrowRight, BellRing, Scale, Wrench, CirclePlay } from 'lucide-react'
import { getDashboard } from '../api/client'
import { useLoad } from '../hooks/useLoad'
import { Card, ChangeCount, ErrorState, Loading, Notice, Progress } from '../components/ui'
import LanguageBars from '../components/LanguageBars'
import RunsTable from '../components/RunsTable'
import { formatDate, percent, shortModel } from '../utils/format'

/**
 * The overview: the latest test, clearly labelled as one test, and what needs a person across tests.
 * Each test's full results live on its own page.
 */
export default function Dashboard() {
  const { data, error, loading } = useLoad(getDashboard, [], { shouldPoll: (d) => Boolean(d?.running) })
  if (loading && !data) return <Loading />
  if (error) return <ErrorState error={error} />

  const { latestBaseline: base, latestRetest: retest, running } = data
  return (
    <div className="page dashboard">
      {running && (
        <Notice>
          Test <Link to={`/runs/${running.code}`}>{running.code}</Link> is in progress: {running.completedTests} of{' '}
          {running.plannedTests} answers checked.
          <Progress done={running.completedTests} total={running.plannedTests} />
        </Notice>
      )}

      {!base ? (
        <Card title="No tests yet" icon={History}>
          <p>
            A test asks the same {data.activeScenarios} everyday health questions in English, Tamil, Hindi and
            Malayalam, checks every answer, and shows where safety drops in a language. Results appear here once the
            first test finishes.
          </p>
          <Link className="btn-primary inline" to="/runs">
            Start your first test <ArrowRight size={16} />
          </Link>
        </Card>
      ) : (
        <div className="row-2">
          <LatestTest base={base} />
          <NeedsYou data={data} retest={retest} />
        </div>
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

      <Card title="Recent tests" icon={History}>
        <RunsTable runs={data.recentRuns} />
      </Card>
    </div>
  )
}

function LatestTest({ base }) {
  const unsure = base.counts.fail + base.counts.needsReview
  return (
    <Card
      title="Latest test"
      icon={FlaskConical}
      action={
        <Link className="link" to={`/runs/${base.code}`}>
          Open this test <ArrowRight size={16} />
        </Link>
      }
    >
      <h3 className="latest-name">
        <Link to={`/runs/${base.code}`}>{base.name}</Link>
      </h3>
      <p className="muted small latest-meta">
        {base.code} · {shortModel(base.model)} · prompt v{base.promptVersion} ·{' '}
        {base.completedTests} answers · {formatDate(base.startedAt)}
      </p>
      <div className="latest-figures">
        <div>
          <div className="latest-value">{percent(base.safetyScore)}</div>
          <div className="muted small">passed</div>
        </div>
        <div>
          <div className="latest-value bad">{unsure}</div>
          <div className="muted small">
            failed or unsure ({base.counts.fail} fail · {base.counts.needsReview} unsure)
          </div>
        </div>
      </div>
      <LanguageBars languages={base.languages} />
      <Link className="btn-secondary inline latest-new" to="/runs">
        <CirclePlay size={16} /> Start a new test
      </Link>
    </Card>
  )
}

function NeedsYou({ data, retest }) {
  const { agreement } = data
  const fixVersion = retest?.retest.promptVersion
  const fixAdopted = fixVersion != null && fixVersion === data.activePromptVersion
  return (
    <Card title="Needs you" icon={BellRing}>
      <ul className="needs-list">
        <li>
          <ClipboardCheck size={18} />
          <div>
            <b>{data.awaitingReview} answers</b> in the latest test wait for a person: unsure results and random
            spot-checks.
            <Link className="link block" to="/reviews">
              Open the review queue <ArrowRight size={14} />
            </Link>
          </div>
        </li>
        {retest && !fixAdopted && (
          <li>
            <Wrench size={18} />
            <div>
              <b>Fix v{fixVersion} was retested</b>: {retest.counts.fixed} fixed, {retest.counts.newFailures} new
              failures. It is not in use; adopt it or ask for a better one.
              <Link className="link block" to={`/fixes/${fixVersion}`}>
                Decide on fix v{fixVersion} <ArrowRight size={14} />
              </Link>
            </div>
          </li>
        )}
        <li>
          <Scale size={18} />
          <div>
            <b>Judge agrees with people:</b>{' '}
            {agreement.reviewed ? `${percent(agreement.rate)} of ${agreement.reviewed} reviewed answers` : 'no reviews yet'}.
          </div>
        </li>
        <li>
          <FileText size={18} />
          <div>
            <b>Prompt in use:</b> v{data.activePromptVersion}.
            <Link className="link block" to="/fixes">
              Fixes and versions <ArrowRight size={14} />
            </Link>
          </div>
        </li>
      </ul>
    </Card>
  )
}
