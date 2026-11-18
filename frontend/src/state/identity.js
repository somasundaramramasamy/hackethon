import { useSyncExternalStore } from 'react'

// Who is using Kavasam: a name and a role that sign every review, approval and test. It identifies
// people; it does not authenticate them. A deployment would connect the organization's own sign-in.
// The identity is remembered in this browser, and confirmed once per visit.

export const ROLES = {
  reviewer: {
    label: 'Native-speaker reviewer',
    hint: 'Checks answers in your own language',
    home: (language) => `/reviews?language=${language}`,
  },
  clinician: {
    label: 'Clinician',
    hint: 'Judges whether the advice is medically safe',
    home: () => '/reviews?language=EN',
  },
  lead: {
    label: 'Product or safety lead',
    hint: 'Decides on fixes and on launch',
    home: () => '/',
  },
  engineer: {
    label: 'Engineer',
    hint: 'Runs tests and applies adopted fixes',
    home: () => '/runs',
  },
}

export const REVIEW_LANGUAGES = [
  ['TA', 'Tamil'],
  ['HI', 'Hindi'],
  ['ML', 'Malayalam'],
  ['EN', 'English'],
]

const KEY = 'kavasam.identity'
const OLD_KEY = 'kavasam.reviewer'
const VISIT_KEY = 'kavasam.visit' // sessionStorage: 'confirmed' or 'viewer'
const EVENT = 'kavasam-identity'

let snapshot = null
let snapshotKey = null
// Used only when the browser blocks storage.
let memory = null
const SERVER_SNAPSHOT = { stored: null, visit: '' }

function readStored() {
  try {
    const raw = localStorage.getItem(KEY)
    if (raw) return JSON.parse(raw)
    const oldName = localStorage.getItem(OLD_KEY)
    return oldName ? { name: oldName, role: null, language: null } : null
  } catch {
    return null
  }
}

function readVisit() {
  try {
    return sessionStorage.getItem(VISIT_KEY) || ''
  } catch {
    return ''
  }
}

function read() {
  const stored = readStored() ?? memory?.stored ?? null
  const visit = readVisit() || memory?.visit || ''
  const key = JSON.stringify([stored, visit])
  if (key !== snapshotKey) {
    snapshotKey = key
    snapshot = { stored, visit }
  }
  return snapshot
}

function notify() {
  window.dispatchEvent(new Event(EVENT))
}

function subscribe(onChange) {
  window.addEventListener(EVENT, onChange)
  window.addEventListener('storage', onChange)
  return () => {
    window.removeEventListener(EVENT, onChange)
    window.removeEventListener('storage', onChange)
  }
}

/** The text that signs a decision, e.g. "Yokeshwaran (Tamil reviewer)". */
export function signatureOf(identity) {
  if (!identity?.name) return ''
  const name = identity.name.trim()
  if (identity.role === 'reviewer') {
    const language = REVIEW_LANGUAGES.find(([code]) => code === identity.language)?.[1]
    return language ? `${name} (${language} reviewer)` : `${name} (Reviewer)`
  }
  const role = ROLES[identity.role]
  return role ? `${name} (${role.label})` : name
}

export function roleLabel(identity) {
  if (identity?.role === 'reviewer') {
    const language = REVIEW_LANGUAGES.find(([code]) => code === identity.language)?.[1]
    return language ? `${language} reviewer` : 'Reviewer'
  }
  return ROLES[identity?.role]?.label ?? ''
}

export function saveIdentity(identity) {
  try {
    localStorage.setItem(KEY, JSON.stringify(identity))
    localStorage.removeItem(OLD_KEY)
    sessionStorage.setItem(VISIT_KEY, 'confirmed')
  } catch {
    // Storage blocked (private window): the identity lasts until the page reloads.
    memory = { stored: identity, visit: 'confirmed' }
  }
  notify()
}

export function continueAsViewer() {
  try {
    sessionStorage.setItem(VISIT_KEY, 'viewer')
  } catch {
    memory = { ...(memory ?? { stored: null }), visit: 'viewer' }
  }
  notify()
}


/**
 * { identity, signature, confirmed, viewer }: identity is the remembered name and role, confirmed
 * whether it was confirmed during this visit, viewer whether the person chose to just look around.
 */
export function useIdentity() {
  const { stored: identity, visit: state } = useSyncExternalStore(subscribe, read, () => SERVER_SNAPSHOT)
  const confirmed = state === 'confirmed' && Boolean(identity?.name && identity?.role)
  return { identity, signature: confirmed ? signatureOf(identity) : '', confirmed, viewer: state === 'viewer' }
}

// Asking for an identity from anywhere in the app: the dialog listens, and runs the pending action
// with the new signature once the person continues.
const ASK = 'kavasam-ask-identity'

export function askForIdentity(then) {
  window.dispatchEvent(new CustomEvent(ASK, { detail: { then } }))
}

export function onAskForIdentity(handler) {
  const listener = (e) => handler(e.detail?.then)
  window.addEventListener(ASK, listener)
  return () => window.removeEventListener(ASK, listener)
}

/**
 * For actions that must be signed: signed(fn) runs fn(signature) at once, or first asks who is using
 * the app and then runs it.
 */
export function useSigner() {
  const { signature } = useIdentity()
  const signed = (fn) => (signature ? fn(signature) : askForIdentity(fn))
  return { signature, signed }
}
