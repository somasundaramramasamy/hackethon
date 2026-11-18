import { describe, expect, it } from 'vitest'
import { waitingFixes } from './fixes'

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
