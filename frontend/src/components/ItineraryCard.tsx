import type { Itinerary } from '../api/types'
import { formatDuration, formatPrice, formatUtc } from '../format'

function connectionsLabel(connections: number): string {
  if (connections === 0) return 'Non-stop'
  return connections === 1 ? '1 connection' : `${connections} connections`
}

export function ItineraryCard({ itinerary }: { itinerary: Itinerary }) {
  return (
    <article className="card">
      <header className="card-header">
        <div>
          <div className="route">
            {itinerary.origin} → {itinerary.destination}
          </div>
          <div className="muted">
            {formatDuration(itinerary.totalDuration)} · {connectionsLabel(itinerary.connections)}
          </div>
        </div>
        <div className="price">
          <div className="final-price">{formatPrice(itinerary.finalPrice)}</div>
          <div className="muted small">base {formatPrice(itinerary.basePrice)}</div>
        </div>
      </header>
      <ul className="segments">
        {itinerary.segments.map((segment) => (
          <li key={`${segment.flightNumber}-${segment.departureAt}`}>
            <strong>{segment.flightNumber}</strong> {segment.from} → {segment.to} · {formatUtc(segment.departureAt)} –{' '}
            {formatUtc(segment.arrivalAt)}
          </li>
        ))}
      </ul>
      <span className="tag">{itinerary.source}</span>
    </article>
  )
}
