import { createBrowserRouter, RouterProvider } from 'react-router-dom'
import Layout from './components/Layout'
import Dashboard from './pages/Dashboard'
import Runs from './pages/Runs'
import RunReview from './pages/RunReview'
import Placeholder from './pages/Placeholder'
import LiveTesting from './pages/LiveTesting'
import Scenarios from './pages/Scenarios'
import ScenarioForm from './pages/ScenarioForm'

const page = (title, subtitle) => ({ header: () => ({ title, subtitle }) })
const soon = (path, title) => ({ path, element: <Placeholder title={title} />, handle: page(title) })

const router = createBrowserRouter([
  {
    element: <Layout />,
    children: [
      {
        index: true,
        element: <Dashboard />,
        handle: page('Dashboard', 'Overview of AI safety testing for the multilingual healthcare assistant'),
      },
      {
        path: 'live',
        element: <LiveTesting />,
        handle: page('Live Testing', 'Ask every scenario in English, Tamil and Hindi and check each answer'),
      },
      { path: 'runs', element: <Runs />, handle: page('Test Runs', 'Every run across English, Tamil and Hindi') },
      {
        path: 'runs/:runId/review',
        element: <RunReview />,
        handle: {
          header: ({ runId }) => ({
            crumbs: [{ label: 'Test Runs', to: '/runs' }, { label: runId, to: '/runs' }, { label: 'Review' }],
          }),
        },
      },
      {
        path: 'scenarios',
        element: <Scenarios />,
        handle: page('Test Scenarios', 'The questions every live run asks, in English, Tamil and Hindi'),
      },
      {
        path: 'scenarios/new',
        element: <ScenarioForm />,
        handle: { header: () => ({ crumbs: [{ label: 'Scenarios', to: '/scenarios' }, { label: 'New scenario' }] }) },
      },
      {
        path: 'scenarios/:code/edit',
        element: <ScenarioForm />,
        handle: {
          header: ({ code }) => ({ crumbs: [{ label: 'Scenarios', to: '/scenarios' }, { label: code }, { label: 'Edit' }] }),
        },
      },
      soon('reviews', 'Review Queue'),
      soon('fixes', 'Fixes & Retests'),
      soon('analytics', 'Analytics'),
      soon('datasets', 'Datasets'),
      soon('settings', 'Configuration'),
      soon('*', 'Page not found'),
    ],
  },
])

export default function App() {
  return <RouterProvider router={router} />
}
