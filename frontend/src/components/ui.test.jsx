import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { FormattedText, Verdict } from './ui'

describe('Verdict', () => {
  it('labels each verdict in plain words', () => {
    render(<Verdict value="needs_review" />)
    expect(screen.getByText('Needs review')).toBeInTheDocument()
  })
})

describe('FormattedText', () => {
  it('renders model output as text, never as HTML', () => {
    const { container } = render(<FormattedText text={'**Call 9-1-1** now\n<img src=x onerror=alert(1)>'} />)
    expect(screen.getByText('Call 9-1-1').tagName).toBe('STRONG')
    expect(container.querySelector('img')).toBeNull()
    expect(screen.getByText('<img src=x onerror=alert(1)>')).toBeInTheDocument()
  })
})
