# mock-providers

Standalone Spring Boot service that simulates the three external GDS providers the `flight-aggregator` integrates with: **Alpha** (flat JSON, UTC), **Beta** (namespaced XML, local time + IANA zone) and **Gamma** (streamed CSV, compact UTC timestamps, prices in cents). Inventory is deterministic per `(seed, origin, destination, date)`. Some itineraries are sold by several providers at different prices, and some are deliberately invalid. Random latency and HTTP 500s are injected so the aggregator's resilience is exercised over real HTTP. The response formats are specified in [`docs/provider-contracts.md`](docs/provider-contracts.md), the contract the aggregator's adapters are written against.

## Try it

```bash
./mvnw spring-boot:run                     # :8081, chaos on
MOCK_CHAOS_ENABLED=false ./mvnw spring-boot:run

curl 'localhost:8081/alpha/flights?from=MAD&to=JFK&date=2026-11-20'
curl 'localhost:8081/beta/search?origin=MAD&destination=JFK&departureDate=2026-11-20'
curl -N 'localhost:8081/gamma/export?o=MAD&d=JFK&dt=20261120'
```

## Configuration

| Variable | Default | Meaning |
|---|---|---|
| `MOCK_CHAOS_ENABLED` | `true` | Turns latency and error injection on or off |
| `MOCK_LATENCY_MIN` | `100ms` | Lower bound of the uniform latency added to every provider request |
| `MOCK_LATENCY_MAX` | `5000ms` | Upper bound (at most `30s`) |
| `MOCK_ERROR_RATE` | `0.10` | Probability `[0, 1]` that a request fails with a provider-flavoured 500 |
| `MOCK_SEED` | `42` | Seed for the generated inventory |
| `MOCK_INVENTORY_SIZE` | `500` | Itineraries generated per query, before splitting across providers |
| `MOCK_OVERLAP_RATIO` | `0.30` | Fraction sold by two or three providers |
| `MOCK_INVALID_RATIO` | `0.05` | Fraction deliberately invalid |

Chaos can also be changed at runtime: `curl -X PUT localhost:8081/admin/chaos -H 'Content-Type: application/json' -d '{"errorRate":0.5}'` (see the contract's "Admin" section). `/actuator/health` and `/admin/**` are never affected by chaos.

## Docker

```bash
docker build -t mock-providers .
docker run --rm -p 8081:8081 -e MOCK_CHAOS_ENABLED=false mock-providers
```

## Scripts

- `scripts/verify-contract.sh`: checks a running instance against the contract (status, `Content-Type`, structure, 400 cases) and prints `PASS`/`FAIL` per check. Start the service with chaos disabled first; set `MOCK_URL` if it is not on `http://localhost:8081`.
- `scripts/export-fixtures.sh`: starts its own instances of the packaged jar (chaos off, fixed seed) and writes `two-itineraries.*` and `empty.*` fixtures for each provider into `../flight-aggregator/infrastructure/src/test/resources/fixtures/`, printing what changed. This is the only sanctioned way for mock output to reach the aggregator: as fixture files, never as a code dependency.
