import { Link, useParams } from 'react-router-dom'
import { SearchCheck, Loader2, MessageCircleReply, Repeat, Languages, Flag } from 'lucide-react'
import { getInvestigation } from '../api/client'
import { useLoad } from '../hooks/useLoad'
import { Card, ErrorState, FormattedText, LangChip, Loading, Notice } from '../components/ui'
import { formatDate, htmlLang, shortModel } from '../utils/format'

const ACTIONS = {
  follow_up: { label: 'Follow-up in the same conversation', Icon: MessageCircleReply },
  rephrase: { label: 'Asked again in different words', Icon: Repeat },
  ask_in_english: { label: 'Asked the English question', Icon: Languages },
  conclude: { label: 'Concluded', Icon: Flag },
}

const CONCLUSIONS = {
  CONFIRMED_FAILURE: { text: 'Confirmed failure', kind: 'error' },
  NOT_A_FAILURE: { text: 'Not a failure', kind: 'success' },
  UNCLEAR: { text: 'Unclear', kind: 'warning' },
}

export default function Investigation() {
  const { id } = useParams()
  const { data: inv, error, loading } = useLoad(() => getInvestigation(id), [id], {
    shouldPoll: (d) => d?.status === 'running',
  })
  if (loading && !inv) return <Loading />
  if (error) return <ErrorState error={error} />

  const conclusion = CONCLUSIONS[inv.conclusion]
  return (
    <div className="page">
      <Card>
        <div className="result-title">
          <SearchCheck size={22} className="card-icon" />
          <h2>
            Investigation of {inv.scenarioCode} {inv.scenarioTitle}
          </h2>
          <LangChip code={inv.language} />
        </div>
        <p className="muted small">
          Agent: {shortModel(inv.agentModel)} · started {formatDate(inv.startedAt)}
          {inv.startedBy && ` by ${inv.startedBy}`} ·{' '}
          <Link to={`/results/${inv.resultId}`}>back to the answer</Link>
        </p>
      </Card>

      {inv.status === 'completed' && conclusion && (
        <Card title="The agent's conclusion" icon={Flag}>
          <Notice kind={conclusion.kind}>
            <b>{conclusion.text}</b>
            {inv.failureType && inv.failureType !== 'NONE' && <> · {inv.failureType.toLowerCase()} failure</>}
          </Notice>
          <p className="diagnosis">{inv.diagnosis}</p>
          {inv.suggestedFix && (
            <>
              <h4 className="mini-head">Suggested fix</h4>
              <p className="suggested-fix">{inv.suggestedFix}</p>
            </>
          )}
          <p className="muted small">
            A recommendation for a person, not a final decision. The fix agent uses it when it proposes a new prompt.
          </p>
        </Card>
      )}
      {inv.status === 'failed' && <Notice kind="error">The investigation stopped: {inv.error}</Notice>}

      <Card title="Decision log" icon={MessageCircleReply}>
        <ol className="timeline">
          {inv.steps.map((s) => {
            const a = ACTIONS[s.action] ?? ACTIONS.conclude
            return (
              <li key={s.step} className={`timeline-step step-${s.action}`}>
                <div className="timeline-marker">
                  <a.Icon size={16} />
                </div>
                <div className="timeline-body">
                  <div className="timeline-head">
                    <b>
                      Step {s.step}: {a.label}
                    </b>
                  </div>
                  {s.thought && <p className="thought">Why: {s.thought}</p>}
                  {s.message && (
                    <div className="bubble bubble-user" lang={s.action === 'ask_in_english' ? 'en' : htmlLang(inv.language)}>
                      {s.message}
                    </div>
                  )}
                  {s.reply && (
                    <div className="bubble bubble-assistant" lang={s.action === 'ask_in_english' ? 'en' : htmlLang(inv.language)}>
                      <FormattedText text={s.reply} />
                    </div>
                  )}
                  {s.replySummary && <p className="reply-summary">In English: {s.replySummary}</p>}
                </div>
              </li>
            )
          })}
          {inv.status === 'running' && (
            <li className="timeline-step">
              <div className="timeline-marker">
                <Loader2 size={16} className="spin" />
              </div>
              <div className="timeline-body muted">The agent is deciding its next step…</div>
            </li>
          )}
        </ol>
      </Card>
    </div>
  )
}
