// The only place the UI talks to the backend. In development Vite proxies /api to localhost:8080.
import { currentUser } from './constants'

export class ApiError extends Error {
  constructor(message, status, body) {
    super(message)
    this.status = status
    this.body = body
  }
}

async function request(path, options = {}) {
  let res
  try {
    res = await fetch(path, {
      ...options,
      headers: { 'Content-Type': 'application/json', ...options.headers },
    })
  } catch {
    throw new ApiError('Cannot reach the backend. Is it running on port 8080?', 0)
  }
  if (res.status === 204) return null
  const body = await res.json().catch(() => null)
  if (!res.ok) {
    throw new ApiError(body?.detail || `Request failed (HTTP ${res.status})`, res.status, body)
  }
  return body
}

const post = (path, data) => request(path, { method: 'POST', body: data === undefined ? undefined : JSON.stringify(data) })
const id = (value) => encodeURIComponent(value)

// Dashboard and runs
export const getDashboard = () => request('/api/dashboard')
export const getRuns = () => request('/api/runs')

// Human review
export const getRunReview = (runId) => request(`/api/runs/${id(runId)}/review`)
export const submitReview = (runId, payload) =>
  post(`/api/runs/${id(runId)}/review`, { ...payload, reviewer: currentUser.name })
export const getPendingReviewCount = () => request('/api/reviews/pending/count').then((r) => r.count)

// Test scenarios
const json = (method, data) => ({ method, body: JSON.stringify(data) })
export const getScenarios = () => request('/api/scenarios') // active only: what the next run asks
export const getAllScenarios = () => request('/api/scenarios?includeInactive=true')
export const getScenario = (code) => request(`/api/scenarios/${id(code)}`)
export const createScenario = (data) => post('/api/scenarios', data)
export const updateScenario = (code, data) => request(`/api/scenarios/${id(code)}`, json('PUT', data))
export const setScenarioActive = (code, active) =>
  request(`/api/scenarios/${id(code)}/active`, json('PATCH', { active }))
export const deleteScenario = (code) => request(`/api/scenarios/${id(code)}`, { method: 'DELETE' })
export const exportScenarios = () => request('/api/scenarios/export')
export const importScenarios = (scenarios) => post('/api/scenarios/import', scenarios)

// Live testing against the assistant under test
export const startLiveRun = () => post('/api/live-runs')
export const getLatestLiveRun = () => request('/api/live-runs/latest')
export const getLiveRun = (runId) => request(`/api/live-runs/${id(runId)}`)
export const getAiStatus = () => request('/api/ai/status')

export const getCurrentUser = () => currentUser
