import { Link } from 'react-router-dom'
import { LangChips, StatusPill } from './ui'
import { formatDate } from '../utils/format'

export default function RunsTable({ runs }) {
  return (
    <div className="table-wrap">
      <table className="table">
        <thead>
          <tr>
            <th>Run ID</th>
            <th>Test Suite</th>
            <th>Languages</th>
            <th className="num">Total</th>
            <th className="num">Passed</th>
            <th className="num">Failed</th>
            <th>Status</th>
            <th>Started At</th>
          </tr>
        </thead>
        <tbody>
          {runs.map((r) => (
            <tr key={r.id}>
              <td>
                <Link className="run-id" to="/runs/TR-2026-0142/review">
                  {r.id}
                </Link>
              </td>
              <td className="suite">{r.suite}</td>
              <td>
                <LangChips languages={r.languages} />
              </td>
              <td className="num">{r.total}</td>
              <td className="num pass">{r.passed}</td>
              <td className="num fail">{r.failed}</td>
              <td>
                <StatusPill status={r.status} />
              </td>
              <td className="nowrap">{formatDate(r.startedAt)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
