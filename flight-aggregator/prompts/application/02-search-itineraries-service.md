# B2 — SearchItinerariesService (FRESH / STALE / REFRESHING)

Load the `hexagonal-java` and `testing-conventions` skills. Read `docs/domain-model.md` §8–§9 and the read-path flow summarised below. Work only in `application/`.

## Agreed read-path flow

```
search(criteria, filter, cursor, limit):
  if cursor present            → repository.findBy only; freshness = status (informational); never aggregate
  status = freshness.status(criteria)
  FRESH                        → repository.findBy
  MISSING (cold miss)          → if freshness.tryLock: aggregate synchronously, markFresh, release; then findBy
                                  else (someone else is aggregating): findBy, report REFRESHING
  STALE                        → findBy immediately; if tryLock: schedule aggregation on refreshExecutor
                                  (markFresh + release in a finally); report STALE
  REFRESHING                   → findBy, report REFRESHING
```

## Task

Implement `SearchItinerariesService implements SearchItinerariesUseCase`.

Constructor dependencies:
- `ItineraryRepositoryPort repository`
- `FreshnessPort freshness`
- `AggregateFlightsUseCase aggregator`
- `Executor refreshExecutor` (background refresh)
- `Duration freshnessTtl`, `Duration lockTtl`

Rules:
- The lock must always be released, including when aggregation throws. Aggregation exceptions on the synchronous path are caught, the lock released, and the search still returns whatever the repository has, with freshness `MISSING` or `STALE` as appropriate. The service never fails a search because providers failed.
- `SearchResult.lastOutcomes` comes from `freshness.lastResult(criteria)` when present, otherwise from the aggregation just performed, otherwise empty.
- `limit` is clamped to `[1, 100]`.

## Tests

Fakes: `InMemoryFreshness` (controllable status, lock behaviour, records calls), `RecordingAggregator` (counts invocations, can throw, returns a configurable `AggregationResult`), `InMemoryItineraryRepository`, a `CapturingExecutor` that stores runnables so the test decides when the background refresh runs.

Scenarios:
- FRESH → repository only; aggregator never called.
- MISSING + lock acquired → aggregator called once **before** `findBy`; `markFresh` and `release` called; result `FRESH`.
- MISSING + lock not acquired → aggregator not called; result `REFRESHING`.
- STALE + lock acquired → `findBy` first; a refresh task is scheduled; aggregator not yet called until the executor runs it; after running, `markFresh` and `release` called.
- STALE + lock not acquired → no task scheduled; result `STALE`.
- cursor present with status MISSING → aggregator never called.
- aggregator throws on the synchronous path → lock released, search returns, no exception.
- background refresh throws → lock released (use the capturing executor to run it and assert).
- `limit` 0 → 1; 500 → 100.

## Acceptance

- `./mvnw -q -pl application test` green.
- `domain-guardian` clean of CRITICAL/MAJOR on `application/`.

## Closing

`/check-arch`, `/log-ai-decision`, show diff summary. This closes Phase B.
