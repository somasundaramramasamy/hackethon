import { useSyncExternalStore } from 'react'

// Who is using Kavasam: a name and a role that sign every review, approval and test. It identifies
// people; it does not authenticate them. A deployment would connect the organization's own sign-in.
// The identity lasts as long as this tab: it survives a refresh and is forgotten when the tab or the
// browser closes, so the next person starts with an empty window. "Check out" forgets it at once.

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
    home: () => '/',
  },
}

export const REVIEW_LANGUAGES = [
  ['TA', 'Tamil'],
  ['HI', 'Hindi'],
  ['ML', 'Malayalam'],
  ['EN', 'English'],
]

const KEY = 'kavasam.identity' // sessionStorage: the person using this tab
const VISIT_KEY = 'kavasam.visit' // sessionStorage: 'confirmed' or 'viewer'
// Earlier versions kept the name in localStorage, where it outlived the visit; remove it.
const LEGACY_KEYS = ['kavasam.identity', 'kavasam.reviewer']
try {
  LEGACY_KEYS.forEach((k) => localStorage.removeItem(k))
} catch {
  // Storage blocked: nothing was kept there either.
}
const EVENT = 'kavasam-identity'

let snapshot = null
let snapshotKey = null
// Used only when the browser blocks storage.
let memory = null
const SERVER_SNAPSHOT = { stored: null, visit: '' }

function readStored() {
  try {
    const raw = sessionStorage.getItem(KEY)
    return raw ? JSON.parse(raw) : null
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
    sessionStorage.setItem(KEY, JSON.stringify(identity))
    sessionStorage.setItem(VISIT_KEY, 'confirmed')
  } catch {
    // Storage blocked (private window): the identity lasts until the page reloads.
    memory = { stored: identity, visit: 'confirmed' }
  }
  notify()
}

/** Forgets who is using this tab, so the next person starts with an empty window. */
export function checkOut() {
  try {
    sessionStorage.removeItem(KEY)
    sessionStorage.removeItem(VISIT_KEY)
  } catch {
    // Storage blocked: the in-memory identity below is all there is.
  }
  memory = null
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
