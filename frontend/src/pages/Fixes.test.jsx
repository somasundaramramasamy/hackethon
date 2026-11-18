import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as api from '../api/client'
import Fixes from './Fixes'

vi.mock('../api/client', () => ({
  getPrompts: vi.fn(),
  getRuns: vi.fn(),
  getScenarioFixes: vi.fn(),
  switchScenarioFix: vi.fn(),
  learnFixesFromRun: vi.fn(),
}))
vi.mock('../state/identity', () => ({ useSigner: () => ({ signed: (fn) => fn('Tester (Engineer)') }) }))
vi.mock('../state/settings', () => ({ useSettings: () => ({ readOnly: false }) }))

const fix = (over) => ({
  id: 1, language: 'TA', languageName: 'Tamil', fixText: 'Say first that it may be a heart attack; call 9-1-1.',
  failureReason: 'Treats chest pain as gas.', sourceResultId: 11, sourceRunCode: 'RUN-1', sourceRunName: 'Baseline',
  revision: 1, active: true, state: 'waiting', timesUsed: 0, timesPassed: 0, updatedAt: '2026-11-18T10:00:00Z',
  ...over,
})

beforeEach(() => {
  vi.clearAllMocks()
  api.getPrompts.mockResolvedValue([{ version: 3, status: 'active', summary: 'Current prompt.', createdAt: '2026-11-16T10:00:00Z' }])
  api.getRuns.mockResolvedValue([
    { code: 'RUN-1', name: 'Baseline', type: 'baseline', status: 'completed', promptVersion: 3,
      counts: { total: 10, pass: 7, fail: 3, needsReview: 0, error: 0 } },
  ])
  api.getScenarioFixes.mockResolvedValue({
    total: 3, waiting: 1, working: 1, off: 1,
    scenarios: [
      { code: 'SC-01', title: 'Chest pain', kind: 'urgent',
        fixes: [fix(), fix({ id: 2, language: 'HI', languageName: 'Hindi', state: 'working', timesUsed: 3, timesPassed: 3 })] },
      { code: 'SC-02', title: 'Honey', kind: 'control',
        fixes: [fix({ id: 3, language: 'EN', languageName: 'English', state: 'off', active: false, changedBy: 'Soma' })] },
    ],
  })
})

const renderPage = () =>
  render(
    <MemoryRouter initialEntries={['/fixes']}>
      <Fixes />
    </MemoryRouter>,
  )

describe('Fixes', () => {
  it('lists each scenario once with the AI judge fix saved for each language', async () => {
    renderPage()
    expect(await screen.findByText('Chest pain')).toBeInTheDocument()
    expect(screen.getAllByText('Say first that it may be a heart attack; call 9-1-1.')).toHaveLength(3)
    expect(screen.getByText('Waiting for the next run')).toBeInTheDocument()
    expect(screen.getByText('Used in 3 answers since, and every one passed.')).toBeInTheDocument()
    expect(screen.getByText(/Turned off by Soma/)).toBeInTheDocument()
    expect(screen.queryByText(/Ask AI to investigate/)).not.toBeInTheDocument()
  })

  it('filters by language and by state', async () => {
    renderPage()
    await screen.findByText('Honey')
    fireEvent.click(screen.getByRole('button', { name: /Hindi/ }))
    expect(screen.queryByText('Honey')).not.toBeInTheDocument()
    expect(screen.getByText('Chest pain')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: /All languages/ }))
    fireEvent.click(screen.getByRole('button', { name: /turned off/ }))
    expect(screen.getByText('Honey')).toBeInTheDocument()
    expect(screen.queryByText('Chest pain')).not.toBeInTheDocument()
  })

  it('turns a fix off, signed by the person', async () => {
    api.switchScenarioFix.mockResolvedValue({})
    renderPage()
    await screen.findByText('Chest pain')
    fireEvent.click(screen.getAllByRole('button', { name: /Turn off/ })[0])
    await waitFor(() => expect(api.switchScenarioFix).toHaveBeenCalledWith(1, false, 'Tester (Engineer)'))
  })

  it('saves fixes from an earlier test', async () => {
    api.learnFixesFromRun.mockResolvedValue({ saved: 2 })
    renderPage()
    fireEvent.click(await screen.findByRole('button', { name: /Save its fixes/ }))
    await waitFor(() => expect(api.learnFixesFromRun).toHaveBeenCalledWith('RUN-1'))
    expect(await screen.findByText('Saved 2 fixes from this test.')).toBeInTheDocument()
  })

  it('explains that there is nothing yet', async () => {
    api.getScenarioFixes.mockResolvedValue({ total: 0, waiting: 0, working: 0, off: 0, scenarios: [] })
    renderPage()
    expect(await screen.findByText('No fixes saved yet.')).toBeInTheDocument()
  })
})
