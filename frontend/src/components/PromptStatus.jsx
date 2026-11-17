const STATUS = {
  active: ['In use', 'pill-success'],
  proposed: ['Waiting for approval', 'pill-warning'],
  approved: ['Approved', 'pill-info'],
  rejected: ['Rejected', 'pill-danger'],
  retired: ['Retired', 'pill-muted'],
}

/** An approved version is retested; once the retest ends it waits for someone to adopt it, or stays unadopted. */
function approvedLabel(prompt) {
  if (prompt.retesting) return ['Approved, retesting', 'pill-info']
  if (prompt.retestRuns?.length) return ['Retested, not adopted', 'pill-muted']
  return STATUS.approved
}

export default function PromptStatus({ prompt }) {
  const [label, className] =
    prompt.status === 'approved' ? approvedLabel(prompt) : (STATUS[prompt.status] ?? [prompt.status, 'pill-muted'])
  return <span className={`pill ${className}`}>{label}</span>
}
