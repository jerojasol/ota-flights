# C5 — Chaos filter and admin endpoint

Read `AGENTS.md` §5 ("Chaos") and `docs/provider-contracts.md` → "Chaos" and "Admin". Work in `src/main/java/io/github/jerojasol/mockproviders/chaos/`.

## Task

### `ChaosState`
Thread-safe mutable holder (`AtomicReference<ChaosConfig>`) initialised from `MockProperties.chaos`:
```java
record ChaosConfig(boolean enabled, Duration latencyMin, Duration latencyMax, double errorRate)
```
Validate `latencyMin ≤ latencyMax`, `errorRate ∈ [0,1]`.

### `ChaosFilter` (`OncePerRequestFilter`, registered via `FilterRegistrationBean` for `/alpha/*`, `/beta/*`, `/gamma/*` only)
Per request, when enabled:
1. Sleep uniform `[latencyMin, latencyMax]` (use a `Random` **separate** from the generator's; seed from `mock.seed + 1` so runs are reproducible, or unseeded — decide, record).
2. With probability `errorRate`, short-circuit with HTTP 500 and the provider-flavoured body: pick the writer by path prefix (`AlphaErrorWriter`, `BetaFaultWriter`, `GammaErrorWriter`) and set the matching `Content-Type`.
3. Otherwise continue the chain.
4. Always log one line: `provider=alpha latency_ms=2310 outcome=OK|ERROR`.

`/actuator/**` and `/admin/**` are never filtered (registration URL patterns guarantee it; add a test).

Virtual threads are enabled, so `Thread.sleep` is cheap here; still, cap `latencyMax` at 30 s to avoid foot-guns.

### `ChaosAdminController`
- `GET /admin/chaos` → current `ChaosConfig`.
- `PUT /admin/chaos` with a JSON body of any subset of fields → merges into the current config, validates, returns the effective config. Durations accepted as ISO (`PT2S`) or simple (`2000ms`, `2s`); use Spring's `DurationStyle.detectAndParse`.

## Tests

`ChaosFilterTest` (`@SpringBootTest` + MockMvc, or `@WebMvcTest` importing the filter config):
- `errorRate=1.0`, `latency 0–0` → every `/alpha/flights` call is 500 with `application/json` body `error=INTERNAL`; `/beta/search` 500 with XML `Fault`; `/gamma/export` 500 `text/plain` starting `ERR 500`.
- `errorRate=0.0` → 200s.
- `latency 200ms–200ms` → request takes ≥ 200 ms.
- `/actuator/health` with `errorRate=1.0` → 200.
- `PUT /admin/chaos {"errorRate":0.5}` → `GET` reflects `0.5` and other fields unchanged; `{"errorRate":2}` → 400.

## Acceptance

- `./mvnw -q verify` green.
- With defaults, `for i in $(seq 1 20); do curl -s -o /dev/null -w '%{http_code} %{time_total}\n' 'localhost:8081/alpha/flights?from=MAD&to=JFK&date=2026-11-20'; done` shows a mix of 200/500 and latencies between 0.1 and 5 s.

## Closing

`/log-ai-decision`, show diff summary.
