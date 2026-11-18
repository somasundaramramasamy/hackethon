import { Link } from 'react-router-dom'
import { BarChart3, History as HistoryIcon } from 'lucide-react'
import { getPrompts, getRuns } from '../api/client'
import { useLoad } from '../hooks/useLoad'
import { Card, ErrorState, LangChip, Loading } from '../components/ui'
import RunsTable from '../components/RunsTable'
import { formatDate, shortModel } from '../utils/format'

const LANGS = ['EN', 'TA', 'HI', 'ML']

/**
 * Every test so far, with metrics that stay honest across tests: counts, and pass rates per model
 * from each model's latest full test. Tests differ in model, prompt and scenarios, so their pass
 * rates are never averaged together.
 */
export default function History() {
  const runs = useLoad(getRuns, [], { shouldPoll: (d) => d?.some((r) => r.status === 'running') })
  const prompts = useLoad(getPrompts, [])
  if ((runs.loading && !runs.data) || (prompts.loading && !prompts.data)) return <Loading />
  if (runs.error) return <ErrorState error={runs.error} />

  return (
    <div className="page">
      <Card title="Across all tests" icon={BarChart3}>
        <Totals runs={runs.data} prompts={prompts.data ?? []} />
        <ModelTable runs={runs.data} />
      </Card>
      <Card title="All tests" icon={HistoryIcon}>
        <RunsTable runs={runs.data} />
      </Card>
    </div>
  )
}

function Totals({ runs, prompts }) {
  const finished = runs.filter((r) => r.status === 'completed')
  const baselines = finished.filter((r) => r.type === 'baseline').length
  const retests = finished.filter((r) => r.type === 'retest').length
  const answers = finished.reduce((sum, r) => sum + r.completedTests, 0)
  const models = new Set(finished.map((r) => r.model)).size
  const reviewed = runs.reduce((sum, r) => sum + r.reviewed, 0)
  const fixes = prompts.filter((p) => p.basedOnVersion != null)
  const adopted = fixes.filter((p) => p.status === 'active' || p.status === 'retired').length
  const rejected = fixes.filter((p) => p.status === 'rejected').length
  return (
    <div className="history-totals">
      <Total value={finished.length} label="tests run" sub={`${baselines} ${baselines === 1 ? 'test' : 'tests'} · ${retests} ${retests === 1 ? 'retest' : 'retests'}`} />
      <Total value={answers} label="answers checked" sub="each by the rule check and the AI judge" />
      <Total value={models} label={models === 1 ? 'model tested' : 'models tested'} sub="as the assistant under test" />
      <Total value={reviewed} label="reviewed by people" sub={<Link to="/reviews">Open the review queue</Link>} />
      <Total
        value={fixes.length}
        label={fixes.length === 1 ? 'fix proposed' : 'fixes proposed'}
        sub={`${rejected} rejected · ${adopted} adopted`}
      />
    </div>
  )
}

function Total({ value, label, sub }) {
  return (
    <div className="history-total">
      <span className="history-total-value">{value}</span>
      <span className="history-total-label">{label}</span>
      <span className="muted small">{sub}</span>
    </div>
  )
}

/**
 * Pass rate by language for the latest full test of each model and prompt version, and how far the
 * weakest language trails English. A fix changes the prompt, so its effect shows as a new row.
 */
function ModelTable({ runs }) {
  const latest = latestFullTests(runs)
  if (!latest.length) return <p className="muted">Pass rates appear once a test covers every language.</p>
  return (
    <>
      <h3 className="history-sub">Pass rate by language, latest full test of each model and prompt</h3>
      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>Model · prompt</th>
              {LANGS.map((l) => (
                <th key={l} className="num">
                  <LangChip code={l} />
                </th>
              ))}
              <th className="num">Gap to English</th>
              <th>Test</th>
            </tr>
          </thead>
          <tbody>
            {latest.map((r) => {
              const rates = Object.fromEntries(LANGS.map((l) => [l, rate(r, l)]))
              const others = LANGS.slice(1).map((l) => rates[l]).filter((x) => x != null)
              const gap = rates.EN != null && others.length ? Math.round(rates.EN - Math.min(...others)) : null
              return (
                <tr key={r.code}>
                  <td>
                    <b>{shortModel(r.model)}</b> <span className="muted">· v{r.promptVersion}</span>
                  </td>
                  {LANGS.map((l) => (
                    <td key={l} className={`num ${l !== 'EN' && rates[l] != null && rates[l] < rates.EN ? 'fail' : ''}`}>
                      {rates[l] == null ? '—' : `${Math.round(rates[l])}%`}
                    </td>
                  ))}
                  <td className="num">
                    {gap == null ? '—' : gap > 0 ? <b className="fail">up to {gap} pts lower</b> : 'none'}
                  </td>
                  <td>
                    <Link to={`/runs/${r.code}`}>{r.name}</Link>
                    <div className="muted small">
                      {r.completedTests} answers · {formatDate(r.startedAt)}
                    </div>
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
      <p className="muted small history-note">
        Rate = passed ÷ checked, before people review. Different tests use different prompts and scenarios, so their
        rates are shown side by side, never averaged.
      </p>
    </>
  )
}

function rate(run, language) {
  const c = run.languages.find((l) => l.language === language)?.counts
  if (!c) return null
  const checked = c.pass + c.fail + c.needsReview
  return checked ? (100 * c.pass) / checked : null
}

const scenarioCount = (run) => Number(run.suite?.match(/(\d+) scenarios?/)?.[1] ?? 0)

/** Per model and prompt, the newest finished test that asked every language and nearly every scenario. */
function latestFullTests(runs) {
  const baselines = runs.filter((r) => r.type === 'baseline' && r.status === 'completed')
  const most = Math.max(0, ...baselines.map(scenarioCount))
  const full = baselines.filter(
    (r) => LANGS.every((l) => r.languages.some((x) => x.language === l)) && scenarioCount(r) >= most * 0.8,
  )
  const latest = new Map()
  full.forEach((r) => {
    const key = `${r.model}|${r.promptVersion}`
    if (!latest.has(key)) latest.set(key, r) // runs arrive newest first
  })
  return [...latest.values()]
}
