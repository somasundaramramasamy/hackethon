import { createContext, useContext } from 'react'

// Settings from GET /api/settings: the models on offer, languages, and whether this is the read-only demo.
export const SettingsContext = createContext(null)

export function useSettings() {
  return useContext(SettingsContext)
}
