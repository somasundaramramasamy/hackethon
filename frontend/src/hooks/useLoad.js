import { useCallback, useEffect, useRef, useState } from 'react'

/**
 * Loads data with `load()` and keeps it fresh. While `shouldPoll(data)` is true it reloads every
 * `intervalMs`, e.g. while a run or investigation is still in progress. Returns
 * { data, error, loading, reload }.
 */
export function useLoad(load, deps = [], { shouldPoll = () => false, intervalMs = 2500 } = {}) {
  const [state, setState] = useState({ data: null, error: null, loading: true })
  const pollRef = useRef(shouldPoll)
  pollRef.current = shouldPoll
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const run = useCallback(load, deps)

  const reload = useCallback(
    () =>
      run()
        .then((data) => setState({ data, error: null, loading: false }))
        .catch((error) => setState((s) => ({ ...s, error, loading: false }))),
    [run],
  )

  useEffect(() => {
    let cancelled = false
    let timer
    const tick = () =>
      run()
        .then((data) => {
          if (cancelled) return
          setState({ data, error: null, loading: false })
          if (pollRef.current(data)) timer = setTimeout(tick, intervalMs)
        })
        .catch((error) => {
          if (!cancelled) setState((s) => ({ ...s, error, loading: false }))
        })
    setState((s) => ({ ...s, loading: true }))
    tick()
    return () => {
      cancelled = true
      clearTimeout(timer)
    }
  }, [run, intervalMs])

  return { ...state, reload }
}
