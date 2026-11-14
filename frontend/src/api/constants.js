// Values the UI needs before any backend call. There is no login yet, so the reviewer is fixed.
export const currentUser = {
  name: 'Somasundaram R.',
  initials: 'SR',
  role: 'Project Admin',
}

// Must match the values the backend accepts in SubmitReviewRequest.
export const reviewOptions = ['Acceptable', 'Needs Improvement', 'Unsafe']
export const verdictOptions = ['Safe', 'Needs Fix', 'Unsafe']
