import { useState } from 'react'
import { Link } from 'react-router-dom'
import {
  BookmarkCheck,
  ChevronDown,
  CircleCheck,
  Database,
  History,
  Loader2,
  Power,
  PowerOff,
  RotateCcw,
  Scale,
  Sparkles,
  TriangleAlert,
} from 'lucide-react'
import { getPrompts, getRuns, getScenarioFixes, learnFixesFromRun, switchScenarioFix } from '../api/client'
import { useLoad } from '../hooks/useLoad'
import { Card, ErrorState, LangChip, Loading, Notice } from '../components/ui'
import PromptStatus from '../components/PromptStatus'
import { formatDate } from '../utils/format'
import { FIX_STATES, filterFixes, fixableRuns, usageText } from '../utils/fixes'
import { useSettings } from '../state/settings'
import { useSigner } from '../state/identity'

/**
 * Fixes: when an answer fails, the AI judge writes what the assistant should do differently, and it is
 * saved for that scenario and language. The next run gives the assistant that fix with the same
 * question. One card per scenario, however many languages and attempts failed.
 */
export default function Fixes() {
  const runs = useLoad(getRuns, [], { shouldPoll: (d) => Boolean(d?.some((r) => r.status === 'running')) })
  const running = Boolean(runs.data?.some((r) => r.status === 'running'))
  const fixes = useLoad(getScenarioFixes, [], { shouldPoll: () => running, intervalMs: 4000 })
  const prompts = useLoad(getPrompts, [])

  if ((fixes.loading && !fixes.data) || (runs.loading && !runs.data)) return <Loading />
  if (fixes.error) return <ErrorState error={fixes.error} />
  if (runs.error) return <ErrorState error={runs.error} />

  return (
    <div className="page fixes-page">
      <HowItWorks />
      <SavedFixes data={fixes.data} running={running} onChange={fixes.reload} />
      <LearnFromRun runs={fixableRuns(runs.data)} onLearned={fixes.reload} />
      {prompts.data && <PromptVersions prompts={prompts.data} />}
    </div>
  )
}

const HOW = [
  ['An answer fails', 'The rule check or the AI judge finds it unsafe'],
  ['The AI judge writes a fix', 'What to do differently, saved for that scenario and language'],
  ['A person decides', 'Fixes start switched off, so tests measure the model alone; switch one on to try it'],
  ['Checked again', 'If it passes, the fix works. If not, the judge rewrites it'],
]

function HowItWorks() {
  return (
    <ol className="fix-how" aria-label="How fixes work">
      {HOW.map(([title, text], i) => (
        <li key={title}>
          <span className="fix-how-num">{i + 1}</span>
          <div>
            <b>{title}</b>
            <span>{text}</span>
          </div>
        </li>
      ))}
    </ol>
  )
}

const STATE_FILTERS = ['waiting', 'working', 'failing', 'off']

function SavedFixes({ data, running, onChange }) {
  const [language, setLanguage] = useState('')
  const [state, setState] = useState('')
  const all = data.scenarios.flatMap((s) => s.fixes)
  const languages = [...new Map(all.map((f) => [f.language, f.languageName])).entries()]
  const counts = Object.fromEntries(STATE_FILTERS.map((k) => [k, all.filter((f) => f.state === k).length]))
  const shown = filterFixes(data.scenarios, { language, state })

  return (
    <Card title="Saved fixes" icon={Database}>
      <div className="sfix-summary">
        <button className={`sfix-stat ${!state ? 'on' : ''}`} onClick={() => setState('')}>
          <span className="fix-big">{data.total}</span>
          <span className="small">
            fix{data.total === 1 ? '' : 'es'} saved
            <br />
            <span className="muted">across {data.scenarios.length} scenario{data.scenarios.length === 1 ? '' : 's'}</span>
          </span>
        </button>
        {STATE_FILTERS.map((k) => (
          <button
            key={k}
            className={`sfix-stat tone-${FIX_STATES[k].tone} ${state === k ? 'on' : ''}`}
            onClick={() => setState(state === k ? '' : k)}
            aria-pressed={state === k}
          >
            <span className="fix-big">{counts[k]}</span>
            <span className="small">{FIX_STATES[k].short}</span>
          </button>
        ))}
      </div>

      {running && (
        <Notice kind="info">
          A test is running. Questions with a saved fix are being asked with it; this page updates as answers come
          in.
        </Notice>
      )}

      {data.total === 0 ? (
        <div className="sfix-empty">
          <CircleCheck size={22} />
          <div>
            <b>No fixes saved yet.</b>
            <p className="muted small">
              When an answer fails in a test, the AI judge&apos;s fix appears here, and the next test uses it. For a
              test from before fixes were saved, use <i>Save fixes from an earlier test</i> below.
            </p>
          </div>
        </div>
      ) : (
        <>
          {languages.length > 1 && (
            <div className="fix-filter" role="group" aria-label="Filter by language">
              <button className={!language ? 'on' : ''} onClick={() => setLanguage('')}>
                All languages
              </button>
              {languages.map(([code, name]) => (
                <button key={code} className={language === code ? 'on' : ''} onClick={() => setLanguage(code)}>
                  <LangChip code={code} /> {name}
                </button>
              ))}
            </div>
          )}
          {shown.length === 0 ? (
            <p className="muted">No fixes match these filters.</p>
          ) : (
            <ul className="fix-failure-list">
              {shown.map((s) => (
                <ScenarioCard key={s.code} scenario={s} onChange={onChange} />
              ))}
            </ul>
          )}
        </>
      )}
    </Card>
  )
}

function ScenarioCard({ scenario: s, onChange }) {
  return (
    <li className="fix-failure sfix-card">
      <div className="fix-failure-title">
        <span className={`tag tag-${s.kind}`}>{s.kind === 'urgent' ? 'Urgent' : 'Control'}</span>
        <b>{s.code}</b> {s.title}
        <span className="muted small sfix-count">
          {s.fixes.length} language{s.fixes.length === 1 ? '' : 's'}
        </span>
      </div>
      <ul className="sfix-list">
        {s.fixes.map((f) => (
          <FixRow key={f.id} fix={f} onChange={onChange} />
        ))}
      </ul>
    </li>
  )
}

function FixRow({ fix: f, onChange }) {
  const settings = useSettings()
  const { signed } = useSigner()
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)
  const meta = FIX_STATES[f.state] ?? FIX_STATES.waiting
  const label = f.state === 'waiting' && f.revision > 1 ? 'Rewritten, waiting for the next run' : meta.label

  const toggle = () =>
    signed(async (by) => {
      setBusy(true)
      setError(null)
      try {
        await switchScenarioFix(f.id, !f.active, by)
        await onChange()
      } catch (err) {
        setError(err.message)
      } finally {
        setBusy(false)
      }
    })

  return (
    <li className={`sfix sfix-${f.state}`}>
      <div className="sfix-head">
        <LangChip code={f.language} />
        <b>{f.languageName}</b>
        <span className={`fix-tone tone-${meta.tone}`}>{label}</span>
        {f.revision > 1 && <span className="muted small">version {f.revision}</span>}
        {!settings?.readOnly && (
          <button className="btn-ghost small-btn sfix-toggle" disabled={busy} onClick={toggle}>
            {busy ? <Loader2 size={14} className="spin" /> : f.active ? <PowerOff size={14} /> : <Power size={14} />}
            {f.active ? 'Turn off' : 'Turn on'}
          </button>
        )}
      </div>

      <div className="fix-ai sfix-fix">
        <div className="fix-ai-head">
          <Sparkles size={16} />
          <b>AI judge&apos;s fix</b>
          <span className="muted small">
            {f.active ? 'given to the model with this question' : 'off: not given to the model, so tests measure it alone'}
          </span>
        </div>
        <p className="fix-ai-fix sfix-text">{f.fixText}</p>
      </div>

      {f.failureReason && (
        <p className="sfix-reason small">
          <TriangleAlert size={14} />
          <span>
            <b>Why it failed:</b> {f.failureReason}
          </span>
        </p>
      )}

      <div className="sfix-meta small muted">
        <span>
          <Scale size={14} /> Learned from{' '}
          {f.sourceRunCode ? (
            <Link className="link" to={`/runs/${f.sourceRunCode}`}>
              {f.sourceRunName ?? f.sourceRunCode}
            </Link>
          ) : (
            'a test'
          )}
          {f.sourceResultId && (
            <>
              {' · '}
              <Link className="link" to={`/results/${f.sourceResultId}`}>
                the failed answer
              </Link>
            </>
          )}
          {' · '}
          {formatDate(f.updatedAt)}
        </span>
        <span className={`sfix-usage tone-${meta.tone}`}>
          {f.state === 'working' ? <BookmarkCheck size={14} /> : <RotateCcw size={14} />} {usageText(f)}
        </span>
        {!f.active && f.changedBy && (
          <span>
            Turned off by {f.changedBy}
            {f.changeNote && `: ${f.changeNote}`}
          </span>
        )}
      </div>
      {error && <Notice kind="error">{error}</Notice>}
    </li>
  )
}

function LearnFromRun({ runs, onLearned }) {
  const settings = useSettings()
  const [code, setCode] = useState('')
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState(null)
  if (settings?.readOnly || runs.length === 0) return null
  const chosen = code || runs[0].code

  const learn = async () => {
    setBusy(true)
    setMessage(null)
    try {
      const { saved } = await learnFixesFromRun(chosen)
      await onLearned()
      setMessage(
        saved
          ? { kind: 'success', text: `Saved ${saved} fix${saved === 1 ? '' : 'es'} from this test.` }
          : { kind: 'info', text: 'Nothing new: the fixes from this test are already saved.' },
      )
    } catch (err) {
      setMessage({ kind: 'error', text: err.message })
    } finally {
      setBusy(false)
    }
  }

  return (
    <Card title="Save fixes from an earlier test" icon={History}>
      <p className="muted small">
        New tests save their fixes on their own. A test from before then can be added here: each of its failed
        answers is saved once per scenario and language.
      </p>
      <div className="sfix-learn">
        <label className="select-wrap">
          <span className="sr-only">Test</span>
          <select value={chosen} onChange={(e) => setCode(e.target.value)} aria-label="Test">
            {runs.map((r) => (
              <option key={r.code} value={r.code}>
                {r.name} · {r.counts.fail + r.counts.needsReview} not passed
              </option>
            ))}
          </select>
          <ChevronDown size={16} />
        </label>
        <button className="btn-primary" disabled={busy} onClick={learn}>
          {busy ? <Loader2 size={18} className="spin" /> : <Database size={18} />}
          Save its fixes
        </button>
      </div>
      {message && <Notice kind={message.kind}>{message.text}</Notice>}
    </Card>
  )
}

function PromptVersions({ prompts }) {
  const sorted = [...prompts].sort((a, b) => b.version - a.version)
  return (
    <Card title="Prompt versions" icon={History}>
      <p className="muted small">
        The general instructions every question is asked with. A saved fix is added to them only when a person
        switches it on, and only for the question it was learned from.
      </p>
      <ol className="fix-history">
        {sorted.map((p) => (
          <li key={p.version} className={`fix-history-${p.status}`}>
            <span className="fix-history-dot" />
            <div className="fix-history-body">
              <div className="fix-history-head">
                <Link className="run-id" to={`/fixes/${p.version}`}>
                  v{p.version}
                </Link>
                <PromptStatus prompt={p} />
                <span className="muted small">
                  {formatDate(p.createdAt)}
                  {p.decidedBy && ` · decided by ${p.decidedBy}`}
                </span>
              </div>
              <p className="small">{p.summary}</p>
            </div>
          </li>
        ))}
      </ol>
    </Card>
  )
}
