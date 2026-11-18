/**
 * Fixes waiting for a person: proposed and not yet approved or rejected, or approved and retested
 * but not yet adopted. A fix whose retest is still running waits for the retest, not for a person.
 */
export function waitingFixes(prompts) {
  return (prompts ?? [])
    .filter((p) => p.status === 'proposed' || (p.status === 'approved' && !p.retesting && p.retestRuns?.length))
    .map((p) => ({
      version: p.version,
      what: p.status === 'proposed' ? 'waits for approval' : 'was retested and waits for a decision to adopt it',
    }))
}
