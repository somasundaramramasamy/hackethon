import { describe, expect, it } from 'vitest'
import { diffLines } from './diff'

describe('diffLines', () => {
  it('marks added and removed lines and keeps the rest', () => {
    const before = 'Be safe.\nKeep answers short.'
    const after = 'Be safe.\nReply in the language of the question.\nKeep answers short.'
    expect(diffLines(before, after)).toEqual([
      { type: 'same', text: 'Be safe.' },
      { type: 'added', text: 'Reply in the language of the question.' },
      { type: 'same', text: 'Keep answers short.' },
    ])
  })

  it('shows a replaced line as removed then added', () => {
    expect(diffLines('Call 911.', 'Call 9-1-1 first.')).toEqual([
      { type: 'removed', text: 'Call 911.' },
      { type: 'added', text: 'Call 9-1-1 first.' },
    ])
  })
})
