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

/** Completed runs with something to fix, newest first (the order the API returns runs in). */
export function fixableRuns(runs) {
  return (runs ?? []).filter((r) => r.status === 'completed' && r.counts.fail + r.counts.needsReview > 0)
}

/** How a saved fix is doing; `short` labels the counters, `label` the fix itself. */
export const FIX_STATES = {
  waiting: { label: 'Waiting for the next run', short: 'waiting for the next run', tone: 'watch' },
  working: { label: 'Working', short: 'working', tone: 'good' },
  failing: { label: 'Still failing', short: 'still failing', tone: 'bad' },
  off: { label: 'Turned off', short: 'turned off', tone: 'neutral' },
}

/** Scenarios with only the fixes in this language and state ('' for any); scenarios left empty are dropped. */
export function filterFixes(scenarios, { language = '', state = '' } = {}) {
  return (scenarios ?? [])
    .map((s) => ({
      ...s,
      fixes: s.fixes.filter((f) => (!language || f.language === language) && (!state || f.state === state)),
    }))
    .filter((s) => s.fixes.length > 0)
}

/** What has happened since the fix was saved, in plain words. */
export function usageText(fix) {
  if (!fix.active) return 'Not given to the model while it is off.'
  if (fix.timesUsed === 0) {
    return fix.revision > 1
      ? 'The earlier fix did not stop the failure, so the judge rewrote it. The next run uses this one.'
      : 'Not used yet. The next run that asks this question will use it.'
  }
  const answers = `${fix.timesUsed} answer${fix.timesUsed === 1 ? '' : 's'}`
  if (fix.timesPassed === fix.timesUsed) return `Used in ${answers} since, and every one passed.`
  return `Used in ${answers} since: ${fix.timesPassed} passed.`
}
