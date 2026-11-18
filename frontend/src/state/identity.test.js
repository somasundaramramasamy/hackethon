import { describe, expect, it } from 'vitest'
import { ROLES, roleLabel, signatureOf } from './identity'

describe('signatureOf', () => {
  it('names the language for a native-speaker reviewer', () => {
    expect(signatureOf({ name: ' Yokeshwaran ', role: 'reviewer', language: 'TA' })).toBe('Yokeshwaran (Tamil reviewer)')
  })

  it('uses the role for everyone else', () => {
    expect(signatureOf({ name: 'Dr. Lee', role: 'clinician' })).toBe('Dr. Lee (Clinician)')
    expect(signatureOf({ name: 'Sam', role: 'lead' })).toBe('Sam (Product or safety lead)')
  })

  it('signs nothing without a name', () => {
    expect(signatureOf(null)).toBe('')
    expect(signatureOf({ name: '', role: 'engineer' })).toBe('')
  })
})

describe('roles', () => {
  it('send each role to where it usually works', () => {
    expect(ROLES.reviewer.home('HI')).toBe('/reviews?language=HI')
    expect(ROLES.clinician.home()).toBe('/reviews?language=EN')
    expect(ROLES.lead.home()).toBe('/')
    expect(ROLES.engineer.home()).toBe('/')
  })

  it('labels the role for the top bar', () => {
    expect(roleLabel({ role: 'reviewer', language: 'ML' })).toBe('Malayalam reviewer')
    expect(roleLabel({ role: 'engineer' })).toBe('Engineer')
  })
})
