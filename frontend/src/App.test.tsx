import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import App from './App'
import type { Itinerary, ItineraryPage } from './api/types'

function itinerary(id: string, amount = 100, origin = 'MAD', destination = 'JFK'): Itinerary {
  return {
    id,
    origin,
    destination,
    segments: [
      { carrier: 'IB', flightNumber: `IB${id}`, from: origin, to: destination, departureAt: '2026-11-20T07:35:00Z', arrivalAt: '2026-11-20T16:10:00Z' },
    ],
    basePrice: { amount: amount - 5, currency: 'EUR' },
    finalPrice: { amount, currency: 'EUR' },
    source: 'ALPHA',
    connections: 0,
    totalDuration: 'PT8H35M',
  }
}

function page(items: Itinerary[], nextCursor: string | null = null): ItineraryPage {
  return { items, nextCursor, freshness: 'FRESH', aggregatedAt: '2026-11-14T10:00:00Z', providers: {} }
}

const json = (body: unknown, status = 200, type = 'application/json') =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': type } })

function stubFetch(handler: (url: URL) => Promise<Response> | Response) {
  const fn = vi.fn(async (input: RequestInfo | URL) => handler(new URL(String(input), 'http://localhost')))
  vi.stubGlobal('fetch', fn)
  return fn
}

function requestedUrls(fn: ReturnType<typeof stubFetch>): URL[] {
  return fn.mock.calls.map(([input]) => new URL(String(input), 'http://localhost'))
}

function renderApp() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, refetchOnWindowFocus: false } } })
  return render(
    <QueryClientProvider client={client}>
      <App />
    </QueryClientProvider>,
  )
}

async function fillAndSubmit(origin: string, destination: string, date = '2026-11-20') {
  const user = userEvent.setup()
  await user.type(screen.getByLabelText('Origin'), origin)
  await user.type(screen.getByLabelText('Destination'), destination)
  const dateInput = screen.getByLabelText('Date')
  await user.clear(dateInput)
  await user.type(dateInput, date)
  await user.click(screen.getByRole('button', { name: 'Search' }))
  return user
}

beforeEach(() => {
  window.history.replaceState(null, '', '/')
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('App', () => {
  it('shows a hint before any search', () => {
    stubFetch(() => json(page([])))
    renderApp()
    expect(screen.getByText(/enter a route and a date/i)).toBeInTheDocument()
  })

  it('submits a valid form, calls the API and updates the URL', async () => {
    const fetchMock = stubFetch(() => json(page([itinerary('a')])))
    renderApp()

    await fillAndSubmit('mad', 'jfk')

    expect(await screen.findByText('Showing 1 itineraries')).toBeInTheDocument()
    const url = requestedUrls(fetchMock)[0]!
    expect(url.pathname).toBe('/api/itineraries')
    expect(url.searchParams.toString()).toBe('origin=MAD&destination=JFK&date=2026-11-20&limit=20')
    expect(window.location.search).toBe('?origin=MAD&destination=JFK&date=2026-11-20')
  })

  it('searches on mount from a complete query string and fills the form', async () => {
    window.history.replaceState(null, '', '/?origin=MAD&destination=JFK&date=2026-11-20&maxPrice=500&carrier=IB')
    const fetchMock = stubFetch(() => json(page([itinerary('a')])))
    renderApp()

    expect(await screen.findByText('Showing 1 itineraries')).toBeInTheDocument()
    expect(screen.getByLabelText('Origin')).toHaveValue('MAD')
    expect(screen.getByLabelText('Destination')).toHaveValue('JFK')
    expect(screen.getByLabelText('Date')).toHaveValue('2026-11-20')
    expect(screen.getByLabelText('Max price (EUR)')).toHaveValue(500)
    expect(screen.getByLabelText('Carrier')).toHaveValue('IB')
    const url = requestedUrls(fetchMock)[0]!
    expect(url.searchParams.get('maxPrice')).toBe('500')
    expect(url.searchParams.get('carrier')).toBe('IB')
  })

  it('loads the next page with the cursor and appends without duplicates', async () => {
    window.history.replaceState(null, '', '/?origin=MAD&destination=JFK&date=2026-11-20')
    const fetchMock = stubFetch((url) =>
      url.searchParams.get('cursor') === 'c1'
        ? json(page([itinerary('c', 300), itinerary('d', 400)], null))
        : json(page([itinerary('a', 100), itinerary('b', 200)], 'c1')),
    )
    renderApp()

    expect(await screen.findByText('Showing 2 itineraries')).toBeInTheDocument()
    await userEvent.setup().click(screen.getByRole('button', { name: 'Load more' }))

    expect(await screen.findByText('Showing 4 itineraries')).toBeInTheDocument()
    expect(requestedUrls(fetchMock)[1]!.searchParams.get('cursor')).toBe('c1')
    const flights = screen.getAllByText(/^IB[a-d]$/).map((el) => el.textContent)
    expect(flights).toEqual(['IBa', 'IBb', 'IBc', 'IBd'])
    expect(screen.queryByRole('button', { name: 'Load more' })).not.toBeInTheDocument()
    expect(screen.getByText('End of results')).toBeInTheDocument()
  })

  it('keeps items and offers a retry when loading more fails', async () => {
    window.history.replaceState(null, '', '/?origin=MAD&destination=JFK&date=2026-11-20')
    let failNext = true
    stubFetch((url) => {
      if (!url.searchParams.get('cursor')) return json(page([itinerary('a')], 'c1'))
      if (failNext) {
        failNext = false
        return json({ status: 503, detail: 'Database unavailable' }, 503, 'application/problem+json')
      }
      return json(page([itinerary('b', 200)]))
    })
    renderApp()
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Load more' }))
    expect(await screen.findByText(/could not load more: database unavailable/i)).toBeInTheDocument()
    expect(screen.getByText('Showing 1 itineraries')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Retry' }))
    expect(await screen.findByText('Showing 2 itineraries')).toBeInTheDocument()
  })

  it('searches again when the same search is submitted twice', async () => {
    window.history.replaceState(null, '', '/?origin=MAD&destination=JFK&date=2026-11-20')
    const fetchMock = stubFetch(() => json(page([itinerary('a')])))
    renderApp()
    expect(await screen.findByText('Showing 1 itineraries')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledTimes(1)

    await userEvent.setup().click(screen.getByRole('button', { name: 'Search' }))

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2))
    expect(window.location.search).toBe('?origin=MAD&destination=JFK&date=2026-11-20')
  })

  it('rejects origin equal to destination without calling the API', async () => {
    const fetchMock = stubFetch(() => json(page([])))
    renderApp()

    await fillAndSubmit('MAD', 'MAD')

    const destination = screen.getByLabelText('Destination')
    expect(destination).toHaveAttribute('aria-invalid', 'true')
    expect(destination).toHaveAccessibleDescription('Destination must differ from origin')
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('shows a 400 field error at the matching field and unknown keys in a summary', async () => {
    stubFetch(() =>
      json(
        { status: 400, title: 'Bad Request', errors: { date: 'must be today or later', cursor: 'is invalid' } },
        400,
        'application/problem+json',
      ),
    )
    renderApp()

    await fillAndSubmit('MAD', 'JFK', '2020-01-01')

    await waitFor(() => expect(screen.getByLabelText('Date')).toHaveAccessibleDescription('must be today or later'))
    expect(screen.getByLabelText('Date')).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByText('cursor: is invalid')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Try again' })).not.toBeInTheDocument()
  })

  it('shows a generic error on 500 and refetches on "Try again"', async () => {
    window.history.replaceState(null, '', '/?origin=MAD&destination=JFK&date=2026-11-20')
    let calls = 0
    const fetchMock = stubFetch(() => (++calls === 1 ? new Response('boom', { status: 500 }) : json(page([itinerary('a')]))))
    renderApp()

    expect(await screen.findByText(/something went wrong/i)).toBeInTheDocument()
    await userEvent.setup().click(screen.getByRole('button', { name: 'Try again' }))

    expect(await screen.findByText('Showing 1 itineraries')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  it('shows the correlation id from problem details', async () => {
    window.history.replaceState(null, '', '/?origin=MAD&destination=JFK&date=2026-11-20')
    stubFetch(() => json({ status: 503, detail: 'Database unavailable', correlationId: 'abc-123' }, 503, 'application/problem+json'))
    renderApp()

    expect(await screen.findByText('Database unavailable')).toBeInTheDocument()
    expect(screen.getByText(/abc-123/)).toBeInTheDocument()
  })

  it('shows the empty state with the filters used', async () => {
    window.history.replaceState(null, '', '/?origin=MAD&destination=JFK&date=2026-11-20&maxPrice=1')
    stubFetch(() => json(page([])))
    renderApp()

    expect(await screen.findByText('No itineraries found')).toBeInTheDocument()
    expect(screen.getByText(/MAD → JFK, 2026-11-20, max €1/)).toBeInTheDocument()
  })

  it('renders only the newer search when an older slow response arrives late', async () => {
    window.history.replaceState(null, '', '/?origin=MAD&destination=JFK&date=2026-11-20')
    let releaseSlow: (response: Response) => void = () => {}
    stubFetch((url) =>
      url.searchParams.get('origin') === 'MAD'
        ? new Promise<Response>((resolve) => {
            releaseSlow = resolve
          })
        : json(page([itinerary('new', 100, 'BCN', 'LHR')])),
    )
    renderApp()
    expect(await screen.findByText('Searching providers…')).toBeInTheDocument()

    act(() => {
      window.history.pushState(null, '', '/?origin=BCN&destination=LHR&date=2026-11-20')
      window.dispatchEvent(new PopStateEvent('popstate'))
    })
    expect(await screen.findByText('BCN → LHR')).toBeInTheDocument()

    await act(async () => {
      releaseSlow(json(page([itinerary('old', 50)])))
    })

    expect(screen.queryByText('MAD → JFK')).not.toBeInTheDocument()
    expect(screen.getByText('Showing 1 itineraries')).toBeInTheDocument()
    expect(screen.getByText('IBnew')).toBeInTheDocument()
  })
})
