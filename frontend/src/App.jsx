import { createBrowserRouter, RouterProvider } from 'react-router-dom'
import Layout from './components/Layout'
import Dashboard from './pages/Dashboard'
import Runs from './pages/Runs'
import RunReview from './pages/RunReview'
import Placeholder from './pages/Placeholder'

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
      soon('scenarios', 'Test Scenarios'),
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
