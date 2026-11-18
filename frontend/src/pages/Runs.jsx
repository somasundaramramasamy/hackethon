import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ArrowLeft, ArrowRight, CirclePlay, FlaskConical, History, Lock, Plus } from 'lucide-react'
import { getRuns, getScenarios, startRun } from '../api/client'
import { useLoad } from '../hooks/useLoad'
import { Card, ErrorState, Loading, Notice } from '../components/ui'
import RunsTable from '../components/RunsTable'
import Stepper from '../components/Stepper'
import MultiSelect from '../components/MultiSelect'
import ScenarioForm from './ScenarioForm'
import { useSettings } from '../state/settings'
import { useSigner } from '../state/identity'

// From our measured runs: about $3 for 180 answers with Gemma 4 31B and the Claude judge.
const COST_PER_ANSWER = 3 / 180

export default function Runs() {
  const { data: runs, error, loading } = useLoad(getRuns, [], {
    shouldPoll: (d) => d?.some((r) => r.status === 'running'),
  })
  return (
    <div className="page">
      <NewTest running={runs?.some((r) => r.status === 'running')} />
      <Card title="All tests" icon={History}>
        {loading && !runs ? <Loading /> : error ? <ErrorState error={error} /> : <RunsTable runs={runs} />}
      </Card>
    </div>
  )
}

function NewTest({ running }) {
  const settings = useSettings()
  const { signed } = useSigner()
  const navigate = useNavigate()
  const [step, setStep] = useState(1)
  const [name, setName] = useState('')
  const [model, setModel] = useState('')
  const [repeats, setRepeats] = useState(1)
  const [languages, setLanguages] = useState(['EN', 'TA', 'HI', 'ML'])
  const [picked, setPicked] = useState(null) // scenario codes; null = every active scenario
  const [busy, setBusy] = useState(false)
  const [failure, setFailure] = useState(null)

  if (!settings) return null
  const configured = settings.assistantConfigured && settings.judgeConfigured
  const blocked = settings.readOnly || !configured || running

  const start = async (by, chosen) => {
    setBusy(true)
    setFailure(null)
    try {
      const run = await startRun({
        model: model || undefined,
        repeats,
        languages,
        scenarios: chosen,
        startedBy: by,
        name: name.trim() || undefined,
      })
      navigate(`/runs/${run.code}`, { state: { fromNewTest: true } })
    } catch (err) {
      setFailure(err.message)
      setBusy(false)
    }
  }

  return (
    <Card title="New test" icon={FlaskConical}>
      <Stepper current={step} />
      {settings.readOnly ? (
        <Notice>Starting tests is turned off in the read-only demo.</Notice>
      ) : !configured ? (
        <Notice kind="warning">
          No model API key is set. Add <code>OPENROUTER_API_KEY</code> to <code>backend/.env</code> and restart the
          backend.
        </Notice>
      ) : running ? (
        <Notice>A test is in progress. A new one can start when it finishes.</Notice>
      ) : null}

      {step === 1 ? (
        <SetUp
          settings={settings}
          name={name}
          setName={setName}
          model={model}
          setModel={setModel}
          repeats={repeats}
          setRepeats={setRepeats}
          languages={languages}
          setLanguages={setLanguages}
          onNext={() => setStep(2)}
        />
      ) : (
        <ChooseScenarios
          languages={languages}
          repeats={repeats}
          picked={picked}
          setPicked={setPicked}
          busy={busy}
          blocked={blocked}
          onBack={() => setStep(1)}
          onStart={(chosen) => signed((by) => start(by, chosen))}
        />
      )}
      {failure && <Notice kind="error">{failure}</Notice>}
    </Card>
  )
}

function SetUp({ settings, name, setName, model, setModel, repeats, setRepeats, languages, setLanguages, onNext }) {
  const option = settings.assistantModels.find((m) => m.id === model) ?? settings.assistantModels[0]
  return (
    <form
      className="test-form"
      onSubmit={(e) => {
        e.preventDefault()
        if (languages.length) onNext()
      }}
    >
      <label className="field test-name">
        <span className="field-label">Name this test (optional)</span>
        <input
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder={defaultName(settings, model, languages.length, repeats)}
          maxLength={120}
        />
        <span className="field-hint">
          Say why you are running it, e.g. &quot;Before launch&quot;. Left empty, it is named after the model and
          settings.
        </span>
      </label>

      <label className="field">
        <span className="field-label">Assistant under test</span>
        <select value={model} onChange={(e) => setModel(e.target.value)}>
          {settings.assistantModels.map((m, i) => (
            <option key={m.id} value={i === 0 ? '' : m.id}>
              {shortLabel(m.label)}
            </option>
          ))}
        </select>
        <span className="field-hint">{option?.note}</span>
      </label>

      <div className="field">
        <span className="field-label">Judge</span>
        <div className="select-like locked" title="Every test is judged by the same model, so results compare">
          <span>{shortLabel(settings.judgeModels[0]?.label)}</span>
          <Lock size={14} />
        </div>
        <span className="field-hint">By Anthropic. The same judge for every test, so results compare</span>
      </div>

      <label className="field">
        <span className="field-label">Ask each question</span>
        <select value={repeats} onChange={(e) => setRepeats(Number(e.target.value))}>
          <option value={1}>Once</option>
          <option value={2}>2 times</option>
          <option value={3}>3 times</option>
        </select>
        <span className="field-hint">Repeats show whether a failure happens every time</span>
      </label>

      <div className="field">
        <span className="field-label">Languages</span>
        <MultiSelect
          label="Languages"
          options={settings.languages}
          value={languages}
          onChange={setLanguages}
          placeholder="Choose at least one"
          allLabel="languages"
        />
        <span className={`field-hint ${languages.length ? '' : 'warn'}`}>
          {languages.length ? 'English is the reference for the others' : 'Choose at least one language'}
        </span>
      </div>

      <div className="form-actions">
        <button className="btn-primary" type="submit" disabled={!languages.length}>
          Next: scenarios <ArrowRight size={18} />
        </button>
      </div>
    </form>
  )
}

function ChooseScenarios({ languages, repeats, picked, setPicked, busy, blocked, onBack, onStart }) {
  const { data: scenarios, error, loading, reload } = useLoad(getScenarios, [])
  const [adding, setAdding] = useState(false)

  if (loading && !scenarios) return <Loading />
  if (error) return <ErrorState error={error} />

  const chosen = picked ?? scenarios.map((s) => s.code)
  const toggle = (code) => setPicked(chosen.includes(code) ? chosen.filter((c) => c !== code) : [...chosen, code])
  const answers =
    scenarios
      .filter((s) => chosen.includes(s.code))
      .reduce((sum, s) => sum + s.questions.filter((q) => languages.includes(q.language)).length, 0) * repeats
  const allChosen = chosen.length === scenarios.length

  return (
    <div className="scenario-step">
      <div className="scenario-step-head">
        <p className="muted small">
          Tests usually ask the same scenarios each time, so results can be compared. Untick any you want to leave out,
          or add one for a new case.
        </p>
        <div className="scenario-step-tools">
          <button type="button" className="btn-link" onClick={() => setPicked(allChosen ? [] : null)}>
            {allChosen ? 'Clear all' : 'Choose all'}
          </button>
          <button type="button" className="btn-secondary" onClick={() => setAdding(true)}>
            <Plus size={16} /> Add a scenario
          </button>
        </div>
      </div>

      <ul className="scenario-pick">
        {scenarios.map((s) => {
          const missing = languages.filter((l) => !s.questions.some((q) => q.language === l))
          return (
            <li key={s.code}>
              <label className={chosen.includes(s.code) ? 'on' : ''}>
                <input type="checkbox" checked={chosen.includes(s.code)} onChange={() => toggle(s.code)} />
                <span className="scenario-pick-text">
                  <b>{s.code}</b> {s.title}
                  {missing.length > 0 && <span className="muted small"> · no question in {missing.join(', ')}</span>}
                </span>
                <span className={`tag ${s.kind === 'urgent' ? 'tag-urgent' : 'tag-control'}`}>
                  {s.kind === 'urgent' ? 'Urgent' : 'Control'}
                </span>
              </label>
            </li>
          )
        })}
      </ul>

      <div className="scenario-step-foot">
        <p className="small">
          <b>
            {chosen.length} of {scenarios.length} scenarios
          </b>{' '}
          · {languages.length} {languages.length === 1 ? 'language' : 'languages'} · asked{' '}
          {repeats === 1 ? 'once' : `${repeats} times`} = <b>{answers} answers</b>
          <span className="muted"> · about ${(answers * COST_PER_ANSWER).toFixed(2)}, mostly for the judge</span>
        </p>
        <div className="form-actions">
          <button type="button" className="btn-secondary" onClick={onBack}>
            <ArrowLeft size={18} /> Back
          </button>
          <button
            type="button"
            className="btn-primary"
            disabled={busy || blocked || answers === 0}
            onClick={() => onStart(allChosen ? undefined : chosen)}
          >
            <CirclePlay size={18} /> {busy ? 'Starting…' : 'Start test'}
          </button>
        </div>
      </div>

      {adding && (
        <div className="modal-backdrop" onMouseDown={(e) => e.target === e.currentTarget && setAdding(false)}>
          <div className="modal modal-wide" role="dialog" aria-modal="true" aria-label="Add a scenario">
            <h2 className="modal-title">Add a scenario</h2>
            <ScenarioForm
              embedded
              onCancel={() => setAdding(false)}
              onSaved={async (saved) => {
                setAdding(false)
                await reload()
                setPicked([...chosen, saved.code])
              }}
            />
          </div>
        </div>
      )}
    </div>
  )
}

/** "Gemma 4 31B (Google, open)" → "Gemma 4 31B"; the hint under the field says the rest. */
const shortLabel = (label) => (label ?? '').replace(/\s*\(.*\)$/, '')

/** Mirrors the backend's name for a test started without one. */
function defaultName(settings, model, languages, repeats) {
  const option = settings.assistantModels.find((m) => m.id === model) ?? settings.assistantModels[0]
  const label = shortLabel(option?.label) || 'Model'
  return `${label} · ${languages} ${languages === 1 ? 'language' : 'languages'}${repeats > 1 ? ` · ×${repeats}` : ''}`
}
