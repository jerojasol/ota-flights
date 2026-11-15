import { useCallback, useState } from 'react'
import { useMutation, useQueryClient, type InfiniteData } from '@tanstack/react-query'
import { triggerAggregation, type ApiError } from '../api/client'
import type { AggregationResult, ItineraryPage } from '../api/types'
import type { SearchState } from '../searchState'
import { useFreshnessPolling } from '../useFreshnessPolling'
import type { useItinerarySearch } from '../useItinerarySearch'
import { formatUtc } from '../format'
import { FreshnessBadge } from './FreshnessBadge'
import { ProviderPanel } from './ProviderPanel'
import { ResultsList } from './ResultsList'

const BUSY_MESSAGE = 'A refresh is already in progress, or refreshing is temporarily unavailable.'
const NO_PROVIDER_MESSAGE = 'No provider answered; showing stored results.'

function describeFilters(state: SearchState): string {
  const parts = [`${state.origin} → ${state.destination}`, state.date]
  if (state.maxPrice !== undefined) parts.push(`max €${state.maxPrice}`)
  if (state.carrier) parts.push(`carrier ${state.carrier}`)
  return parts.join(', ')
}

interface Props {
  params: SearchState
  result: ReturnType<typeof useItinerarySearch>
}

/** Mounted with `key` = serialised search, so polling and refresh state never leak between searches. */
export function SearchResults({ params, result }: Props) {
  const queryClient = useQueryClient()
  const [updateAvailable, setUpdateAvailable] = useState(false)
  const [message, setMessage] = useState<{ text: string; error: boolean } | null>(null)
  const { pageCount, refetch, queryKey } = result

  const applyFresh = useCallback(() => {
    if (pageCount <= 1) void refetch()
    else setUpdateAvailable(true)
  }, [pageCount, refetch])

  const polling = useFreshnessPolling(params, result.freshness, applyFresh)

  const refresh = useMutation<AggregationResult, ApiError>({
    mutationFn: () => triggerAggregation({ origin: params.origin, destination: params.destination, date: params.date }),
    onMutate: () => setMessage(null),
    onSuccess: (outcome) => {
      // Nothing was stored, and the search response keeps describing the last aggregation that produced data.
      if (!Object.values(outcome.providers).some((provider) => provider?.status === 'OK')) {
        setMessage({ text: NO_PROVIDER_MESSAGE, error: false })
        return
      }
      polling.markFresh({ items: [], nextCursor: null, freshness: 'FRESH', ...outcome })
      applyFresh()
    },
    onError: (error) => {
      if (error.status === 409) {
        setMessage({ text: BUSY_MESSAGE, error: false })
        polling.restart()
      } else {
        setMessage({ text: `Refresh failed: ${error.problem?.detail ?? error.message}`, error: true })
      }
    },
  })

  function reload() {
    queryClient.setQueryData<InfiniteData<ItineraryPage>>(queryKey, (data) =>
      data && { pages: data.pages.slice(0, 1), pageParams: data.pageParams.slice(0, 1) },
    )
    setUpdateAvailable(false)
    void refetch()
  }

  const status = polling.latest ?? {
    freshness: result.freshness ?? 'MISSING',
    aggregatedAt: result.aggregatedAt,
    providers: result.providers,
  }

  const badge = <FreshnessBadge freshness={status.freshness} aggregatedAt={status.aggregatedAt} polling={polling.polling} />

  return (
    <div className="layout">
      <aside className="status-column">
        <div className="panel">
          <h2>Data</h2>
          <p className="small">
            Last aggregated: {status.aggregatedAt ? formatUtc(status.aggregatedAt) : 'never'}
          </p>
          {polling.polling && <p className="muted small">Checking for fresh data every 2 s…</p>}
          {polling.exhausted && <p className="muted small">Stopped checking after 15 attempts.</p>}
        </div>
        <ProviderPanel
          providers={status.providers}
          refreshing={refresh.isPending}
          message={message?.text ?? null}
          messageIsError={message?.error ?? false}
          onRefresh={() => refresh.mutate()}
        />
      </aside>
      <div className="results-column">
        {updateAvailable && (
          <div className="update-bar" role="status">
            Updated results are available —{' '}
            <button type="button" className="link" onClick={reload}>
              Reload
            </button>
          </div>
        )}
        {result.items.length === 0 ? (
          <div className="empty">
            <p>No itineraries found {badge}</p>
            <p className="muted small">Filters: {describeFilters(params)}</p>
          </div>
        ) : (
          <ResultsList
            items={result.items}
            badge={badge}
            hasNextPage={result.hasNextPage}
            isFetchingNextPage={result.isFetchingNextPage}
            nextPageError={result.isFetchNextPageError ? result.error : null}
            onLoadMore={() => void result.fetchNextPage()}
          />
        )}
      </div>
    </div>
  )
}
