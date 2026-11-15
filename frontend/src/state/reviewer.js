import { useSyncExternalStore } from 'react'

// There is no login in the MVP, so every decision is signed with the name the person enters here.
// It is kept in this browser only.
const KEY = 'kavasam.reviewer'
const EVENT = 'kavasam-reviewer'

function read() {
  try {
    return localStorage.getItem(KEY) || ''
  } catch {
    return ''
  }
}

export function setReviewerName(name) {
  try {
    localStorage.setItem(KEY, name.trim())
  } catch {
    // Storage blocked (private window): the name lasts until the page reloads.
  }
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

export function useReviewerName() {
  return useSyncExternalStore(subscribe, read, () => '')
}
