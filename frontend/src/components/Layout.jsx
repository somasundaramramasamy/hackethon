import { useEffect, useState } from 'react'
import { Link, NavLink, Outlet, useLocation, useMatches } from 'react-router-dom'
import { House, CirclePlay, Layers, ClipboardCheck, Wrench, HeartPulse, Menu, X, ChevronRight, UserPen } from 'lucide-react'
import Logo from './Logo'
import { getReviewQueueCount, getSettings } from '../api/client'
import { SettingsContext } from '../state/settings'
import { setReviewerName, useReviewerName } from '../state/reviewer'

const nav = [
  { to: '/', label: 'Dashboard', icon: House, end: true },
  { to: '/runs', label: 'Test Runs', icon: CirclePlay },
  { to: '/reviews', label: 'Review Queue', icon: ClipboardCheck, badge: true },
  { to: '/fixes', label: 'Fixes', icon: Wrench },
  { to: '/scenarios', label: 'Scenarios', icon: Layers },
]

export default function Layout() {
  const [settings, setSettings] = useState(null)
  const [reviewCount, setReviewCount] = useState(0)
  const [open, setOpen] = useState(false)
  const location = useLocation()
  const [lastPath, setLastPath] = useState(location.pathname)
  const matches = useMatches()
  const match = [...matches].reverse().find((m) => m.handle?.header)
  const header = match ? match.handle.header(match.params) : {}

  useEffect(() => {
    getSettings()
      .then(setSettings)
      .catch(() => setSettings(null))
  }, [])

  // Refresh the review badge on every navigation (a review may have just been submitted).
  useEffect(() => {
    getReviewQueueCount()
      .then(setReviewCount)
      .catch(() => setReviewCount(0))
  }, [location.pathname])

  // Close the mobile drawer whenever the route changes.
  if (lastPath !== location.pathname) {
    setLastPath(location.pathname)
    setOpen(false)
  }

  return (
    <SettingsContext.Provider value={settings}>
      <div className="app">
        <aside className={`sidebar ${open ? 'open' : ''}`}>
          <div className="brand">
            <Logo size={40} />
            <div>
              <div className="brand-name">Kavasam</div>
              <div className="brand-sub">Multilingual AI safety testing</div>
            </div>
            <button className="icon-btn sidebar-close" onClick={() => setOpen(false)} aria-label="Close menu">
              <X size={20} />
            </button>
          </div>

          <nav className="nav">
            {nav.map(({ to, label, icon: Icon, end, badge }) => (
              <NavLink key={to} to={to} end={end} className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}>
                <Icon size={20} strokeWidth={1.8} />
                <span>{label}</span>
                {badge && reviewCount > 0 && <span className="nav-badge">{reviewCount}</span>}
              </NavLink>
            ))}
          </nav>

          <div className="assistant-card">
            <div className="assistant-icon">
              <HeartPulse size={22} />
            </div>
            <div>
              <div className="assistant-title">Health assistant under test</div>
              <div className="assistant-langs">English • Tamil • Hindi • Malayalam</div>
            </div>
          </div>
        </aside>
        {open && <div className="scrim" onClick={() => setOpen(false)} />}

        <div className="main">
          {settings?.readOnly && (
            <div className="demo-banner" role="status">
              Read-only demo with recorded results. Run Kavasam locally to start runs, review answers or approve
              fixes.
            </div>
          )}
          <header className="topbar">
            <button className="icon-btn menu-btn" onClick={() => setOpen(true)} aria-label="Open menu">
              <Menu size={22} />
            </button>
            <div className="topbar-slot">
              {header.crumbs ? (
                <nav className="crumbs" aria-label="Breadcrumb">
                  {header.crumbs.map((c, i) => (
                    <span key={`${c.label}-${i}`} className="crumb">
                      {i > 0 && <ChevronRight size={16} />}
                      {c.to ? <Link to={c.to}>{c.label}</Link> : <b>{c.label}</b>}
                    </span>
                  ))}
                </nav>
              ) : (
                <div className="topbar-title">
                  <h1>{header.title}</h1>
                  {header.subtitle && <p>{header.subtitle}</p>}
                </div>
              )}
            </div>
            <div className="topbar-right">
              <ReviewerName />
            </div>
          </header>
          <main className="content">
            <Outlet />
          </main>
        </div>
      </div>
    </SettingsContext.Provider>
  )
}

/** Every review and approval is signed with this name; there is no login in the MVP. */
function ReviewerName() {
  const name = useReviewerName()
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState('')

  if (editing || !name) {
    return (
      <form
        className="reviewer-form"
        onSubmit={(e) => {
          e.preventDefault()
          if (draft.trim()) {
            setReviewerName(draft)
            setEditing(false)
          }
        }}
      >
        <label htmlFor="reviewer-name" className="muted small">
          Your name
        </label>
        <input
          id="reviewer-name"
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          placeholder="Signs your reviews"
          maxLength={100}
          autoFocus={editing}
        />
        <button className="btn-secondary" type="submit" disabled={!draft.trim()}>
          Save
        </button>
      </form>
    )
  }
  return (
    <button
      className="user"
      onClick={() => {
        setDraft(name)
        setEditing(true)
      }}
      title="Change the name that signs your reviews and approvals"
    >
      <span className="avatar">{initials(name)}</span>
      <span className="user-text">
        <span className="user-name">{name}</span>
        <span className="user-role">Reviewer</span>
      </span>
      <UserPen size={16} />
    </button>
  )
}

const initials = (name) =>
  name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((w) => w[0].toUpperCase())
    .join('')
