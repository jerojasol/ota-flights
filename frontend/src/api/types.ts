// Mirrors flight-aggregator/infrastructure/.../web/dto. Update in the same change as the DTOs.

export type Freshness = 'FRESH' | 'STALE' | 'REFRESHING' | 'MISSING'
export type ProviderStatus = 'OK' | 'TIMEOUT' | 'ERROR' | 'CIRCUIT_OPEN'
export type Source = 'ALPHA' | 'BETA' | 'GAMMA'

export interface Price {
  amount: number
  currency: string
}

export interface Segment {
  carrier: string
  flightNumber: string
  from: string
  to: string
  departureAt: string
  arrivalAt: string
}

export interface Itinerary {
  id: string
  origin: string
  destination: string
  segments: Segment[]
  basePrice: Price
  finalPrice: Price
  source: Source
  connections: number
  /** ISO-8601 duration, e.g. "PT8H35M" */
  totalDuration: string
}

export interface ProviderOutcome {
  status: ProviderStatus
  received: number
  rejected: number
  elapsedMs: number
}

/** May be empty: never assume all providers are present. */
export type ProviderOutcomes = Partial<Record<string, ProviderOutcome>>

export interface ItineraryPage {
  items: Itinerary[]
  nextCursor: string | null
  freshness: Freshness
  aggregatedAt: string | null
  providers: ProviderOutcomes
}

export interface AggregationResult {
  persisted: number
  aggregatedAt: string
  providers: ProviderOutcomes
}

export interface ProblemDetail {
  status: number
  title?: string
  detail?: string
  errors?: Record<string, string>
  correlationId?: string
}
