import { Link } from 'react-router-dom'
import { LangChip, StatusPill } from './ui'
import { formatDate, shortModel } from '../utils/format'

export default function RunsTable({ runs }) {
  if (!runs.length) return <p className="muted">No runs yet.</p>
  return (
    <div className="table-wrap">
      <table className="table">
        <thead>
          <tr>
            <th>Run</th>
            <th>Assistant · prompt</th>
            <th>Languages</th>
            <th className="num">Pass</th>
            <th className="num">Fail</th>
            <th className="num">Review</th>
            <th>Status</th>
            <th>Started</th>
          </tr>
        </thead>
        <tbody>
          {runs.map((r) => (
            <tr key={r.code}>
              <td>
                <Link className="run-id" to={`/runs/${r.code}`}>
                  {r.code}
                </Link>
                <div className="muted small">{r.type === 'retest' ? `Retest of ${r.baselineRun}` : r.suite}</div>
              </td>
              <td>
                {shortModel(r.model)} <span className="muted">· v{r.promptVersion}</span>
              </td>
              <td>
                <span className="lang-chips">
                  {r.languages.map((l) => (
                    <LangChip key={l.language} code={l.language} />
                  ))}
                </span>
              </td>
              <td className="num pass">{r.counts.pass}</td>
              <td className="num fail">{r.counts.fail}</td>
              <td className="num warn">{r.counts.needsReview}</td>
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
