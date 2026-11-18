import { useEffect, useState } from 'react'
import { BUBBLE_PATH, CHECK_PATH, SHIELD_PATH } from './Logo'
import { getSettings } from '../api/client'
import { markSplashSeen } from '../state/splash'
import './splash.css'

// The load-up screen: the shield draws itself, the four languages settle around it and are checked
// one by one. It plays once per visit, can be skipped with a click or a key, and waits for the
// backend, so it never opens onto a broken page.

const LENGTH_MS = 8000
const REDUCED_MS = 1200
const GIVE_UP_MS = 30000

const GLYPHS = [
  { char: 'A', lang: 'en', name: 'English' },
  { char: 'த', lang: 'ta', name: 'Tamil' },
  { char: 'ह', lang: 'hi', name: 'Hindi' },
  { char: 'മ', lang: 'ml', name: 'Malayalam' },
]

export default function Splash({ onDone }) {
  const [reduced] = useState(() => Boolean(window.matchMedia?.('(prefers-reduced-motion: reduce)').matches))
  const [ready, setReady] = useState(false)
  const [timeUp, setTimeUp] = useState(false)
  const [failed, setFailed] = useState(false)
  const [forced, setForced] = useState(false)
  const [attempt, setAttempt] = useState(0)
  const leaving = forced || (timeUp && ready)

  // Check that the backend answers, retrying while the animation plays.
  useEffect(() => {
    let cancelled = false
    let timer
    const started = Date.now()
    const ping = () =>
      getSettings()
        .then(() => !cancelled && setReady(true))
        .catch(() => {
          if (cancelled) return
          if (Date.now() - started > GIVE_UP_MS) setFailed(true)
          else timer = setTimeout(ping, 1500)
        })
    ping()
    return () => {
      cancelled = true
      clearTimeout(timer)
    }
  }, [attempt])

  useEffect(() => {
    const t = setTimeout(() => setTimeUp(true), reduced ? REDUCED_MS : LENGTH_MS - 600)
    return () => clearTimeout(t)
  }, [reduced])

  // Once it is time and the backend is ready (or the person chose to go in), fade out.
  useEffect(() => {
    if (!leaving) return undefined
    markSplashSeen()
    const t = setTimeout(onDone, reduced ? 150 : 600)
    return () => clearTimeout(t)
  }, [leaving, onDone, reduced])

  // Any click or key skips ahead; it still waits for the backend.
  useEffect(() => {
    const skip = (e) => {
      if (!e.target.closest?.('.splash-retry')) setTimeUp(true)
    }
    window.addEventListener('keydown', skip)
    window.addEventListener('pointerdown', skip)
    return () => {
      window.removeEventListener('keydown', skip)
      window.removeEventListener('pointerdown', skip)
    }
  }, [])

  const retry = () => {
    setFailed(false)
    setAttempt((a) => a + 1)
  }

  const status = failed
    ? "Can't reach the Kavasam server."
    : timeUp && !ready
      ? 'Waiting for the server to start…'
      : 'Checking every language…'

  return (
    <div
      className={`splash ${reduced ? 'reduced' : ''} ${leaving ? 'leaving' : ''}`}
      role="status"
      aria-live="polite"
      aria-label="Kavasam is loading"
    >
      <div className="splash-glow" />
      <div className="splash-stage">
        <div className="splash-orbit">
          {GLYPHS.map((g, i) => (
            <span key={g.lang} className={`splash-glyph g${i}`} lang={g.lang} title={g.name}>
              <span className="splash-glyph-char">{g.char}</span>
              <span className="splash-tick" aria-hidden="true">
                <span>✓</span>
              </span>
            </span>
          ))}
        </div>
        <svg className="splash-logo" viewBox="0 0 64 64" aria-hidden="true">
          <defs>
            <linearGradient id="splash-shield" x1="10" y1="4" x2="54" y2="60" gradientUnits="userSpaceOnUse">
              <stop offset="0" stopColor="#6aa1ff" />
              <stop offset="0.55" stopColor="#3b6cf6" />
              <stop offset="1" stopColor="#7c5cf6" />
            </linearGradient>
          </defs>
          <path className="splash-shield-fill" d={SHIELD_PATH} fill="url(#splash-shield)" />
          <path className="splash-shield-line" d={SHIELD_PATH} pathLength="1" />
          <path className="splash-bubble" d={BUBBLE_PATH} />
          <path className="splash-check" d={CHECK_PATH} pathLength="1" />
        </svg>
      </div>

      <h1 className="splash-title" aria-label="Kavasam">
        {'Kavasam'.split('').map((c, i) => (
          <span key={i} style={{ animationDelay: `${4.6 + i * 0.09}s` }} aria-hidden="true">
            {c}
          </span>
        ))}
      </h1>
      <p className="splash-tagline">Is your health assistant as safe in every language?</p>

      <div className="splash-footer">
        <div className="splash-bar">
          <span />
        </div>
        <p className="splash-status">{status}</p>
        {failed ? (
          <div className="splash-retry">
            <button type="button" onClick={retry}>
              Try again
            </button>
            <button type="button" className="ghost" onClick={() => setForced(true)}>
              Open anyway
            </button>
          </div>
        ) : (
          <p className="splash-skip">Click or press any key to skip</p>
        )}
      </div>
    </div>
  )
}
