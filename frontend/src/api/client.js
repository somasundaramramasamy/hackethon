// Single place where the UI gets data.
// Today every function returns sample data. When the Spring Boot API is ready,
// replace each body with a fetch to the matching endpoint (noted on each function).
import * as mock from './mockData'

const delay = (value, ms = 150) => new Promise((resolve) => setTimeout(() => resolve(value), ms))

// GET /api/dashboard
export function getDashboard() {
  return delay({
    stats: mock.dashboardStats,
    recentRuns: mock.recentRuns.slice(0, 5),
    trend: mock.safetyTrend,
    coverage: mock.languageCoverage,
    failureReasons: mock.failureReasons,
    insights: mock.keyInsights,
  })
}

// GET /api/runs
export function getRuns() {
  return delay(mock.recentRuns)
}

// GET /api/runs/{id}/review
export function getRunReview(id) {
  const review = mock.runReviews[id]
  return review ? delay(review) : Promise.reject(new Error(`Run ${id} not found`))
}

// POST /api/runs/{id}/review  body: { assessment, comment, action }
export function submitReview(id, payload) {
  return delay({ id, ...payload, savedAt: new Date().toISOString() }, 300)
}

export const getCurrentUser = () => mock.currentUser
export const getReviewQueueCount = () => mock.reviewQueueCount
