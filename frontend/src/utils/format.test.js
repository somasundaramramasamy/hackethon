import { describe, expect, it } from 'vitest'
import { htmlLang, percent, shortModel } from './format'

describe('format', () => {
  it('shortens provider model ids', () => {
    expect(shortModel('meta-llama/llama-3.2-3b-instruct')).toBe('llama-3.2-3b-instruct')
    expect(shortModel(null)).toBe('—')
  })

  it('rounds percentages and shows a dash when there is no value', () => {
    expect(percent(66.67)).toBe('67%')
    expect(percent(null)).toBe('—')
  })

  it('maps language codes to HTML lang values for each script', () => {
    expect(htmlLang('ML')).toBe('ml')
    expect(htmlLang('TA')).toBe('ta')
    expect(htmlLang('XX')).toBeUndefined()
  })
})
