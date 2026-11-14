import type { ProviderOutcomes, ProviderStatus } from '../api/types'

const PROVIDERS = ['ALPHA', 'BETA', 'GAMMA'] as const

const STATUS_LABEL: Record<ProviderStatus, string> = {
  OK: 'OK',
  TIMEOUT: 'Timed out',
  ERROR: 'Failed',
  CIRCUIT_OPEN: 'Circuit open (skipped)',
}

const STATUS_MARKER: Record<ProviderStatus, string> = {
  OK: '●',
  TIMEOUT: '◐',
  ERROR: '✕',
  CIRCUIT_OPEN: '⊘',
}

function seconds(ms: number): string {
  return `${(ms / 1000).toFixed(2)} s`
}

interface Props {
  providers: ProviderOutcomes
  refreshing: boolean
  message: string | null
  messageIsError: boolean
  onRefresh: () => void
}

export function ProviderPanel({ providers, refreshing, message, messageIsError, onRefresh }: Props) {
  const reported = PROVIDERS.filter((name) => providers[name])
  const answered = PROVIDERS.filter((name) => providers[name]?.status === 'OK').length

  let summary: string | null = null
  if (answered > 0 && answered < PROVIDERS.length) {
    summary = `Partial results: ${answered} of ${PROVIDERS.length} providers answered.`
  } else if (answered === 0 && reported.length > 0) {
    summary = 'No provider answered in the last aggregation; showing stored results.'
  }

  return (
    <section className="panel" aria-label="Providers">
      <h2>Providers</h2>
      {summary && <p className="panel-summary">{summary}</p>}
      <ul className="providers">
        {PROVIDERS.map((name) => {
          const outcome = providers[name]
          return (
            <li key={name} data-testid={`provider-${name}`} className={`provider provider-${outcome?.status ?? 'none'}`}>
              <div className="provider-head">
                <strong>{name}</strong>
                <span className="provider-status">
                  {outcome ? (
                    <>
                      <span aria-hidden="true">{STATUS_MARKER[outcome.status]}</span> {STATUS_LABEL[outcome.status]}
                    </>
                  ) : (
                    'no data'
                  )}
                </span>
              </div>
              {outcome && (
                <div className="muted small">
                  {outcome.received} received · {outcome.rejected} rejected · {seconds(outcome.elapsedMs)}
                </div>
              )}
            </li>
          )
        })}
      </ul>
      <button type="button" onClick={onRefresh} disabled={refreshing}>
        {refreshing ? 'Refreshing…' : 'Refresh now'}
      </button>
      <p className="muted small">Demo control: forces a new aggregation of this route.</p>
      {message && (
        <p className={messageIsError ? 'field-error' : 'muted small'} role={messageIsError ? 'alert' : 'status'}>
          {message}
        </p>
      )}
    </section>
  )
}
