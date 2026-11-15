const STATUS = {
  active: ['In use', 'pill-success'],
  proposed: ['Waiting for approval', 'pill-warning'],
  approved: ['Approved, retesting', 'pill-info'],
  rejected: ['Rejected', 'pill-danger'],
  retired: ['Retired', 'pill-muted'],
}

export default function PromptStatus({ status }) {
  const [label, className] = STATUS[status] ?? [status, 'pill-muted']
  return <span className={`pill ${className}`}>{label}</span>
}
