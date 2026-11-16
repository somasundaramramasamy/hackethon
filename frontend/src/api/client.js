// The only place the UI talks to the backend. In development Vite proxies /api to localhost:8080.

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
    const fieldErrors = body?.errors ? Object.values(body.errors).join('; ') : ''
    const message = body?.detail && body.detail !== 'Validation failed' ? body.detail : fieldErrors
    throw new ApiError(message || `Request failed (HTTP ${res.status})`, res.status, body)
  }
  return body
}

const send = (method, path, data) =>
  request(path, { method, body: data === undefined ? undefined : JSON.stringify(data) })
const post = (path, data) => send('POST', path, data)
const id = (value) => encodeURIComponent(value)

// App
export const getSettings = () => request('/api/settings')
export const getDashboard = () => request('/api/dashboard')

// Runs and results
export const getRuns = () => request('/api/runs')
export const getRun = (code) => request(`/api/runs/${id(code)}`)
export const startRun = (body) => post('/api/runs', body)
export const getComparison = (code) => request(`/api/runs/${id(code)}/comparison`)
export const getResult = (resultId) => request(`/api/results/${id(resultId)}`)

// Human review
export const getReviewQueue = ({ run, language } = {}) => {
  const params = new URLSearchParams()
  if (run) params.set('run', run)
  if (language) params.set('language', language)
  const query = params.toString()
  return request(`/api/reviews/queue${query ? `?${query}` : ''}`)
}
export const getReviewQueueCount = () => request('/api/reviews/queue/count').then((r) => r.count)
export const getAgreement = () => request('/api/reviews/agreement')
export const submitReview = (resultId, body) => post(`/api/results/${id(resultId)}/review`, body)

// Agents and fixes
export const startInvestigation = (resultId, person) =>
  post(`/api/results/${id(resultId)}/investigations`, { person })
export const getInvestigation = (investigationId) => request(`/api/investigations/${id(investigationId)}`)
export const proposeFix = (runCode, person) => post(`/api/runs/${id(runCode)}/fix-proposals`, { person })
export const getPrompts = () => request('/api/prompts')
export const getPrompt = (version) => request(`/api/prompts/${id(version)}`)
export const decidePrompt = (version, decision, person, note) =>
  post(`/api/prompts/${id(version)}/${decision}`, { person, note })

// Test scenarios
export const getScenarios = () => request('/api/scenarios') // active only: what the next run asks
export const getAllScenarios = () => request('/api/scenarios?includeInactive=true')
export const getScenario = (code) => request(`/api/scenarios/${id(code)}`)
export const createScenario = (data) => post('/api/scenarios', data)
export const updateScenario = (code, data) => send('PUT', `/api/scenarios/${id(code)}`, data)
export const setScenarioActive = (code, active) => send('PATCH', `/api/scenarios/${id(code)}/active`, { active })
export const deleteScenario = (code) => request(`/api/scenarios/${id(code)}`, { method: 'DELETE' })
export const exportScenarios = () => request('/api/scenarios/export')
export const importScenarios = (scenarios) => post('/api/scenarios/import', scenarios)
