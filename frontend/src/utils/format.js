const dateFmt = new Intl.DateTimeFormat('en-CA', {
  month: 'short',
  day: 'numeric',
  hour: 'numeric',
  minute: '2-digit',
})

export const formatDate = (iso) => (iso ? dateFmt.format(new Date(iso)) : '—')

/** Short name for a model id, e.g. "meta-llama/llama-3.2-3b-instruct" → "llama-3.2-3b-instruct". */
export const shortModel = (id) => (id ? id.split('/').pop() : '—')

export const percent = (value) => (value == null ? '—' : `${Math.round(value)}%`)

/** HTML lang attribute for a language code, so screen readers and fonts handle each script. */
export const htmlLang = (code) => ({ EN: 'en', TA: 'ta', HI: 'hi', ML: 'ml' })[code] ?? undefined
