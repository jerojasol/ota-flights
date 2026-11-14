# C6 — Dockerfile, contract verification, fixture export

Read `../AGENTS.md` (compose contract) and `docs/provider-contracts.md`. Load the `contract-sync` skill.

## Task

### 1. `Dockerfile` (multi-stage)
```
FROM eclipse-temurin:21-jdk AS build   -> COPY . ; ./mvnw -q -DskipTests package
FROM eclipse-temurin:21-jre            -> COPY --from=build target/*.jar app.jar ; non-root ; EXPOSE 8081
HEALTHCHECK CMD curl -f http://localhost:8081/actuator/health || exit 1
ENTRYPOINT ["java","-jar","/app.jar"]
```
Add a `.dockerignore` (`target/`, `.idea/`, `*.iml`, `prompts/`, `docs/`).

### 2. Contract verification script — `scripts/verify-contract.sh`
Starts nothing; assumes the service is running on `MOCK_URL` (default `http://localhost:8081`) with chaos disabled. For each provider:
- Calls the documented endpoint with `MAD → JFK → 2026-11-20`.
- Checks `Content-Type`, HTTP 200, and 3–4 structural assertions with `jq` / `xmllint --xpath` / `awk` matching the contract (field names, timestamp formats, `#END`, namespace).
- Calls the documented 400 case and checks status + body prefix.
Prints `PASS`/`FAIL` per check and exits non-zero on any failure.

### 3. Fixture export — `scripts/export-fixtures.sh`
With chaos disabled and `MOCK_INVENTORY_SIZE=2`, fetches each provider's response for `MAD→JFK→2026-11-20` and writes:
```
../flight-aggregator/infrastructure/src/test/resources/fixtures/alpha/two-itineraries.json
../flight-aggregator/infrastructure/src/test/resources/fixtures/beta/two-itineraries.xml
../flight-aggregator/infrastructure/src/test/resources/fixtures/gamma/two-itineraries.csv
```
plus the `empty.*` variants (use an airport pair the generator returns nothing for, or `MOCK_INVENTORY_SIZE=0`). Overwrites existing files. Prints a diff summary against the previous version if any.

This is the only sanctioned way for mock output to reach the aggregator: as test fixtures, by file, never by code dependency. State this in the script header.

### 4. README for the project
`mock-providers/README.md`: one paragraph, the three curl examples, chaos env vars table, `docker build` command, link to the contract.

## Tests

None beyond a `DockerfileIT`? No — keep it manual. Document in the README how to run the two scripts.

## Acceptance

- `docker build -t mock-providers .` succeeds.
- `docker run --rm -p 8081:8081 -e MOCK_CHAOS_ENABLED=false mock-providers` then `scripts/verify-contract.sh` → all PASS.
- `scripts/export-fixtures.sh` produces files that, when the aggregator's mapper tests exist, pass them.

## Closing

`/log-ai-decision`, show diff summary. This closes Phase C.
