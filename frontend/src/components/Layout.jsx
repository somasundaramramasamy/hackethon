import { useState } from 'react'
import { Link, NavLink, Outlet, useLocation, useMatches } from 'react-router-dom'
import {
  House,
  CirclePlay,
  Layers,
  ClipboardCheck,
  Wrench,
  ChartColumn,
  Database,
  Settings,
  Bell,
  ChevronDown,
  HeartPulse,
  Menu,
  X,
  ChevronRight,
} from 'lucide-react'
import Logo from './Logo'
import { getCurrentUser, getReviewQueueCount } from '../api/client'

const nav = [
  { to: '/', label: 'Dashboard', icon: House, end: true },
  { to: '/runs', label: 'Test Runs', icon: CirclePlay },
  { to: '/scenarios', label: 'Scenarios', icon: Layers },
  { to: '/reviews', label: 'Reviews', icon: ClipboardCheck, badge: true },
  { to: '/fixes', label: 'Fixes & Retests', icon: Wrench },
  { to: '/analytics', label: 'Analytics', icon: ChartColumn },
  { to: '/datasets', label: 'Datasets', icon: Database },
  { to: '/settings', label: 'Configuration', icon: Settings },
]

export default function Layout() {
  const user = getCurrentUser()
  const reviewCount = getReviewQueueCount()
  const [open, setOpen] = useState(false)
  const location = useLocation()
  const [lastPath, setLastPath] = useState(location.pathname)
  const matches = useMatches()
  const match = [...matches].reverse().find((m) => m.handle?.header)
  const header = match ? match.handle.header(match.params) : {}

  // Close the mobile drawer whenever the route changes.
  if (lastPath !== location.pathname) {
    setLastPath(location.pathname)
    setOpen(false)
  }

  return (
    <div className="app">
      <aside className={`sidebar ${open ? 'open' : ''}`}>
        <div className="brand">
          <Logo size={40} />
          <div>
            <div className="brand-name">Kavasam</div>
            <div className="brand-sub">AI Safety Testing Platform</div>
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
            <div className="assistant-title">Multilingual Healthcare Assistant</div>
            <div className="assistant-langs">English • Tamil • Hindi</div>
          </div>
        </div>
      </aside>
      {open && <div className="scrim" onClick={() => setOpen(false)} />}

      <div className="main">
        <header className="topbar">
          <button className="icon-btn menu-btn" onClick={() => setOpen(true)} aria-label="Open menu">
            <Menu size={22} />
          </button>
          <div className="topbar-slot">
            {header.crumbs ? (
              <nav className="crumbs" aria-label="Breadcrumb">
                {header.crumbs.map((c, i) => (
                  <span key={c.label} className="crumb">
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
            <span className="sample-chip" title="All figures are simulated until the backend is connected">
              Sample data
            </span>
            <button className="icon-btn bell" aria-label="Notifications">
              <Bell size={22} />
              <span className="bell-dot" />
            </button>
            <div className="divider-v" />
            <button className="user">
              <span className="avatar">{user.initials}</span>
              <span className="user-text">
                <span className="user-name">{user.name}</span>
                <span className="user-role">{user.role}</span>
              </span>
              <ChevronDown size={16} />
            </button>
          </div>
        </header>
        <main className="content">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
