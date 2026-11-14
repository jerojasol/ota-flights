import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import App from './App'
import { ProviderPanel } from './components/ProviderPanel'
import type { Freshness, Itinerary, ItineraryPage, ProviderOutcomes } from './api/types'

function itinerary(id: string, amount: number, origin = 'MAD', destination = 'JFK'): Itinerary {
  return {
    id,
    origin,
    destination,
    segments: [{ carrier: 'IB', flightNumber: `IB${id}`, from: origin, to: destination, departureAt: '2026-11-20T07:35:00Z', arrivalAt: '2026-11-20T16:10:00Z' }],
    basePrice: { amount: amount - 5, currency: 'EUR' },
    finalPrice: { amount, currency: 'EUR' },
    source: 'ALPHA',
    connections: 0,
    totalDuration: 'PT8H35M',
  }
}

function page(freshness: Freshness, items: Itinerary[] = [itinerary('a', 100)], nextCursor: string | null = null, providers: ProviderOutcomes = {}): ItineraryPage {
  return { items, nextCursor, freshness, aggregatedAt: new Date(Date.now() - 120_000).toISOString(), providers }
}

const json = (body: unknown, status = 200, type = 'application/json') =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': type } })

interface Call {
  url: URL
  method: string
  body: unknown
}

function stubFetch(handler: (call: Call) => Promise<Response> | Response) {
  const calls: Call[] = []
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const call = {
      url: new URL(String(input), 'http://localhost'),
      method: init?.method ?? 'GET',
      body: init?.body ? JSON.parse(String(init.body)) : undefined,
    }
    calls.push(call)
    return handler(call)
  }))
  return {
    polls: (origin = 'MAD') => calls.filter((c) => c.url.searchParams.get('limit') === '1' && c.url.searchParams.get('origin') === origin).length,
    searches: () => calls.filter((c) => c.url.searchParams.get('limit') === '20').length,
    posts: () => calls.filter((c) => c.method === 'POST'),
    all: calls,
  }
}

const isPoll = (url: URL) => url.searchParams.get('limit') === '1'

function renderApp(search = '?origin=MAD&destination=JFK&date=2026-11-20') {
  window.history.replaceState(null, '', `/${search}`)
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, refetchOnWindowFocus: false } } })
  return render(
    <QueryClientProvider client={client}>
      <App />
    </QueryClientProvider>,
  )
}

async function advance(ms: number) {
  await act(() => vi.advanceTimersByTimeAsync(ms))
}

const user = () => userEvent.setup({ advanceTimers: vi.advanceTimersByTime })

beforeEach(() => {
  vi.useFakeTimers({ shouldAdvanceTime: true })
})

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

describe('freshness polling', () => {
  it('polls a STALE first page after 2 s, stops on FRESH and refetches the results once', async () => {
    const api = stubFetch(({ url }) => json(page(isPoll(url) ? 'FRESH' : 'STALE')))
    renderApp()
    expect(await screen.findByText('Refreshing in background')).toBeInTheDocument()
    expect(api.polls()).toBe(0)

    await advance(2000)
    expect(api.polls()).toBe(1)
    expect(await screen.findByText('Up to date')).toBeInTheDocument()

    await advance(20_000)
    expect(api.polls()).toBe(1)
    expect(api.searches()).toBe(2)
  })

  it('never polls when the first page is FRESH', async () => {
    const api = stubFetch(() => json(page('FRESH')))
    renderApp()
    expect(await screen.findByText('Up to date')).toBeInTheDocument()
    expect(screen.getByText(/aggregated 2 min ago/)).toBeInTheDocument()

    await advance(60_000)
    expect(api.polls()).toBe(0)
  })

  it('stops after 15 STALE polls and says the data may be out of date', async () => {
    const api = stubFetch(() => json(page('STALE')))
    renderApp()
    await screen.findByText('Refreshing in background')

    for (let i = 0; i < 20; i++) await advance(2000)

    expect(api.polls()).toBe(15)
    expect(screen.getByText('May be out of date')).toBeInTheDocument()
    await advance(30_000)
    expect(api.polls()).toBe(15)
  })

  it('stops polling the old search when the search changes', async () => {
    const api = stubFetch(({ url }) =>
      url.searchParams.get('origin') === 'MAD' ? json(page('STALE')) : json(page('FRESH', [itinerary('b', 90, 'BCN', 'LHR')])),
    )
    renderApp()
    await screen.findByText('Refreshing in background')
    await advance(2000)
    expect(api.polls('MAD')).toBe(1)

    act(() => {
      window.history.pushState(null, '', '/?origin=BCN&destination=LHR&date=2026-11-20')
      window.dispatchEvent(new PopStateEvent('popstate'))
    })
    expect(await screen.findByText('IBb')).toBeInTheDocument()

    await advance(30_000)
    expect(api.polls('MAD')).toBe(1)
    expect(api.polls('BCN')).toBe(0)
  })

  it('offers a reload instead of replacing the list when more pages are loaded', async () => {
    let pollFresh = false
    const api = stubFetch(({ url }) => {
      if (isPoll(url)) return json(page(pollFresh ? 'FRESH' : 'STALE'))
      if (url.searchParams.get('cursor') === 'c1') return json(page('STALE', [itinerary('c', 300), itinerary('d', 400)]))
      return json(page('STALE', [itinerary('a', 100), itinerary('b', 200)], 'c1'))
    })
    renderApp()
    await user().click(await screen.findByRole('button', { name: 'Load more' }))
    expect(await screen.findByText('Showing 4 itineraries')).toBeInTheDocument()
    const searchesBefore = api.searches()

    pollFresh = true
    await advance(2000)

    expect(await screen.findByText(/updated results are available/i)).toBeInTheDocument()
    expect(screen.getByText('Showing 4 itineraries')).toBeInTheDocument()
    expect(api.searches()).toBe(searchesBefore)

    await user().click(screen.getByRole('button', { name: 'Reload' }))
    expect(await screen.findByText('Showing 2 itineraries')).toBeInTheDocument()
    expect(screen.queryByText(/updated results are available/i)).not.toBeInTheDocument()
    expect(api.all.at(-1)!.url.searchParams.get('cursor')).toBeNull()
  })
})

describe('ProviderPanel', () => {
  const noop = () => {}

  it('always shows three rows and counts only OK providers as answered', () => {
    render(
      <ProviderPanel
        providers={{
          ALPHA: { status: 'OK', received: 249, rejected: 13, elapsedMs: 2310 },
          BETA: { status: 'TIMEOUT', received: 0, rejected: 0, elapsedMs: 6000 },
        }}
        refreshing={false}
        message={null}
        messageIsError={false}
        onRefresh={noop}
      />,
    )

    expect(screen.getAllByRole('listitem').map((li) => li.dataset.testid)).toEqual(['provider-ALPHA', 'provider-BETA', 'provider-GAMMA'])
    expect(within(screen.getByTestId('provider-ALPHA')).getByText('OK')).toBeInTheDocument()
    expect(within(screen.getByTestId('provider-ALPHA')).getByText(/249 received · 13 rejected · 2\.31 s/)).toBeInTheDocument()
    expect(within(screen.getByTestId('provider-BETA')).getByText('Timed out')).toBeInTheDocument()
    expect(within(screen.getByTestId('provider-GAMMA')).getByText('no data')).toBeInTheDocument()
    expect(screen.getByText('Partial results: 1 of 3 providers answered.')).toBeInTheDocument()
  })

  it('shows three "no data" rows and no summary for an empty map', () => {
    render(<ProviderPanel providers={{}} refreshing={false} message={null} messageIsError={false} onRefresh={noop} />)

    expect(screen.getAllByText('no data')).toHaveLength(3)
    expect(screen.queryByText(/partial results/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/no provider answered/i)).not.toBeInTheDocument()
  })

  it('says no provider answered when all reported providers failed', () => {
    render(
      <ProviderPanel
        providers={{
          ALPHA: { status: 'ERROR', received: 0, rejected: 0, elapsedMs: 100 },
          BETA: { status: 'CIRCUIT_OPEN', received: 0, rejected: 0, elapsedMs: 0 },
          GAMMA: { status: 'TIMEOUT', received: 0, rejected: 0, elapsedMs: 6000 },
        }}
        refreshing={false}
        message={null}
        messageIsError={false}
        onRefresh={noop}
      />,
    )

    expect(screen.getByText('No provider answered in the last aggregation; showing stored results.')).toBeInTheDocument()
    expect(screen.getByText('Failed')).toBeInTheDocument()
    expect(screen.getByText('Circuit open (skipped)')).toBeInTheDocument()
  })
})

describe('Refresh now', () => {
  it('posts the route, disables itself while pending and updates the panel', async () => {
    let release: (response: Response) => void = () => {}
    const api = stubFetch(({ method }) =>
      method === 'POST'
        ? new Promise<Response>((resolve) => {
            release = resolve
          })
        : json(page('FRESH', undefined, null, { ALPHA: { status: 'OK', received: 1, rejected: 0, elapsedMs: 100 } })),
    )
    renderApp('?origin=MAD&destination=JFK&date=2026-11-20&maxPrice=500')
    await screen.findByText('Up to date')

    await user().click(screen.getByRole('button', { name: 'Refresh now' }))

    expect(api.posts()[0]!.url.pathname).toBe('/api/admin/aggregations')
    expect(api.posts()[0]!.body).toEqual({ origin: 'MAD', destination: 'JFK', date: '2026-11-20' })
    expect(screen.getByRole('button', { name: 'Refreshing…' })).toBeDisabled()

    await act(async () => {
      release(
        json({
          persisted: 10,
          aggregatedAt: new Date().toISOString(),
          providers: {
            ALPHA: { status: 'OK', received: 5, rejected: 1, elapsedMs: 900 },
            BETA: { status: 'ERROR', received: 0, rejected: 0, elapsedMs: 50 },
          },
        }),
      )
    })

    expect(await screen.findByRole('button', { name: 'Refresh now' })).toBeEnabled()
    expect(within(screen.getByTestId('provider-BETA')).getByText('Failed')).toBeInTheDocument()
    expect(screen.getByText('Partial results: 1 of 3 providers answered.')).toBeInTheDocument()
    expect(screen.getByText('Showing 1 itineraries')).toBeInTheDocument()
  })

  it('treats 409 as busy and starts polling', async () => {
    const api = stubFetch(({ method, url }) => {
      if (method === 'POST') return json({ status: 409, detail: 'Aggregation in progress' }, 409, 'application/problem+json')
      return json(page(isPoll(url) ? 'REFRESHING' : 'FRESH'))
    })
    renderApp()
    await screen.findByText('Up to date')

    await user().click(screen.getByRole('button', { name: 'Refresh now' }))

    expect(await screen.findByText('A refresh is already in progress, or refreshing is temporarily unavailable.')).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(api.polls()).toBe(0)
    await advance(2000)
    expect(api.polls()).toBe(1)
    expect(await screen.findByText('Refreshing…')).toBeInTheDocument()
    expect(screen.getByText('Showing 1 itineraries')).toBeInTheDocument()
  })

  it('shows other failures inline and keeps the list', async () => {
    stubFetch(({ method }) =>
      method === 'POST' ? json({ status: 500, detail: 'Boom' }, 500, 'application/problem+json') : json(page('FRESH')),
    )
    renderApp()
    await screen.findByText('Up to date')

    await user().click(screen.getByRole('button', { name: 'Refresh now' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Refresh failed: Boom')
    expect(screen.getByText('Showing 1 itineraries')).toBeInTheDocument()
  })
})
