import { useState } from 'react'
import { Check, X, RotateCcw, FlaskConical } from 'lucide-react'
import { decidePrompt } from '../api/client'
import { Notice } from './ui'
import { useSettings } from '../state/settings'
import { useSigner } from '../state/identity'

/**
 * The decision a prompt version waits for: approve or reject a proposal, start its retest, adopt it once a
 * retest has finished, or roll back to a retired version. Every decision is signed with a name.
 *
 * @param newFailures answers the latest retest broke, if known: adopting then asks for confirmation
 */
export default function FixDecisions({ prompt, retests, onDone, newFailures = 0 }) {
  const settings = useSettings()
  const { signed } = useSigner()
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)
  const [failure, setFailure] = useState(null)
  if (settings?.readOnly) return null

  const decide = (decision) => {
    if (
      decision === 'adopt' &&
      newFailures > 0 &&
      !window.confirm(
        `The retest found ${newFailures} new failure${newFailures > 1 ? 's' : ''}: answers that were safe before are unsafe with v${prompt.version}. Adopt it anyway?`,
      )
    ) {
      return
    }
    signed((by) => record(decision, by))
  }
  const record = async (decision, by) => {
    setBusy(true)
    setFailure(null)
    try {
      await decidePrompt(prompt.version, decision, by, note || undefined)
      setNote('')
      await onDone()
    } catch (err) {
      setFailure(err.message)
    } finally {
      setBusy(false)
    }
  }

  const finishedRetest = retests.some((r) => r.status === 'completed')
  const retestRunning = retests.some((r) => r.status === 'running')
  return (
    <div className="decisions">
      {prompt.status === 'proposed' && (
        <>
          <p className="small">
            Approving starts a retest at once: the failures, every English question and every control question, with
            this prompt. Nothing changes for real users until the version is adopted.
          </p>
          <textarea
            className="note"
            value={note}
            onChange={(e) => setNote(e.target.value)}
            placeholder="Optional note for the record"
            rows={2}
            maxLength={1000}
          />
          <div className="review-buttons">
            <button className="btn-success" disabled={busy} onClick={() => decide('approve')}>
              <Check size={18} /> Approve and retest
            </button>
            <button className="btn-danger" disabled={busy} onClick={() => decide('reject')}>
              <X size={18} /> Reject
            </button>
          </div>
        </>
      )}
      {prompt.status === 'approved' && retests.length === 0 && (
        <button className="btn-primary" disabled={busy} onClick={() => decide('retest')}>
          <FlaskConical size={18} /> Start the retest
        </button>
      )}
      {prompt.status === 'approved' && (
        <button className="btn-success" disabled={busy || !finishedRetest} onClick={() => decide('adopt')}>
          <Check size={18} /> Adopt v{prompt.version}
        </button>
      )}
      {prompt.status === 'approved' && !finishedRetest && (
        <span className="muted small">{retestRunning ? 'Adopt once the retest finishes.' : 'Adopt after a retest.'}</span>
      )}
      {prompt.status === 'retired' && (
        <button className="btn-secondary" disabled={busy} onClick={() => decide('adopt')}>
          <RotateCcw size={18} /> Roll back to v{prompt.version}
        </button>
      )}
      {failure && <Notice kind="error">{failure}</Notice>}
    </div>
  )
}
