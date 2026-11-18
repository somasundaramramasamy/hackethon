import { Check } from 'lucide-react'

const TEST_STEPS = ['Set up', 'Scenarios', 'Results']

/** A horizontal progress line: done steps get a tick, the current one is highlighted. */
export default function Stepper({ steps = TEST_STEPS, current }) {
  return (
    <ol className="stepper" aria-label="Progress">
      {steps.map((label, i) => {
        const n = i + 1
        const state = n < current ? 'done' : n === current ? 'current' : 'todo'
        return (
          <li key={label} className={`step step-${state}`} aria-current={state === 'current' ? 'step' : undefined}>
            <span className="step-dot">{state === 'done' ? <Check size={16} strokeWidth={3} /> : n}</span>
            <span className="step-label">{label}</span>
          </li>
        )
      })}
    </ol>
  )
}
