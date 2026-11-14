import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import {
  HeartPulse,
  Shield,
  Globe,
  Calendar,
  Sparkles,
  TriangleAlert,
  CircleCheck,
  Check,
  MessageSquareText,
  Bot,
  ChartColumn,
  Search,
  BookOpen,
  ExternalLink,
  Scale,
  User,
  ShieldCheck,
  Target,
  Languages,
  FileText,
  ChevronDown,
  SquarePen,
  RefreshCw,
  Info,
  Eye,
  EyeOff,
} from 'lucide-react'
import { getRunReview, submitReview } from '../api/client'
import { reviewOptions, verdictOptions } from '../api/constants'
import { Card, Loading, ErrorState } from '../components/ui'
import { formatDate } from '../utils/format'

const steps = [
  { n: 1, title: 'Test Execution', sub: 'Generate responses' },
  { n: 2, title: 'Investigation', sub: 'Find issues & evidence' },
  { n: 3, title: 'Evaluation (Judge)', sub: 'Score & decision' },
  { n: 4, title: 'Human Review', sub: 'Review and take action' },
  { n: 5, title: 'Retest', sub: 'Validate fixes' },
]

export default function RunReview() {
  const { runId } = useParams()
  const [run, setRun] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    getRunReview(runId).then(setRun).catch((e) => setError(e.message))
  }, [runId])

  if (error) return <ErrorState message={error} />
  if (!run) return <Loading />

  return (
    <div className="review">
      <div className="page-head">
        <h1>Test Run Review</h1>
        <p>Review AI responses, evaluation results and agent findings for multilingual safety testing.</p>
      </div>

      <RunHeader run={run} />
      <Pipeline current={run.pipelineStep} />

      <div className="review-grid">
        <div className="col">
          <Card
            title="Prompt (Test Scenario)"
            icon={MessageSquareText}
            action={<span className="tag tag-purple">Multilingual</span>}
            className="prompt-card"
          >
            <div className="prompt-box">{run.prompt}</div>
            <div className="sub-head">
              <Bot size={22} className="card-icon" />
              <h3>AI Assistant Responses</h3>
            </div>
            <div className="responses">
              {run.responses.map((r) => (
                <Response key={r.lang} r={r} />
              ))}
            </div>
          </Card>
        </div>

        <div className="col">
          <Card
            title="Evaluation Summary"
            icon={ChartColumn}
            action={<span className="muted small">Overall Safety Score</span>}
          >
            <div className="eval">
              <Gauge value={run.overallScore} />
              <ul className="scores">
                {run.scores.map((s) => (
                  <li key={s.key}>
                    <ScoreIcon k={s.key} />
                    <span className="score-label">{s.label}</span>
                    <span className="score-val">{s.value.toFixed(2)}</span>
                    <span className={`tag tag-${s.tone}`}>{s.rating}</span>
                  </li>
                ))}
              </ul>
            </div>
          </Card>

          <Card title="Investigator Findings" icon={Search}>
            <ul className="findings">
              {run.findings.map((f) => (
                <li key={f.text}>
                  {f.ok ? <CircleCheck size={18} className="ok" /> : <Info size={18} className="warn" />}
                  <span>{f.text}</span>
                </li>
              ))}
            </ul>
          </Card>

          <Card
            title="Evidence / Sources"
            icon={BookOpen}
            action={<button className="link">View All ({run.sources.length})</button>}
          >
            <ul className="sources">
              {run.sources.slice(0, 2).map((s) => (
                <li key={s.title}>
                  <span className="source-logo">{s.short}</span>
                  <div className="source-text">
                    <b>
                      {s.org} – {s.title}
                    </b>
                    <span>{s.url}</span>
                  </div>
                  <a href={s.url} target="_blank" rel="noreferrer noopener" aria-label={`Open ${s.title}`}>
                    <ExternalLink size={18} />
                  </a>
                </li>
              ))}
            </ul>
          </Card>

          <Card title="Judge (AI) Decision" icon={Scale}>
            <div className="judge">
              <div className="judge-title">
                <TriangleAlert size={20} /> {run.judge.decision}
              </div>
              <p>
                <b>Reason:</b> {run.judge.reason}
              </p>
            </div>
          </Card>
        </div>

        <div className="col">
          <HumanReview run={run} />
        </div>
      </div>
    </div>
  )
}

function RunHeader({ run }) {
  return (
    <section className="card run-header">
      <div className="run-icon">
        <HeartPulse size={34} />
      </div>
      <div className="run-main">
        <div className="run-code">{run.id}</div>
        <h2>{run.title}</h2>
        <div className="meta">
          <span className="meta-chip">
            <Shield size={14} /> {run.category}
          </span>
          <span className="meta-chip">
            <Globe size={14} /> Multilingual ({run.languages.join(', ')})
          </span>
          <span className="meta-chip plain">
            <Calendar size={14} /> {formatDate(run.startedAt)}
          </span>
          <span className="meta-chip">
            <Sparkles size={14} /> {run.model}
          </span>
          {run.verification === 'unverified' && (
            <span className="meta-chip unverified" title="Not yet confirmed by a native-speaker reviewer">
              Unverified
            </span>
          )}
        </div>
      </div>
      <div className="alert-pill">
        <TriangleAlert size={22} /> Needs Human Review
      </div>
      <div className="run-status">
        <span className="muted">Run Status</span>
        <b>
          <CircleCheck size={22} className="ok" /> {run.runStatus}
        </b>
      </div>
    </section>
  )
}

function Pipeline({ current }) {
  return (
    <ol className="pipeline">
      {steps.map((s) => {
        const state = s.n < current ? 'done' : s.n === current ? 'current' : 'todo'
        return (
          <li key={s.n} className={`step step-${state}`}>
            <span className="step-dot">{state === 'done' ? <Check size={18} strokeWidth={3} /> : s.n}</span>
            <span className="step-text">
              <b>
                {s.n}. {s.title}
              </b>
              <span>{s.sub}</span>
            </span>
          </li>
        )
      })}
    </ol>
  )
}

function Response({ r }) {
  const [showTranslation, setShowTranslation] = useState(false)
  return (
    <article className="response">
      <header>
        <span className={`lang-chip lang-${r.lang.toLowerCase()}`}>{r.lang}</span>
        <b>{r.name}</b>
        <span className={`verdict verdict-${r.verdict}`}>
          <Check size={14} strokeWidth={3} /> {r.verdict === 'safe' ? 'Safe' : 'Unsafe'}
        </span>
      </header>
      <p lang={r.lang.toLowerCase()} className="response-text">
        {r.text}
      </p>
      {r.translation && (
        <>
          <button className="translate-btn" onClick={() => setShowTranslation((v) => !v)}>
            {showTranslation ? <EyeOff size={14} /> : <Eye size={14} />}
            {showTranslation ? 'Hide' : 'Show'} English translation
          </button>
          {showTranslation && <p className="translation">{r.translation}</p>}
        </>
      )}
    </article>
  )
}

function Gauge({ value }) {
  const r = 46
  const c = 2 * Math.PI * r
  return (
    <div className="gauge">
      <svg viewBox="0 0 110 110" width="96" height="96">
        <circle cx="55" cy="55" r={r} fill="none" stroke="#e6f4ea" strokeWidth="9" />
        <circle
          cx="55"
          cy="55"
          r={r}
          fill="none"
          stroke="#1faa59"
          strokeWidth="9"
          strokeLinecap="round"
          strokeDasharray={`${(value / 100) * c} ${c}`}
          transform="rotate(-90 55 55)"
        />
      </svg>
      <b>{value}%</b>
    </div>
  )
}

function ScoreIcon({ k }) {
  const map = {
    groundedness: [ShieldCheck, 'ok'],
    harmfulness: [TriangleAlert, 'warn'],
    language: [Languages, 'info'],
    safety: [Target, 'bad'],
  }
  const [Icon, cls] = map[k] ?? [Info, '']
  return <Icon size={17} className={cls} />
}

function HumanReview({ run }) {
  const [assessment, setAssessment] = useState({
    safety: 'Acceptable',
    groundedness: 'Acceptable',
    language: 'Needs Improvement',
    verdict: 'Needs Fix',
  })
  const [comment, setComment] = useState(
    'The medical guidance is correct and safe in all languages. However, the Tamil response can be improved for clarity and standard medical terminology. Recommend a minor fix and retest.',
  )
  const [busy, setBusy] = useState(null)
  const [toast, setToast] = useState(null)
  const MAX = 500

  const fields = [
    { key: 'safety', label: 'Safety', icon: ShieldCheck, options: reviewOptions },
    { key: 'groundedness', label: 'Groundedness', icon: Target, options: reviewOptions },
    { key: 'language', label: 'Language Quality', icon: Languages, options: reviewOptions },
    { key: 'verdict', label: 'Overall Verdict', icon: FileText, options: verdictOptions },
  ]

  const tone = (v) => (v === 'Acceptable' || v === 'Safe' ? 'good' : v === 'Unsafe' ? 'bad' : 'mid')

  async function act(action, message) {
    if (!comment.trim()) {
      setToast({ type: 'error', text: 'Please add a reviewer comment before taking an action.' })
      return
    }
    setBusy(action)
    try {
      await submitReview(run.id, { assessment, comment, action })
      setToast({ type: 'success', text: message })
    } catch (e) {
      setToast({ type: 'error', text: e.message })
    } finally {
      setBusy(null)
    }
  }

  return (
    <Card title="Human Review" icon={User} className="human-review">
      <div className="hr-alert">
        <TriangleAlert size={30} />
        <div>
          <b>Needs Human Review</b>
          <span>AI decision: Review required</span>
        </div>
      </div>

      <h4>Reviewer Assessment</h4>
      <div className="assess">
        {fields.map(({ key, label, icon: Icon, options }) => (
          <label key={key} className="assess-row">
            <Icon size={19} className="info" />
            <span>{label}</span>
            <span className={`select-wrap assess-select tone-${tone(assessment[key])}`}>
              <select value={assessment[key]} onChange={(e) => setAssessment({ ...assessment, [key]: e.target.value })}>
                {options.map((o) => (
                  <option key={o}>{o}</option>
                ))}
              </select>
              <ChevronDown size={16} />
            </span>
          </label>
        ))}
      </div>

      <h4>
        Reviewer Comments <span className="req">*</span>
      </h4>
      <textarea
        className="comment"
        value={comment}
        maxLength={MAX}
        rows={5}
        onChange={(e) => setComment(e.target.value)}
        aria-label="Reviewer comments"
      />
      <div className="counter">
        {comment.length} / {MAX}
      </div>

      {toast && (
        <div className={`toast toast-${toast.type}`} role="status">
          {toast.text}
        </div>
      )}

      <div className="actions">
        <button className="action action-approve" disabled={!!busy} onClick={() => act('approve', 'Marked as safe and approved.')}>
          <Check size={26} />
          <b>{busy === 'approve' ? 'Saving…' : 'Approve'}</b>
          <span>Accept as safe</span>
        </button>
        <button className="action action-fix" disabled={!!busy} onClick={() => act('request_fix', 'Fix requested. The agent will propose an improved response for approval.')}>
          <SquarePen size={26} />
          <b>{busy === 'request_fix' ? 'Saving…' : 'Request Fix'}</b>
          <span>Generate improved response</span>
        </button>
        <button className="action action-retest" disabled={!!busy} onClick={() => act('retest', 'Retest queued: failing case, English, and all controls.')}>
          <RefreshCw size={26} />
          <b>{busy === 'retest' ? 'Saving…' : 'Retest'}</b>
          <span>Run test again after fix</span>
        </button>
      </div>
    </Card>
  )
}
