import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { Plus, Download, Upload, Pencil, Trash2, Siren, ShieldCheck, CircleAlert, CircleCheck, Layers } from 'lucide-react'
import {
  deleteScenario,
  exportScenarios,
  getAiStatus,
  getAllScenarios,
  importScenarios,
  setScenarioActive,
} from '../api/client'
import { Card, ErrorState, Loading } from '../components/ui'

export default function Scenarios() {
  const location = useLocation()
  const [scenarios, setScenarios] = useState(null)
  const [dailyLimit, setDailyLimit] = useState(0)
  const [loadError, setLoadError] = useState(null)
  const [message, setMessage] = useState(location.state?.message ? { type: 'success', text: location.state.message } : null)
  const [busyCode, setBusyCode] = useState(null)
  const fileInput = useRef(null)

  const load = useCallback(() => {
    return getAllScenarios()
      .then(setScenarios)
      .catch((e) => setLoadError(e.message))
  }, [])

  useEffect(() => {
    load()
    getAiStatus()
      .then((s) => setDailyLimit(s?.assistantDailyLimit ?? 0))
      .catch(() => setDailyLimit(0))
  }, [load])

  async function toggle(s) {
    setBusyCode(s.code)
    try {
      await setScenarioActive(s.code, !s.active)
      await load()
    } catch (e) {
      setMessage({ type: 'error', text: e.message })
    } finally {
      setBusyCode(null)
    }
  }

  async function remove(s) {
    if (!window.confirm(`Delete ${s.code} "${s.title}"? This cannot be undone.`)) return
    setBusyCode(s.code)
    try {
      await deleteScenario(s.code)
      setMessage({ type: 'success', text: `${s.code} deleted.` })
      await load()
    } catch (e) {
      setMessage({ type: 'error', text: e.message })
    } finally {
      setBusyCode(null)
    }
  }

  async function downloadExport() {
    try {
      const data = await exportScenarios()
      const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `kavasam-scenarios-${new Date().toISOString().slice(0, 10)}.json`
      a.click()
      URL.revokeObjectURL(url)
      setMessage({ type: 'success', text: `Exported ${data.length} scenarios. Commit the file so your team can import it.` })
    } catch (e) {
      setMessage({ type: 'error', text: e.message })
    }
  }

  async function handleImport(event) {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) return
    let parsed
    try {
      parsed = JSON.parse(await file.text())
      if (!Array.isArray(parsed)) throw new Error('The file must contain a list of scenarios.')
    } catch (e) {
      setMessage({ type: 'error', text: `Could not read ${file.name}: ${e.message}` })
      return
    }
    if (!window.confirm(`Import ${parsed.length} scenario(s) from ${file.name}? Scenarios with the same code are replaced.`)) {
      return
    }
    try {
      const result = await importScenarios(parsed)
      setMessage({ type: 'success', text: `Imported: ${result.created} added, ${result.updated} updated.` })
      await load()
    } catch (e) {
      const details = e.body?.errors ? Object.entries(e.body.errors).slice(0, 5).map(([k, v]) => `${k}: ${v}`) : []
      setMessage({ type: 'error', text: e.message, details })
    }
  }

  if (loadError) return <ErrorState message={loadError} />
  if (!scenarios) return <Loading />

  const active = scenarios.filter((s) => s.active)
  const requests = active.reduce((n, s) => n + s.questions.length, 0)
  const overLimit = dailyLimit > 0 && requests > dailyLimit

  return (
    <div className="scenarios-page">
      <section className="card live-header">
        <div className="live-header-text">
          <h2>
            {active.length} of {scenarios.length} scenarios active
          </h2>
          <div className="meta">
            <span className="meta-chip">
              Next run: {active.length} × 3 languages = <b>&nbsp;{requests} requests</b>
            </span>
            {dailyLimit > 0 && <span className="meta-chip plain">Gemini daily limit: {dailyLimit}</span>}
          </div>
        </div>
        <div className="toolbar">
          <button className="btn-secondary" onClick={downloadExport}>
            <Download size={16} /> Export
          </button>
          <button className="btn-secondary" onClick={() => fileInput.current?.click()}>
            <Upload size={16} /> Import
          </button>
          <input ref={fileInput} type="file" accept="application/json,.json" hidden onChange={handleImport} />
          <Link to="/scenarios/new" className="btn-primary">
            <Plus size={18} /> Add scenario
          </Link>
        </div>
      </section>

      {overLimit && (
        <div className="notice notice-error">
          <CircleAlert size={18} /> A full run needs {requests} requests, more than your Gemini daily limit of {dailyLimit}.
          Turn some scenarios off, or the run will stop partway with quota errors.
        </div>
      )}
      {message && (
        <div className={`notice ${message.type === 'error' ? 'notice-error' : 'notice-success'}`} role="status">
          {message.type === 'error' ? <CircleAlert size={18} /> : <CircleCheck size={18} />}
          <span>
            {message.text}
            {message.details?.length > 0 && (
              <ul className="error-list">
                {message.details.map((d) => (
                  <li key={d}>{d}</li>
                ))}
              </ul>
            )}
          </span>
        </div>
      )}

      <Card title="Scenarios" icon={Layers}>
        <div className="table-wrap">
          <table className="table scenario-table">
            <thead>
              <tr>
                <th>Code</th>
                <th>Scenario</th>
                <th>Type</th>
                <th>Questions (native review)</th>
                <th className="num">Results</th>
                <th>Active</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {scenarios.map((s) => (
                <tr key={s.code} className={s.active ? undefined : 'row-off'}>
                  <td className="run-id nowrap">{s.code}</td>
                  <td>
                    <div className="scenario-title">{s.title}</div>
                    <div className="muted small">
                      {s.category}
                      {s.sourceName && ` · ${s.sourceName}`}
                    </div>
                  </td>
                  <td>
                    <span className={`tag ${s.kind === 'urgent' ? 'tag-urgent' : 'tag-control'}`}>
                      {s.kind === 'urgent' ? <Siren size={13} /> : <ShieldCheck size={13} />}
                      {s.kind === 'urgent' ? 'Urgent' : 'Control'}
                    </span>
                  </td>
                  <td>
                    <span className="review-chips">
                      {s.questions.map((q) => (
                        <span
                          key={q.language}
                          className={`review-chip ${q.reviewedBy ? 'reviewed' : 'unreviewed'}`}
                          title={q.reviewedBy ? `Reviewed by ${q.reviewedBy}` : 'Not reviewed by a native speaker yet'}
                        >
                          {q.language} {q.reviewedBy ? '✓' : '!'}
                        </span>
                      ))}
                    </span>
                  </td>
                  <td className="num">{s.resultCount}</td>
                  <td>
                    <label className="switch" title={s.active ? 'Included in the next run' : 'Skipped by live runs'}>
                      <input
                        type="checkbox"
                        checked={s.active}
                        disabled={busyCode === s.code}
                        onChange={() => toggle(s)}
                        aria-label={`${s.code} active`}
                      />
                      <span className="switch-track" />
                    </label>
                  </td>
                  <td className="nowrap">
                    <Link to={`/scenarios/${s.code}/edit`} className="icon-action" title="Edit">
                      <Pencil size={16} />
                    </Link>
                    <button
                      className="icon-action danger"
                      onClick={() => remove(s)}
                      disabled={s.resultCount > 0 || busyCode === s.code}
                      title={
                        s.resultCount > 0
                          ? `Has ${s.resultCount} saved results: turn it off instead of deleting`
                          : 'Delete'
                      }
                    >
                      <Trash2 size={16} />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>

      <p className="muted small legend-note">
        <b>✓</b> question reviewed by a native speaker · <b>!</b> not reviewed yet. Scenarios with saved results can only
        be turned off, so past results stay linked to them. Each team member has their own database: use Export and
        Import to share scenarios.
      </p>
    </div>
  )
}
