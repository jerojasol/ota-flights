export interface SearchState {
  origin: string
  destination: string
  date: string
  maxPrice?: number
  carrier?: string
}

export function parseSearch(search: string): SearchState | null {
  const query = new URLSearchParams(search)
  const origin = query.get('origin')?.toUpperCase() ?? ''
  const destination = query.get('destination')?.toUpperCase() ?? ''
  const date = query.get('date') ?? ''
  if (!origin || !destination || !date) return null

  const state: SearchState = { origin, destination, date }
  const maxPrice = Number(query.get('maxPrice'))
  if (query.get('maxPrice') && Number.isFinite(maxPrice)) state.maxPrice = maxPrice
  const carrier = query.get('carrier')?.toUpperCase()
  if (carrier) state.carrier = carrier
  return state
}

export function serializeSearch(state: SearchState): string {
  const query = new URLSearchParams({ origin: state.origin, destination: state.destination, date: state.date })
  if (state.maxPrice !== undefined) query.set('maxPrice', String(state.maxPrice))
  if (state.carrier) query.set('carrier', state.carrier)
  return `?${query.toString()}`
}
