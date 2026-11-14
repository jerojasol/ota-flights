import { useEffect, useState } from 'react'
import type { Freshness } from '../api/types'
import { formatUtc } from '../format'

const TICK_MS = 30_000

function useNow(): number {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), TICK_MS)
    return () => clearInterval(id)
  }, [])
  return now
}

function relativeAge(instant: string, now: number): string {
  const minutes = Math.floor((now - new Date(instant).getTime()) / 60_000)
  if (minutes < 1) return 'just now'
  if (minutes < 60) return `${minutes} min ago`
  return `${Math.floor(minutes / 60)} h ago`
}

interface Props {
  freshness: Freshness
  aggregatedAt: string | null
  polling: boolean
}

export function FreshnessBadge({ freshness, aggregatedAt, polling }: Props) {
  const now = useNow()

  let label: string
  let tone: string
  if (freshness === 'FRESH') {
    label = 'Up to date'
    tone = 'ok'
  } else if (freshness === 'MISSING') {
    label = 'No data yet'
    tone = 'bad'
  } else if (!polling) {
    label = 'May be out of date'
    tone = 'warn'
  } else {
    label = freshness === 'REFRESHING' ? 'Refreshing…' : 'Refreshing in background'
    tone = 'busy'
  }

  return (
    <span className={`badge badge-${tone}`} title={aggregatedAt ? `Aggregated ${formatUtc(aggregatedAt)}` : undefined}>
      {label}
      {freshness === 'FRESH' && aggregatedAt && <span className="badge-age"> · aggregated {relativeAge(aggregatedAt, now)}</span>}
    </span>
  )
}
