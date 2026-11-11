import { useEffect, useState } from 'react'
import { ChartColumn } from 'lucide-react'
import { getRuns } from '../api/client'
import { Card, Loading, ErrorState } from '../components/ui'
import RunsTable from '../components/RunsTable'

export default function Runs() {
  const [runs, setRuns] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    getRuns().then(setRuns).catch((e) => setError(e.message))
  }, [])

  if (error) return <ErrorState message={error} />
  if (!runs) return <Loading />

  return (
    <Card title={`All Test Runs (${runs.length})`} icon={ChartColumn}>
      <RunsTable runs={runs} />
    </Card>
  )
}
