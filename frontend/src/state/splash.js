// Whether the load-up screen already played in this visit.
const KEY = 'kavasam.splash'

export function splashSeen() {
  try {
    // ?intro plays it again, e.g. when recording a demo.
    if (new URLSearchParams(window.location.search).has('intro')) return false
    return sessionStorage.getItem(KEY) === 'shown'
  } catch {
    return false
  }
}

export function markSplashSeen() {
  try {
    sessionStorage.setItem(KEY, 'shown')
  } catch {
    // Storage blocked: the splash may play again on the next load, which is harmless.
  }
}
