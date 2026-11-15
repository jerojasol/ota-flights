import { useCallback, useEffect, useEffectEvent, useState, useSyncExternalStore } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { searchItineraries } from './api/client'
import type { Freshness, ItineraryPage } from './api/types'
import type { SearchState } from './searchState'

export const POLL_INTERVAL_MS = 2000
export const MAX_POLLS = 15

type Phase = 'auto' | 'polling' | 'fresh' | 'exhausted'

function subscribeVisibility(callback: () => void) {
  document.addEventListener('visibilitychange', callback)
  return () => document.removeEventListener('visibilitychange', callback)
}

function useTabVisible(): boolean {
  return useSyncExternalStore(subscribeVisibility, () => document.visibilityState !== 'hidden')
}

export function needsRefresh(freshness: Freshness | undefined): boolean {
  return freshness === 'STALE' || freshness === 'REFRESHING'
}

/**
 * Polls the first page (limit 1, no cursor) while the data is STALE or REFRESHING. Mount it with a `key` per search so
 * a new search starts from a clean state and the old loop is torn down.
 */
export function useFreshnessPolling(
  params: SearchState,
  initialFreshness: Freshness | undefined,
  onFresh: (page: ItineraryPage) => void,
) {
  const queryClient = useQueryClient()
  const visible = useTabVisible()
  const [phase, setPhase] = useState<Phase>('auto')
  const [attempts, setAttempts] = useState(0)
  const [latest, setLatest] = useState<ItineraryPage | null>(null)

  const handleFresh = useEffectEvent((page: ItineraryPage) => onFresh(page))

  const active = phase === 'polling' || (phase === 'auto' && needsRefresh(initialFreshness))

  useEffect(() => {
    if (!active || !visible || attempts >= MAX_POLLS) return
    let cancelled = false
    const queryKey = ['freshness', params]
    const timer = setTimeout(async () => {
      let page: ItineraryPage | null = null
      try {
        page = await queryClient.fetchQuery({
          queryKey,
          queryFn: ({ signal }) => searchItineraries({ ...params, limit: 1 }, signal),
          staleTime: 0,
          retry: false,
        })
      } catch {
        // A failed poll counts as an attempt; the cap still applies.
      }
      if (cancelled) return
      const next = attempts + 1
      setAttempts(next)
      if (page) setLatest(page)
      if (page?.freshness === 'FRESH') {
        setPhase('fresh')
        handleFresh(page)
      } else {
        setPhase(next >= MAX_POLLS ? 'exhausted' : 'polling')
      }
    }, POLL_INTERVAL_MS)
    return () => {
      cancelled = true
      clearTimeout(timer)
      void queryClient.cancelQueries({ queryKey })
    }
  }, [active, visible, attempts, params, queryClient])

  const restart = useCallback(() => {
    setAttempts(0)
    setPhase('polling')
  }, [])

  const markFresh = useCallback((page: ItineraryPage) => {
    setLatest(page)
    setPhase('fresh')
  }, [])

  return { polling: active, exhausted: phase === 'exhausted', latest, restart, markFresh }
}
