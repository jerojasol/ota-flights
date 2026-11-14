import { useEffect, useState } from 'react'
import { SearchForm, type FieldErrors, type FieldName } from './components/SearchForm'
import { SearchResults } from './components/SearchResults'
import { parseSearch, serializeSearch, type SearchState } from './searchState'
import { useItinerarySearch } from './useItinerarySearch'

const FIELDS: readonly string[] = ['origin', 'destination', 'date', 'maxPrice', 'carrier']

export default function App() {
  const [search, setSearch] = useState<SearchState | null>(() => parseSearch(window.location.search))
  const [formKey, setFormKey] = useState(0)
  const result = useItinerarySearch(search)

  useEffect(() => {
    function onPopState() {
      setSearch(parseSearch(window.location.search))
      setFormKey((k) => k + 1)
    }
    window.addEventListener('popstate', onPopState)
    return () => window.removeEventListener('popstate', onPopState)
  }, [])

  function handleSubmit(next: SearchState) {
    const url = serializeSearch(next)
    if (url !== window.location.search) window.history.pushState(null, '', url)
    setSearch(next)
  }

  const error = result.isError && !result.isFetchNextPageError ? result.error : null
  const validation = error?.status === 400 ? (error.problem?.errors ?? {}) : {}
  const serverErrors: FieldErrors = {}
  const summaryErrors: string[] = []
  for (const [key, message] of Object.entries(validation)) {
    if (FIELDS.includes(key)) serverErrors[key as FieldName] = message
    else summaryErrors.push(`${key}: ${message}`)
  }
  if (error?.status === 400 && Object.keys(validation).length === 0) {
    summaryErrors.push(error.problem?.detail ?? 'The search was rejected')
  }

  return (
    <main className="app">
      <h1>Flight search</h1>
      <SearchForm
        key={formKey}
        initial={search}
        serverErrors={serverErrors}
        summaryErrors={summaryErrors}
        disabled={result.isLoading}
        onSubmit={handleSubmit}
      />

      {!search && <p className="hint">Enter a route and a date to search for flights.</p>}

      {result.isLoading && (
        <div className="status" role="status">
          <p>Searching providers…</p>
          <p className="muted small">The first search of a route can take a few seconds.</p>
        </div>
      )}

      {error && error.status !== 400 && (
        <div className="error-box" role="alert">
          <p>{error.problem?.detail ?? 'Something went wrong while searching. Please try again.'}</p>
          {error.problem?.correlationId && <p className="muted small">Reference: {error.problem.correlationId}</p>}
          <button type="button" onClick={() => void result.refetch()}>
            Try again
          </button>
        </div>
      )}

      {search && result.pageCount > 0 && (
        <SearchResults key={serializeSearch(search)} params={search} result={result} />
      )}
    </main>
  )
}
