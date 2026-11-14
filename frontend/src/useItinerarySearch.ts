import { useInfiniteQuery } from '@tanstack/react-query'
import { searchItineraries, type ApiError } from './api/client'
import type { ItineraryPage } from './api/types'
import type { SearchState } from './searchState'

export const PAGE_SIZE = 20

export function useItinerarySearch(params: SearchState | null) {
  const query = useInfiniteQuery<ItineraryPage, ApiError>({
    queryKey: ['itineraries', params],
    queryFn: ({ pageParam, signal }) =>
      searchItineraries({ ...params!, cursor: pageParam as string | undefined, limit: PAGE_SIZE }, signal),
    initialPageParam: undefined,
    getNextPageParam: (last) => last.nextCursor ?? undefined,
    enabled: Boolean(params?.origin && params.destination && params.date),
  })

  const firstPage = query.data?.pages[0]
  return {
    queryKey: ['itineraries', params] as const,
    items: query.data?.pages.flatMap((page) => page.items) ?? [],
    pageCount: query.data?.pages.length ?? 0,
    freshness: firstPage?.freshness,
    aggregatedAt: firstPage?.aggregatedAt ?? null,
    providers: firstPage?.providers ?? {},
    fetchNextPage: query.fetchNextPage,
    hasNextPage: query.hasNextPage,
    isFetchingNextPage: query.isFetchingNextPage,
    isLoading: query.isLoading,
    isSuccess: query.isSuccess,
    isError: query.isError,
    isFetchNextPageError: query.isFetchNextPageError,
    error: query.error,
    refetch: query.refetch,
  }
}
