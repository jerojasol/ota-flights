import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, searchItineraries } from './client'
import type { ItineraryPage } from './types'

const page: ItineraryPage = {
  items: [],
  nextCursor: null,
  freshness: 'FRESH',
  aggregatedAt: '2026-11-14T10:00:00Z',
  providers: {},
}

function stubFetch(impl: (...args: Parameters<typeof fetch>) => Promise<Response>) {
  const fn = vi.fn(impl)
  vi.stubGlobal('fetch', fn)
  return fn
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('searchItineraries', () => {
  it('omits undefined values and includes the cursor', async () => {
    const fetchMock = stubFetch(async () => Response.json(page))

    await searchItineraries({ origin: 'MAD', destination: 'JFK', date: '2026-11-20', maxPrice: undefined, carrier: '', cursor: 'abc' })

    const url = new URL(String(fetchMock.mock.calls[0]?.[0]), 'http://localhost')
    expect(url.pathname).toBe('/api/itineraries')
    expect([...url.searchParams.keys()]).toEqual(['origin', 'destination', 'date', 'cursor'])
    expect(url.searchParams.get('cursor')).toBe('abc')
  })

  it('resolves a 200 to the parsed page', async () => {
    stubFetch(async () => Response.json(page))

    await expect(searchItineraries({ origin: 'MAD', destination: 'JFK', date: '2026-11-20' })).resolves.toEqual(page)
  })

  it('rejects a 400 with Problem Details as ApiError carrying the field errors', async () => {
    const problem = { status: 400, title: 'Bad Request', errors: { origin: 'must be an IATA code' } }
    stubFetch(async () =>
      new Response(JSON.stringify(problem), { status: 400, headers: { 'Content-Type': 'application/problem+json' } }),
    )

    const error = await searchItineraries({ origin: 'X', destination: 'JFK', date: '2026-11-20' }).catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(400)
    expect((error as ApiError).problem?.errors).toEqual({ origin: 'must be an IATA code' })
  })

  it('rejects a network failure as ApiError with no problem', async () => {
    stubFetch(async () => {
      throw new TypeError('Failed to fetch')
    })

    const error = await searchItineraries({ origin: 'MAD', destination: 'JFK', date: '2026-11-20' }).catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).problem).toBeNull()
  })

  it('rejects a non-JSON error body as ApiError with no problem', async () => {
    stubFetch(async () => new Response('<html>Bad gateway</html>', { status: 502, headers: { 'Content-Type': 'text/html' } }))

    const error = await searchItineraries({ origin: 'MAD', destination: 'JFK', date: '2026-11-20' }).catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(502)
    expect((error as ApiError).problem).toBeNull()
  })
})
