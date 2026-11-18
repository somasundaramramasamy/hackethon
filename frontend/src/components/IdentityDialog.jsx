import { useEffect, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { UserRound } from 'lucide-react'
import {
  REVIEW_LANGUAGES,
  ROLES,
  continueAsViewer,
  onAskForIdentity,
  saveIdentity,
  signatureOf,
  useIdentity,
} from '../state/identity'

/**
 * Asks who is using Kavasam: once per visit, and whenever an action needs a signature. The person
 * can also just look around; any signed action then asks again.
 */
export default function IdentityDialog({ disabled }) {
  const { identity, confirmed, viewer } = useIdentity()
  const [asked, setAsked] = useState(null) // { then } when an action or the "Change" button asked
  const navigate = useNavigate()
  const location = useLocation()

  useEffect(() => onAskForIdentity((then) => setAsked({ then })), [])

  if (disabled) return null
  const onOpen = !confirmed && !viewer
  if (!onOpen && !asked) return null

  const close = () => setAsked(null)
  const done = (next) => {
    saveIdentity(next)
    const pending = asked?.then
    setAsked(null)
    if (pending) {
      pending(signatureOf(next))
    } else if (location.pathname === '/') {
      // Opening the app: go where this role usually works. Shared links keep their page.
      navigate(ROLES[next.role].home(next.language))
    }
  }

  return (
    <IdentityForm
      key={asked ? 'asked' : 'open'}
      initial={identity}
      forAction={Boolean(asked?.then)}
      onSubmit={done}
      onViewer={asked ? close : continueAsViewer}
      viewerLabel={asked ? 'Cancel' : 'Just looking'}
    />
  )
}

function IdentityForm({ initial, forAction, onSubmit, onViewer, viewerLabel }) {
  const [name, setName] = useState(initial?.name ?? '')
  const [role, setRole] = useState(initial?.role ?? '')
  const [language, setLanguage] = useState(initial?.language ?? 'TA')
  const nameRef = useRef(null)

  useEffect(() => {
    nameRef.current?.focus()
  }, [])

  const valid = name.trim() && role && (role !== 'reviewer' || language)
  const submit = (e) => {
    e.preventDefault()
    if (valid) onSubmit({ name: name.trim(), role, language: role === 'reviewer' ? language : null })
  }

  return (
    <div className="modal-backdrop">
      <form
        className="modal identity-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="identity-title"
        onSubmit={submit}
        onKeyDown={(e) => e.key === 'Escape' && onViewer()}
      >
        <div className="identity-head">
          <span className="identity-icon">
            <UserRound size={22} />
          </span>
          <div>
            <h2 id="identity-title">Who&apos;s using Kavasam?</h2>
            <p className="muted small">
              {forAction
                ? 'This action is signed with your name and role.'
                : 'Your name and role sign every review, approval and test you start.'}
            </p>
          </div>
        </div>

        <label className="field">
          <span className="field-label">Your name</span>
          <input
            ref={nameRef}
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="e.g. Priya Raman"
            maxLength={60}
            autoComplete="name"
          />
        </label>

        <fieldset className="field role-options">
          <legend className="field-label">Your role</legend>
          {Object.entries(ROLES).map(([key, r]) => (
            <label key={key} className={`role-option ${role === key ? 'selected' : ''}`}>
              <input type="radio" name="role" value={key} checked={role === key} onChange={() => setRole(key)} />
              <span>
                <b>{r.label}</b>
                <span className="muted small">{r.hint}</span>
              </span>
            </label>
          ))}
        </fieldset>

        {role === 'reviewer' && (
          <label className="field">
            <span className="field-label">Language you review</span>
            <select value={language} onChange={(e) => setLanguage(e.target.value)}>
              {REVIEW_LANGUAGES.map(([code, label]) => (
                <option key={code} value={code}>
                  {label}
                </option>
              ))}
            </select>
          </label>
        )}

        <p className="muted small identity-note">
          Kavasam records who decided, not a password. A deployment would use the organization&apos;s own sign-in.
        </p>

        <div className="modal-actions">
          <button type="button" className="btn-link" onClick={onViewer}>
            {viewerLabel}
          </button>
          <button type="submit" className="btn-primary" disabled={!valid}>
            Continue
          </button>
        </div>
      </form>
    </div>
  )
}
