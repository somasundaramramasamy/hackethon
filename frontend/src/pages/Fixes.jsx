import { Link } from 'react-router-dom'
import { Wrench } from 'lucide-react'
import { getPrompts } from '../api/client'
import { useLoad } from '../hooks/useLoad'
import { Card, ErrorState, Loading } from '../components/ui'
import { formatDate } from '../utils/format'
import PromptStatus from '../components/PromptStatus'

export default function Fixes() {
  const { data, error, loading } = useLoad(getPrompts, [])
  return (
    <div className="page">
      <Card title="System prompt versions" icon={Wrench}>
        <p className="muted card-intro">
          A fix is a new version of the assistant's system prompt. The agent proposes it from a run's failures, a named
          person approves or rejects it, approval retests the failures plus every English and control question, and the
          version is adopted only after the retest. Adopting an older version rolls back.
        </p>
        {loading && !data ? (
          <Loading />
        ) : error ? (
          <ErrorState error={error} />
        ) : (
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Version</th>
                  <th>Status</th>
                  <th>What changed</th>
                  <th>Fixes run</th>
                  <th>Decided by</th>
                  <th>Created</th>
                </tr>
              </thead>
              <tbody>
                {data.map((p) => (
                  <tr key={p.version}>
                    <td>
                      <Link className="run-id" to={`/fixes/${p.version}`}>
                        v{p.version}
                      </Link>
                    </td>
                    <td>
                      <PromptStatus prompt={p} />
                    </td>
                    <td className="small">{p.summary}</td>
                    <td className="small">{p.sourceRun ? <Link to={`/runs/${p.sourceRun}`}>{p.sourceRun}</Link> : '—'}</td>
                    <td className="small">{p.decidedBy ?? '—'}</td>
                    <td className="nowrap small">{formatDate(p.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  )
}
