import type { ReactNode } from 'react'
import type { ApiError } from '../api/client'
import type { Itinerary } from '../api/types'
import { ItineraryCard } from './ItineraryCard'

interface Props {
  items: Itinerary[]
  badge?: ReactNode
  hasNextPage: boolean
  isFetchingNextPage: boolean
  nextPageError: ApiError | null
  onLoadMore: () => void
}

export function ResultsList({ items, badge, hasNextPage, isFetchingNextPage, nextPageError, onLoadMore }: Props) {
  return (
    <section aria-label="Results">
      <p className="count">
        Showing {items.length} itineraries {badge}
      </p>
      <ul className="results">
        {items.map((itinerary) => (
          <li key={itinerary.id}>
            <ItineraryCard itinerary={itinerary} />
          </li>
        ))}
      </ul>
      <div className="more">
        {hasNextPage ? (
          <>
            <button type="button" onClick={onLoadMore} disabled={isFetchingNextPage}>
              {isFetchingNextPage ? 'Loading…' : nextPageError ? 'Retry' : 'Load more'}
            </button>
            {nextPageError && !isFetchingNextPage && (
              <span className="field-error" role="alert">
                Could not load more: {nextPageError.problem?.detail ?? nextPageError.message}
              </span>
            )}
          </>
        ) : (
          <p className="muted">End of results</p>
        )}
      </div>
    </section>
  )
}
