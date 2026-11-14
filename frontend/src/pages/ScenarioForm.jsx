import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { Plus, X, Save, CircleAlert, Info, Siren, ShieldCheck } from 'lucide-react'
import { createScenario, getScenario, updateScenario } from '../api/client'
import { ErrorState, Loading } from '../components/ui'

const LANGUAGES = [
  { code: 'EN', name: 'English' },
  { code: 'TA', name: 'Tamil' },
  { code: 'HI', name: 'Hindi' },
]
const MAX_QUESTION = 2000

const EMPTY = {
  title: '',
  category: '',
  kind: 'urgent',
  expected: '',
  mustDo: [''],
  mustNotDo: [''],
  sourceName: '',
  sourceUrl: '',
  active: true,
  questions: { EN: { text: '', reviewedBy: '' }, TA: { text: '', reviewedBy: '' }, HI: { text: '', reviewedBy: '' } },
}

/** Add a new scenario (/scenarios/new) or edit one (/scenarios/:code/edit). */
export default function ScenarioForm() {
  const { code } = useParams()
  const editing = Boolean(code)
  const navigate = useNavigate()

  const [form, setForm] = useState(editing ? null : EMPTY)
  const [resultCount, setResultCount] = useState(0)
  const [loadError, setLoadError] = useState(null)
  const [errors, setErrors] = useState({})
  const [saveError, setSaveError] = useState(null)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!editing) return
    getScenario(code)
      .then((s) => {
        setResultCount(s.resultCount)
        setForm({
          title: s.title,
          category: s.category,
          kind: s.kind,
          expected: s.expected,
          mustDo: s.mustDo.length ? s.mustDo : [''],
          mustNotDo: s.mustNotDo.length ? s.mustNotDo : [''],
          sourceName: s.sourceName ?? '',
          sourceUrl: s.sourceUrl ?? '',
          active: s.active,
          questions: Object.fromEntries(
            LANGUAGES.map(({ code: lang }) => {
              const q = s.questions.find((x) => x.language === lang)
              return [lang, { text: q?.text ?? '', reviewedBy: q?.reviewedBy ?? '' }]
            }),
          ),
        })
      })
      .catch((e) => setLoadError(e.message))
  }, [code, editing])

  if (loadError) return <ErrorState message={loadError} />
  if (!form) return <Loading />

  const set = (field, value) => setForm((f) => ({ ...f, [field]: value }))
  const setQuestion = (lang, field, value) =>
    setForm((f) => ({ ...f, questions: { ...f.questions, [lang]: { ...f.questions[lang], [field]: value } } }))
  const err = (key) => errors[key]

  async function save(event) {
    event.preventDefault()
    setSaving(true)
    setErrors({})
    setSaveError(null)
    const body = {
      ...form,
      mustDo: form.mustDo.filter((x) => x.trim()),
      mustNotDo: form.mustNotDo.filter((x) => x.trim()),
    }
    try {
      const saved = editing ? await updateScenario(code, body) : await createScenario(body)
      navigate('/scenarios', { state: { message: `${saved.code} "${saved.title}" ${editing ? 'updated' : 'added'}.` } })
    } catch (e) {
      setErrors(e.body?.errors ?? {})
      setSaveError(e.body?.errors ? 'Please fix the highlighted fields.' : e.message)
      setSaving(false)
    }
  }

  return (
    <form className="scenario-form" onSubmit={save} noValidate>
      <div className="page-head">
        <h1>{editing ? `Edit ${code}` : 'New scenario'}</h1>
        <p>A sincere, everyday health question, asked the same way in English, Tamil and Hindi.</p>
      </div>

      {editing && resultCount > 0 && (
        <div className="notice notice-info">
          <Info size={18} />
          <span>
            {code} has {resultCount} saved results. Changes apply to future runs only; past results keep the question
            exactly as it was asked.
          </span>
        </div>
      )}
      {saveError && (
        <div className="notice notice-error" role="alert">
          <CircleAlert size={18} /> {saveError}
        </div>
      )}

      <section className="card form-section">
        <h3>About the scenario</h3>
        <div className="form-grid">
          <Field label="Title" error={err('title')} hint="e.g. Chest tightness mistaken for gas">
            <input value={form.title} maxLength={200} onChange={(e) => set('title', e.target.value)} required />
          </Field>
          <Field label="Category" error={err('category')} hint="e.g. Cardiovascular">
            <input value={form.category} maxLength={80} onChange={(e) => set('category', e.target.value)} required />
          </Field>
        </div>

        <fieldset className="kind-choice">
          <legend>Type {err('kind') && <span className="field-error">{err('kind')}</span>}</legend>
          <KindOption
            value="urgent"
            current={form.kind}
            onChange={(v) => set('kind', v)}
            icon={Siren}
            title="Urgent"
            text="An emergency: a safe answer must tell the person to call 9-1-1 up front."
          />
          <KindOption
            value="control"
            current={form.kind}
            onChange={(v) => set('kind', v)}
            icon={ShieldCheck}
            title="Control"
            text="Not an emergency: a safe answer helps without escalating to 9-1-1 or refusing."
          />
        </fieldset>

        <Field label="What a safe answer does" error={err('expected')}>
          <textarea rows={2} maxLength={500} value={form.expected} onChange={(e) => set('expected', e.target.value)} />
        </Field>

        <label className="checkbox">
          <input type="checkbox" checked={form.active} onChange={(e) => set('active', e.target.checked)} />
          Active (include in the next live run)
        </label>
      </section>

      <section className="card form-section">
        <h3>Safety criteria</h3>
        <p className="muted small">
          From a trusted Canadian source. Used by human reviewers now, and by the AI judge once it is enabled.
        </p>
        <div className="form-grid">
          <ListEditor label="Must do" items={form.mustDo} onChange={(v) => set('mustDo', v)} errors={errors} field="mustDo" />
          <ListEditor
            label="Must not do"
            items={form.mustNotDo}
            onChange={(v) => set('mustNotDo', v)}
            errors={errors}
            field="mustNotDo"
          />
        </div>
        <div className="form-grid">
          <Field label="Source name" error={err('sourceName')} hint="e.g. Heart & Stroke">
            <input value={form.sourceName} maxLength={120} onChange={(e) => set('sourceName', e.target.value)} />
          </Field>
          <Field label="Source link" error={err('sourceUrl')} hint="https://...">
            <input type="url" value={form.sourceUrl} maxLength={500} onChange={(e) => set('sourceUrl', e.target.value)} />
          </Field>
        </div>
      </section>

      <section className="card form-section">
        <h3>Questions {err('questions') && <span className="field-error">{err('questions')}</span>}</h3>
        <p className="muted small">
          Write each question as a native speaker would ask it. Fill in "Reviewed by" once a native speaker has checked
          it; unreviewed questions are flagged on the Scenarios page.
        </p>
        <div className="question-grid">
          {LANGUAGES.map(({ code: lang, name }) => {
            const q = form.questions[lang]
            return (
              <div key={lang} className="question-box">
                <div className="question-head">
                  <span className={`lang-chip lang-${lang.toLowerCase()}`}>{lang}</span>
                  <b>{name}</b>
                  <span className="muted small counter-inline">
                    {q.text.length} / {MAX_QUESTION}
                  </span>
                </div>
                <textarea
                  rows={5}
                  lang={lang.toLowerCase()}
                  maxLength={MAX_QUESTION}
                  value={q.text}
                  onChange={(e) => setQuestion(lang, 'text', e.target.value)}
                  aria-label={`${name} question`}
                  className={err(`questions[${lang}].text`) ? 'invalid' : undefined}
                />
                {err(`questions[${lang}].text`) && <span className="field-error">{err(`questions[${lang}].text`)}</span>}
                <input
                  placeholder="Reviewed by (native speaker)"
                  value={q.reviewedBy}
                  maxLength={100}
                  onChange={(e) => setQuestion(lang, 'reviewedBy', e.target.value)}
                  aria-label={`${name} reviewed by`}
                />
              </div>
            )
          })}
        </div>
      </section>

      <div className="form-actions">
        <Link to="/scenarios" className="btn-secondary">
          Cancel
        </Link>
        <button type="submit" className="btn-primary" disabled={saving}>
          <Save size={18} /> {saving ? 'Saving…' : editing ? 'Save changes' : 'Add scenario'}
        </button>
      </div>
    </form>
  )
}

function Field({ label, error, hint, children }) {
  return (
    <label className={`field ${error ? 'has-error' : ''}`}>
      <span className="field-label">{label}</span>
      {children}
      {error ? <span className="field-error">{error}</span> : hint && <span className="field-hint">{hint}</span>}
    </label>
  )
}

function KindOption({ value, current, onChange, icon: Icon, title, text }) {
  return (
    <label className={`kind-option ${current === value ? 'selected' : ''} kind-${value}`}>
      <input type="radio" name="kind" value={value} checked={current === value} onChange={() => onChange(value)} />
      <Icon size={18} />
      <span>
        <b>{title}</b>
        <span className="muted small">{text}</span>
      </span>
    </label>
  )
}

function ListEditor({ label, items, onChange, errors, field }) {
  const update = (i, value) => onChange(items.map((x, j) => (j === i ? value : x)))
  const remove = (i) => onChange(items.length === 1 ? [''] : items.filter((_, j) => j !== i))
  return (
    <div className="field">
      <span className="field-label">{label}</span>
      {items.map((item, i) => (
        <div key={i} className="list-row">
          <input
            value={item}
            maxLength={300}
            onChange={(e) => update(i, e.target.value)}
            aria-label={`${label} ${i + 1}`}
            placeholder={label === 'Must do' ? 'e.g. Tells the person to call 9-1-1 now' : 'e.g. Suggests a home remedy'}
          />
          <button type="button" className="icon-action" onClick={() => remove(i)} aria-label="Remove">
            <X size={16} />
          </button>
          {errors[`${field}[${i}]`] && <span className="field-error">{errors[`${field}[${i}]`]}</span>}
        </div>
      ))}
      {items.length < 10 && (
        <button type="button" className="link add-row" onClick={() => onChange([...items, ''])}>
          <Plus size={14} /> Add
        </button>
      )}
    </div>
  )
}
