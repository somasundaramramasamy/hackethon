import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { CirclePlay, History } from 'lucide-react'
import { getRuns, startRun } from '../api/client'
import { useLoad } from '../hooks/useLoad'
import { Card, ErrorState, Loading, Notice } from '../components/ui'
import RunsTable from '../components/RunsTable'
import { useSettings } from '../state/settings'
import { useReviewerName } from '../state/reviewer'

export default function Runs() {
  const { data: runs, error, loading } = useLoad(getRuns, [], {
    shouldPoll: (d) => d?.some((r) => r.status === 'running'),
  })
  return (
    <div className="page">
      <NewRun running={runs?.some((r) => r.status === 'running')} />
      <Card title="All runs" icon={History}>
        {loading && !runs ? <Loading /> : error ? <ErrorState error={error} /> : <RunsTable runs={runs} />}
      </Card>
    </div>
  )
}

function NewRun({ running }) {
  const settings = useSettings()
  const name = useReviewerName()
  const navigate = useNavigate()
  const [model, setModel] = useState('')
  const [judge, setJudge] = useState('')
  const [repeats, setRepeats] = useState(1)
  const [languages, setLanguages] = useState(['EN', 'TA', 'HI', 'ML'])
  const [busy, setBusy] = useState(false)
  const [failure, setFailure] = useState(null)

  if (!settings) return null
  const configured = settings.assistantConfigured && settings.judgeConfigured
  const disabled = settings.readOnly || !configured || running || busy || languages.length === 0

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true)
    setFailure(null)
    try {
      const run = await startRun({
        model: model || undefined,
        judgeModel: judge || undefined,
        repeats,
        languages,
        startedBy: name || undefined,
      })
      navigate(`/runs/${run.code}`)
    } catch (err) {
      setFailure(err.message)
      setBusy(false)
    }
  }

  const toggle = (code) =>
    setLanguages((ls) => (ls.includes(code) ? ls.filter((l) => l !== code) : [...ls, code]))

  return (
    <Card title="Start a run" icon={CirclePlay}>
      <p className="muted card-intro">
        Asks every active scenario in the chosen languages. Each answer gets a rule check and an AI judge; anything
        unsafe or unsure goes to the review queue.
      </p>
      {!configured && !settings.readOnly && (
        <Notice kind="warning">
          No model API key is set. Add <code>OPENROUTER_API_KEY</code> to <code>backend/.env</code> and restart the
          backend.
        </Notice>
      )}
      <form className="run-form" onSubmit={submit}>
        <label className="field">
          <span className="field-label">Assistant under test</span>
          <select value={model} onChange={(e) => setModel(e.target.value)}>
            {settings.assistantModels.map((m, i) => (
              <option key={m.id} value={i === 0 ? '' : m.id}>
                {m.label}
              </option>
            ))}
          </select>
          <span className="field-hint">{settings.assistantModels.find((m) => m.id === model)?.note ?? settings.assistantModels[0]?.note}</span>
        </label>
        <label className="field">
          <span className="field-label">Judge</span>
          {settings.judgeModels.length > 1 ? (
            <select value={judge} onChange={(e) => setJudge(e.target.value)}>
              {settings.judgeModels.map((m, i) => (
                <option key={m.id} value={i === 0 ? '' : m.id}>
                  {m.label}
                </option>
              ))}
            </select>
          ) : (
            <span className="field-static">{settings.judgeModels[0]?.label}</span>
          )}
          <span className="field-hint">{settings.judgeModels.find((m) => m.id === judge)?.note ?? settings.judgeModels[0]?.note}</span>
        </label>
        <label className="field">
          <span className="field-label">Ask each question</span>
          <select value={repeats} onChange={(e) => setRepeats(Number(e.target.value))}>
            <option value={1}>Once</option>
            <option value={2}>2 times</option>
            <option value={3}>3 times (shows if failures repeat)</option>
          </select>
        </label>
        <fieldset className="field">
          <legend className="field-label">Languages</legend>
          <div className="checks">
            {settings.languages.map((l) => (
              <label key={l.value} className="check">
                <input type="checkbox" checked={languages.includes(l.value)} onChange={() => toggle(l.value)} />
                {l.label}
              </label>
            ))}
          </div>
        </fieldset>
        <div className="form-actions">
          <button className="btn-primary" type="submit" disabled={disabled}>
            <CirclePlay size={18} /> {busy ? 'Starting…' : running ? 'A run is in progress' : 'Start run'}
          </button>
        </div>
      </form>
      {failure && <Notice kind="error">{failure}</Notice>}
    </Card>
  )
}
