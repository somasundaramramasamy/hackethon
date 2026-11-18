import { describe, expect, it } from 'vitest'
import { filterFixes, fixableRuns, usageText, waitingFixes } from './fixes'

describe('waitingFixes', () => {
  it('lists proposed fixes and retested fixes not yet adopted', () => {
    const prompts = [
      { version: 1, status: 'retired' },
      { version: 2, status: 'rejected' },
      { version: 3, status: 'approved', retesting: false, retestRuns: ['RT-1'] },
      { version: 4, status: 'proposed' },
      { version: 5, status: 'approved', retesting: true, retestRuns: ['RT-2'] },
      { version: 6, status: 'active' },
    ]
    expect(waitingFixes(prompts).map((f) => f.version)).toEqual([3, 4])
  })

  it('is empty without prompts', () => {
    expect(waitingFixes(null)).toEqual([])
  })
})

describe('fixableRuns', () => {
  it('keeps finished runs with something that did not pass', () => {
    const c = (fail, needsReview = 0) => ({ pass: 1, fail, needsReview, error: 0 })
    const runs = [
      { code: 'R3', status: 'completed', counts: c(1) },
      { code: 'R2', status: 'completed', counts: c(0, 2) },
      { code: 'R1', status: 'completed', counts: c(0) },
      { code: 'R0', status: 'running', counts: c(4) },
    ]
    expect(fixableRuns(runs).map((r) => r.code)).toEqual(['R3', 'R2'])
  })
})

describe('filterFixes', () => {
  const scenarios = [
    { code: 'SC-01', fixes: [{ language: 'TA', state: 'working' }, { language: 'HI', state: 'waiting' }] },
    { code: 'SC-02', fixes: [{ language: 'EN', state: 'off' }] },
  ]
  it('keeps only matching fixes and drops empty scenarios', () => {
    expect(filterFixes(scenarios, { language: 'TA' }).map((s) => s.code)).toEqual(['SC-01'])
    expect(filterFixes(scenarios, { state: 'off' })[0].fixes).toEqual([{ language: 'EN', state: 'off' }])
    expect(filterFixes(scenarios, {})).toHaveLength(2)
    expect(filterFixes(null)).toEqual([])
  })
})

describe('usageText', () => {
  it('says what happened since the fix was saved', () => {
    expect(usageText({ active: true, timesUsed: 0, revision: 1 })).toMatch(/Not used yet/)
    expect(usageText({ active: true, timesUsed: 0, revision: 2 })).toMatch(/rewrote it/)
    expect(usageText({ active: true, timesUsed: 3, timesPassed: 3 })).toBe('Used in 3 answers since, and every one passed.')
    expect(usageText({ active: true, timesUsed: 2, timesPassed: 1 })).toBe('Used in 2 answers since: 1 passed.')
    expect(usageText({ active: false, timesUsed: 2 })).toMatch(/off/)
  })
})
