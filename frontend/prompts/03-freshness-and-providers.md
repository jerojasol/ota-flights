# G3 — Freshness, provider status and live refresh

## Where you are

The session runs inside `frontend/`, which is one project of a monorepo. Paths in this prompt are relative to `frontend/` unless they start with `../`. Things that live one level up, in the repository root:

- `../AGENTS.md` — repository rules (service names, ports, deliverables). Read it.
- `../README.md` — repository README.
- `../docs/ai-log.md` — the AI usage log that `/log-ai-decision` appends to.
- `../docker-compose.yml` — the stack. Run every `docker compose ...` command with `--project-directory ..` or from `..`; never copy the compose file into `frontend/`.
- `../flight-aggregator/` and `../mock-providers/` — the backend projects. Read them when a prompt says so; do not edit them from here.

Node is pinned by `.tool-versions` (asdf) in this directory: `nodejs 24.22.3`, the Node 24 LTS line. Do not change it and do not add an `.nvmrc`.

Read `AGENTS.md` (this project's). G2 is done: search, results and "load more" work.

This step is what makes the dashboard "real-time" and what makes the backend's resilience visible. It is the part of the frontend a reviewer will look at most closely.

## Background: what the backend reports

Every first-page response carries:
- `freshness`:
  - `FRESH` — aggregated recently; nothing to do.
  - `STALE` — served from the database; a background refresh was started if nobody else was running one. Also what the backend reports while its cache (Redis) is down, in which case **no** refresh is running.
  - `REFRESHING` — another request is aggregating right now.
  - `MISSING` — nothing was ever aggregated and this request could not aggregate either. Rare.
- `aggregatedAt` — when the data was last aggregated, or `null`.
- `providers` — outcome of the last aggregation per provider: `status` (`OK`, `TIMEOUT`, `ERROR`, `CIRCUIT_OPEN`), `received`, `rejected`, `elapsedMs`. May be `{}`.

A first-page request without a cursor is what triggers a refresh on the backend, so polling the first page is both the status check and the trigger. Requests with a cursor never trigger anything.

## Task

### 1. `FreshnessBadge`

A small badge next to the results count:
- `FRESH` → "Up to date", with "aggregated 2 min ago" from `aggregatedAt` (relative time, recomputed every 30 s; absolute UTC time in the `title`).
- `STALE` → "Refreshing in background" while polling is active; "May be out of date" once polling has stopped.
- `REFRESHING` → "Refreshing…".
- `MISSING` → "No data yet".
Colour is never the only signal: each state has its own text.

### 2. `ProviderPanel`

Always shows three rows, `ALPHA`, `BETA`, `GAMMA`, in that order, even when the key is absent from `providers` (then: "no data"). Per row: status as text plus a marker, offers received, offers rejected, elapsed time (`2.31 s`).

Status wording: `OK` → "OK"; `TIMEOUT` → "Timed out"; `ERROR` → "Failed"; `CIRCUIT_OPEN` → "Circuit open (skipped)".

When at least one provider is not `OK` and at least one is, show one line above the list: "Partial results: N of 3 providers answered." When none is `OK`: "No provider answered in the last aggregation; showing stored results." These two sentences are the user-facing form of the brief's resilience requirement; keep the wording.

### 3. Live refresh — `useFreshnessPolling`

While the first page's `freshness` is `STALE` or `REFRESHING`:
- every 2 s, call `searchItineraries({ ...params, limit: 1 })` (no cursor) as a separate query (`["freshness", params]`), not through the infinite query;
- stop when it returns `FRESH`, when the search parameters change, when the tab is hidden (`document.visibilityState`; resume when visible), or after 15 attempts.

After 15 attempts without `FRESH`, stop and switch the badge to "May be out of date". This cap matters: when the backend's cache is down it answers `STALE` forever, and the page must not poll forever.

When polling sees `FRESH`:
- if the user has loaded only the first page → refetch the results query silently; the list updates in place;
- if the user has loaded more pages → do **not** replace the list under them. Show a bar: "Updated results are available — Reload", which resets the infinite query to the first page. Replacing several pages mid-scroll would move the content the user is reading.

In both cases the badge and the provider panel update immediately from the poll's response.

### 4. Manual refresh

A "Refresh now" button in the provider panel calls `triggerAggregation({ origin, destination, date })` (`POST /api/admin/aggregations`), shows "Refreshing…" while it runs (it can take several seconds), then updates the provider panel from the response and applies the same first-page / more-pages rule as above. Disabled while running. On failure, show the error inline; the list stays.

A 409 is not a failure: it means the backend could not take the aggregation lock, either because another aggregation of this route is running or because its cache is unavailable. Show "A refresh is already in progress, or refreshing is temporarily unavailable." and start the same polling as for `REFRESHING`. Add a test for it.

Label it as a demo control ("Forces a new aggregation of this route").

### 5. Layout

Results on the left, a narrow status column (badge details + provider panel) on the right; stacked on narrow screens with the status block above the results.

## Tests (fake timers, `fetch` stubbed)

- `STALE` first page → a poll request goes out after 2 s; when it returns `FRESH`, polling stops and the results refetch once.
- `FRESH` first page → no poll request is ever made.
- 15 polls all returning `STALE` → exactly 15 poll requests, then none; badge reads "May be out of date".
- Changing the search while polling → no further polls for the old parameters.
- Two pages loaded, poll returns `FRESH` → the list is unchanged and the "Reload" bar appears; clicking it shows page 1 only.
- Provider panel: `{ ALPHA: OK, BETA: TIMEOUT }` renders three rows, Gamma as "no data", and the sentence "Partial results: 1 of 3 providers answered." A provider absent from the map is not counted as answered.
- `providers: {}` renders three "no data" rows and neither summary sentence.
- "Refresh now" posts the right body, disables itself while pending, and updates the panel from the response.

## Acceptance

```bash
npm run lint && npm test && npm run build
```

Then against the running stack, and report what you observed for each:
1. Search a route never searched before (pick an unusual pair, e.g. `LIS → BOS`): the first response takes seconds and arrives `FRESH` with provider outcomes.
2. Raise the mock's error rate — `curl -X PUT localhost:8081/admin/chaos -H 'Content-Type: application/json' -d '{"errorRate":0.7}'` — click "Refresh now" a few times: the panel shows failed providers and the partial-results sentence, and the result list is still there.
3. `docker compose --project-directory .. stop redis`, search again: results still render, badge ends at "May be out of date" after the poll cap, no console errors. Then `docker compose --project-directory .. start redis`.
4. Reset the chaos: `-d '{"errorRate":0.1}'`.

Note for the write-up: the mock's chaos setting is global, so a single provider cannot be taken down on demand; which provider fails in step 2 is random.

## Closing

Do not commit. Show `git status --short` (it also lists changes one level up), then `/log-ai-decision`, which writes to `../docs/ai-log.md`.
