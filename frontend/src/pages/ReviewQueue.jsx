import { Link } from 'react-router-dom'
import { ClipboardCheck, Scale } from 'lucide-react'
import { getAgreement, getReviewQueue } from '../api/client'
import { useLoad } from '../hooks/useLoad'
import { AuditTag, Card, ErrorState, LangChip, Loading, Verdict } from '../components/ui'
import { formatDate, percent } from '../utils/format'

export default function ReviewQueue() {
  const queue = useLoad(getReviewQueue, [])
  const agreement = useLoad(getAgreement, [])

  return (
    <div className="page">
      <div className="row-2">
        <Card title="Waiting for a native speaker" icon={ClipboardCheck}>
          <p className="muted card-intro">
            Every failure and unsure answer, plus a random 10% of passes, so the judge's mistakes in either direction are
            caught. Open one, read it in its own language, and mark it safe or unsafe.
          </p>
          {queue.loading && !queue.data ? (
            <Loading />
          ) : queue.error ? (
            <ErrorState error={queue.error} />
          ) : queue.data.length === 0 ? (
            <p className="muted">Nothing waiting. Every flagged answer has been reviewed.</p>
          ) : (
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>Answer</th>
                    <th>Language</th>
                    <th>Automated verdict</th>
                    <th>Why</th>
                    <th>Run</th>
                  </tr>
                </thead>
                <tbody>
                  {queue.data.map((item) => (
                    <tr key={item.id}>
                      <td>
                        <Link className="run-id" to={`/results/${item.id}`}>
                          {item.scenarioCode} {item.scenarioTitle}
                        </Link>
                        <div className="muted small">attempt {item.attempt}</div>
                      </td>
                      <td>
                        <LangChip code={item.language} /> {item.languageName}
                      </td>
                      <td>
                        {item.reason === 'audit' ? <AuditTag /> : <Verdict value={item.verdict} />}
                      </td>
                      <td className="small why">{item.detail}</td>
                      <td className="nowrap small">
                        <Link to={`/runs/${item.runCode}`}>{item.runCode}</Link>
                        <div className="muted">{formatDate(item.askedAt)}</div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>

        <Card title="How often the judge agrees with people" icon={Scale}>
          {agreement.loading && !agreement.data ? (
            <Loading />
          ) : agreement.error ? (
            <ErrorState error={agreement.error} />
          ) : (
            <Agreement report={agreement.data} />
          )}
        </Card>
      </div>
    </div>
  )
}

function Agreement({ report }) {
  const { overall, byJudge } = report
  if (!overall.reviewed) {
    return <p className="muted">No reviews yet. The rate appears once people have reviewed answers the judge passed or failed.</p>
  }
  return (
    <>
      <p className="big-number">{percent(overall.rate)}</p>
      <p className="muted small">
        {overall.agreed} of {overall.reviewed} reviewed answers. Missed failures (judge said safe, a person said unsafe):{' '}
        <b className={overall.missedFailures ? 'bad' : ''}>{overall.missedFailures}</b>. False alarms:{' '}
        <b>{overall.falseAlarms}</b>.
      </p>
      {byJudge.length > 1 && (
        <table className="table compact">
          <thead>
            <tr>
              <th>Judge</th>
              <th className="num">Reviewed</th>
              <th className="num">Agrees</th>
            </tr>
          </thead>
          <tbody>
            {byJudge.map((j) => (
              <tr key={j.judgeModel}>
                <td>{j.judgeModel}</td>
                <td className="num">{j.reviewed}</td>
                <td className="num">{percent(j.rate)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </>
  )
}
