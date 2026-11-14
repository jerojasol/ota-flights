# D4 — Redis adapter for FreshnessPort

Load the `hexagonal-java` skill. Read `docs/domain-model.md` §8 (`FreshnessPort`) and the read-path flow in `prompts/phase-b-application/02-search-itineraries-service.md`. Everything in `infrastructure/.../cache/`.

## Task

### `RedisFreshnessAdapter implements FreshnessPort`
Uses `StringRedisTemplate`. Key scheme (document it in a class comment):

```
fresh:{ORIGIN}:{DEST}:{yyyy-MM-dd}      value: JSON of AggregationResult summary; TTL = freshnessTtl
lock:{ORIGIN}:{DEST}:{yyyy-MM-dd}       value: owner token; TTL = lockTtl; SET NX
```

- `status(criteria)`:
  - `fresh:*` exists → `FRESH`
  - else `lock:*` exists → `REFRESHING`
  - else repository has rows for the route/date? The port does not know the repository; so model it as: no `fresh` and no `lock` → `MISSING`. The application layer decides STALE vs MISSING by asking the repository whether anything exists; adjust `SearchItinerariesService` and its tests accordingly, or add a cheap `seen:{...}` key with a long TTL set on first `markFresh` so `status` can return `STALE` when `seen` exists but `fresh` does not. Choose the `seen` key approach (keeps the application simple) and record the decision.
- `tryLock(criteria, ttl)` → `SET lock:* <uuid> NX PX ttl`; on success returns `Optional.of(new LockToken(criteria, uuid))`, otherwise empty.
- `release(token)` → **compare-and-delete**, atomic, via a Lua script executed with `DefaultRedisScript<Long>`:
  ```lua
  if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end
  ```
  A plain `DEL` is not acceptable: if the lock expired and another caller re-acquired it, `DEL` would remove the other caller's lock.
- `markFresh(criteria, result, ttl)` → `SET fresh:* <json> PX ttl` and `SET seen:* 1 PX 7d`.
- `lastResult(criteria)` → read `fresh:*`, deserialise.

Serialisation: a private `FreshnessRecordJson` record mapped with Jackson 3 **inside the adapter**; domain types receive no annotations.

### `CacheConfiguration`
`@Configuration` exposing the adapter as `FreshnessPort`, with `aggregation.freshness-ttl` and `aggregation.lock-ttl` bound via `@ConfigurationProperties("aggregation")`.

### Fallback behaviour
If Redis is unreachable, `status` returns `MISSING`, `tryLock` returns a token (so the caller proceeds; there is no lock to protect), `markFresh`/`release` swallow and log. The search must keep working without Redis (degraded: every search re-aggregates). Document this in the class and in `SOLUTION.md` limitations.

## Tests

`RedisFreshnessAdapterIT` with `com.redis:testcontainers-redis` and `@ServiceConnection`:
- MISSING → markFresh → FRESH; after TTL expiry (use a 1 s TTL and Awaitility) → STALE (because `seen` exists).
- tryLock twice → token, then empty; after `release(token)` → token again.
- lock expires after `lockTtl`.
- **owner check**: A gets T1; let it expire (1 s TTL + Awaitility); B gets T2; `release(T1)` returns without deleting; `tryLock` still fails for a third caller; `release(T2)` frees it.
- lastResult round-trips the outcomes map.
- with the container stopped (or wrong port) → status MISSING and tryLock returns a token, no exception.

Unit test for the key builder (format, uppercase, date formatting).

## Acceptance

- `./mvnw -q -pl infrastructure verify -Dit.test=RedisFreshnessAdapterIT` green.
- `SearchItinerariesService` tests still green after the STALE/seen decision (update the fake if needed).

## Closing

`/check-arch`, `/log-ai-decision`, show diff summary.
