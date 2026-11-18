import { useState } from 'react'
import { createBrowserRouter, Link, Navigate, RouterProvider } from 'react-router-dom'
import Layout from './components/Layout'
import Splash from './components/Splash'
import { splashSeen } from './state/splash'
import History from './pages/History'
import Runs from './pages/Runs'
import RunDetail from './pages/RunDetail'
import ResultDetail from './pages/ResultDetail'
import Comparison from './pages/Comparison'
import ReviewQueue from './pages/ReviewQueue'
import Investigation from './pages/Investigation'
import Fixes from './pages/Fixes'
import FixDetail from './pages/FixDetail'
import Scenarios from './pages/Scenarios'
import ScenarioForm from './pages/ScenarioForm'

const page = (title, subtitle) => ({ header: () => ({ title, subtitle }) })
const crumbs = (...items) => ({ header: (params) => ({ crumbs: items.map((c) => (typeof c === 'function' ? c(params) : c)) }) })

const router = createBrowserRouter([
  {
    element: <Layout />,
    children: [
      {
        index: true,
        element: <Runs />,
        handle: page('Tests', 'Ask every scenario in every language, then check each answer'),
      },
      { path: 'runs', element: <Navigate to="/" replace /> },
      {
        path: 'history',
        element: <History />,
        handle: page('Test history', 'Every test so far, and how each language compares with English'),
      },
      { path: 'overview', element: <Navigate to="/history" replace /> },
      {
        path: 'runs/:code',
        element: <RunDetail />,
        handle: crumbs({ label: 'Tests', to: '/' }, ({ code }) => ({ label: code })),
      },
      {
        path: 'runs/:code/comparison',
        element: <Comparison />,
        handle: crumbs(
          { label: 'Tests', to: '/' },
          ({ code }) => ({ label: code, to: `/runs/${code}` }),
          { label: 'Before and after' },
        ),
      },
      {
        path: 'results/:id',
        element: <ResultDetail />,
        handle: crumbs({ label: 'Tests', to: '/' }, ({ id }) => ({ label: `Answer #${id}` })),
      },
      {
        path: 'reviews',
        element: <ReviewQueue />,
        handle: page('Review Queue', 'Native speakers and clinicians confirm or reject what the checks flagged'),
      },
      {
        path: 'investigations/:id',
        element: <Investigation />,
        handle: crumbs({ label: 'Review Queue', to: '/reviews' }, ({ id }) => ({ label: `Investigation #${id}` })),
      },
      {
        path: 'fixes',
        element: <Fixes />,
        handle: page('Fixes', 'Prompt versions proposed by the agent and approved by people'),
      },
      {
        path: 'fixes/:version',
        element: <FixDetail />,
        handle: crumbs({ label: 'Fixes', to: '/fixes' }, ({ version }) => ({ label: `v${version}` })),
      },
      {
        path: 'scenarios',
        element: <Scenarios />,
        handle: page('Test Scenarios', 'The questions every run asks, with their safety criteria and sources'),
      },
      {
        path: 'scenarios/new',
        element: <ScenarioForm />,
        handle: crumbs({ label: 'Scenarios', to: '/scenarios' }, { label: 'New scenario' }),
      },
      {
        path: 'scenarios/:code/edit',
        element: <ScenarioForm />,
        handle: crumbs({ label: 'Scenarios', to: '/scenarios' }, ({ code }) => ({ label: code }), { label: 'Edit' }),
      },
      { path: '*', element: <NotFound />, handle: page('Page not found') },
    ],
  },
])

function NotFound() {
  return (
    <div className="card">
      <p>
        This page does not exist. <Link to="/">Back to the tests</Link>
      </p>
    </div>
  )
}

export default function App() {
  const [splash, setSplash] = useState(() => !splashSeen())
  const [replays, setReplays] = useState(0)
  // ?intro=loop shows only the load-up screen, over and over, e.g. for recording it.
  const loop = new URLSearchParams(window.location.search).get('intro') === 'loop'
  if (splash || loop) {
    return (
      <Splash
        key={replays}
        onDone={() => {
          if (loop) {
            setReplays((n) => n + 1)
            return
          }
          document.body.style.background = ''
          setSplash(false)
        }}
      />
    )
  }
  return <RouterProvider router={router} />
}
