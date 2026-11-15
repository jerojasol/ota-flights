import type { AggregationResult, ItineraryPage, ProblemDetail } from './types'

export interface SearchParams {
  origin: string
  destination: string
  date: string
  maxPrice?: number
  carrier?: string
  cursor?: string
  limit?: number
}

export interface AggregationRequest {
  origin: string
  destination: string
  date: string
}

export class ApiError extends Error {
  readonly status: number
  readonly problem: ProblemDetail | null

  constructor(message: string, status: number, problem: ProblemDetail | null) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.problem = problem
  }
}

export function buildQuery(params: SearchParams): string {
  const query = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === '') continue
    query.set(key, String(value))
  }
  return query.toString()
}

async function request<T>(url: string, init: RequestInit): Promise<T> {
  let response: Response
  try {
    response = await fetch(url, { ...init, headers: { Accept: 'application/json', ...init.headers } })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error
    throw new ApiError(error instanceof Error ? error.message : 'Network error', 0, null)
  }

  if (!response.ok) {
    let problem: ProblemDetail | null = null
    if (response.headers.get('content-type')?.includes('application/problem+json')) {
      try {
        problem = (await response.json()) as ProblemDetail
      } catch {
        problem = null
      }
    }
    const message = problem?.detail ?? problem?.title ?? `HTTP ${response.status}`
    throw new ApiError(message, response.status, problem)
  }

  try {
    return (await response.json()) as T
  } catch {
    throw new ApiError('Invalid JSON response', response.status, null)
  }
}

export function searchItineraries(params: SearchParams, signal?: AbortSignal): Promise<ItineraryPage> {
  return request<ItineraryPage>(`/api/itineraries?${buildQuery(params)}`, { signal })
}

export function triggerAggregation(body: AggregationRequest): Promise<AggregationResult> {
  return request<AggregationResult>('/api/admin/aggregations', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}
