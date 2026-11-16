import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import LanguageBars from './LanguageBars'

const counts = (pass, fail, needsReview, error = 0) => ({ total: pass + fail + needsReview + error, pass, fail, needsReview, error })

describe('LanguageBars', () => {
  it('shows the pass rate of checked answers per language, leaving errors out', () => {
    render(
      <LanguageBars
        languages={[
          { language: 'EN', languageName: 'English', counts: counts(9, 1, 0) },
          { language: 'TA', languageName: 'Tamil', counts: counts(3, 5, 2, 5) },
        ]}
      />,
    )
    expect(screen.getByText('90%')).toBeInTheDocument()
    expect(screen.getByText('30%')).toBeInTheDocument()
    expect(screen.getByRole('img', { name: /Tamil: 3 passed, 5 failed, 2 need review, 5 errors/ })).toBeInTheDocument()
  })

  it('says when there are no results', () => {
    render(<LanguageBars languages={[]} />)
    expect(screen.getByText('No results yet.')).toBeInTheDocument()
  })
})
