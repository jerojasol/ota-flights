# G1 — Frontend scaffold, AGENTS.md and typed API client

## Where you are

The session runs inside `frontend/`, which is one project of a monorepo. Paths in this prompt are relative to `frontend/` unless they start with `../`. Things that live one level up, in the repository root:

- `../AGENTS.md` — repository rules (service names, ports, deliverables). Read it.
- `../README.md` — repository README.
- `../docs/ai-log.md` — the AI usage log that `/log-ai-decision` appends to.
- `../docker-compose.yml` — the stack. Run every `docker compose ...` command with `--project-directory ..` or from `..`; never copy the compose file into `frontend/`.
- `../flight-aggregator/` and `../mock-providers/` — the backend projects. Read them when a prompt says so; do not edit them from here.

Node is pinned by `.tool-versions` (asdf) in this directory: `nodejs 24.22.3`, the Node 24 LTS line. Do not change it and do not add an `.nvmrc`.

The directory already exists and contains only `.tool-versions` and `prompts/`; scaffold into it without removing them. Precondition: the backend stack is running (`docker compose --project-directory .. up -d`), so the API is reachable at `http://localhost:8080`.

The frontend is the lowest-weight part of the deliverable. It must be small, correct and boring. No UI framework, no state library, no router. Three screens' worth of features on one page.

## Task

### 1. Scaffold

Scaffold this directory with Vite + React + TypeScript (strict). `npm create vite` refuses a non-empty directory: generate into a temporary directory and move the files here, or write them by hand. Dependencies, and nothing else without asking:
- runtime: `react`, `react-dom`, `@tanstack/react-query`
- dev: `vite`, `typescript`, `@vitejs/plugin-react`, `vitest`, `@testing-library/react`, `@testing-library/user-event`, `@testing-library/jest-dom`, `jsdom`, `eslint` with the Vite template's default config

Use the current stable versions from npm (check with `npm view <pkg> version`; do not pin versions from memory). Node is the version in `.tool-versions` (24.22.3); set `package.json` `engines.node` to `>=24 <25`. Check `node -v` prints v24 before installing anything; if it does not, stop and say so. Keep `package-lock.json` (it must be committed with the project).

Scripts: `dev`, `build` (`tsc -b && vite build`), `preview`, `test` (`vitest run`), `lint`.

### 2. API access without CORS

The backend has no CORS configuration and must not get one for this. The browser always calls same-origin `/api/...`:
- in development, `vite.config.ts` proxies `/api` to `process.env.VITE_API_TARGET ?? "http://localhost:8080"`;
- in the container (step G4), nginx proxies `/api` to `flight-aggregator:8080`.

So the client code uses relative URLs only. No base URL constant, no environment variable read at runtime.

### 3. Types — `src/api/types.ts`

Mirror the backend DTOs exactly. Read them in `../flight-aggregator/infrastructure/src/main/java/io/github/jerojasol/flights/infrastructure/web/dto/` and confirm against a live response; as of writing:

```ts
type Freshness = "FRESH" | "STALE" | "REFRESHING" | "MISSING";
type ProviderStatus = "OK" | "TIMEOUT" | "ERROR" | "CIRCUIT_OPEN";

interface Price { amount: number; currency: string }
interface Segment { carrier: string; flightNumber: string; from: string; to: string; departureAt: string; arrivalAt: string }
interface Itinerary {
  id: string; origin: string; destination: string; segments: Segment[];
  basePrice: Price; finalPrice: Price; source: "ALPHA" | "BETA" | "GAMMA";
  connections: number; totalDuration: string;          // ISO-8601, e.g. "PT8H35M"
}
interface ProviderOutcome { status: ProviderStatus; received: number; rejected: number; elapsedMs: number }
interface ItineraryPage {
  items: Itinerary[]; nextCursor: string | null; freshness: Freshness;
  aggregatedAt: string | null; providers: Record<string, ProviderOutcome>;
}
interface AggregationResult { persisted: number; aggregatedAt: string; providers: Record<string, ProviderOutcome> }
interface ProblemDetail { status: number; title?: string; detail?: string; errors?: Record<string, string>; correlationId?: string }
```

`providers` may be an empty object (nothing aggregated yet, or Redis down). Never assume the three keys exist.

### 4. Client — `src/api/client.ts`

```ts
interface SearchParams { origin: string; destination: string; date: string; maxPrice?: number; carrier?: string; cursor?: string; limit?: number }

searchItineraries(params: SearchParams, signal?: AbortSignal): Promise<ItineraryPage>
triggerAggregation(body: { origin: string; destination: string; date: string }): Promise<AggregationResult>
class ApiError extends Error { status: number; problem: ProblemDetail | null }
```

- Build the query string with `URLSearchParams`; omit undefined and empty values.
- Non-2xx: parse the body as Problem Details when the content type is `application/problem+json`, throw `ApiError`. A network failure or a non-JSON error body still produces an `ApiError` with `problem: null`, never an unhandled rejection.
- Pass `signal` through so React Query can cancel a superseded search. A cold search blocks for several seconds while the backend aggregates; that is expected, so set no client-side timeout shorter than 30 s.

### 5. Formatting helpers — `src/format.ts`

- `formatDuration("PT8H35M") → "8h 35m"`; handles days (`P1DT2H` → `26h 0m`) and minutes-only.
- `formatPrice({amount: 512.3, currency: "EUR"}) → "€512.30"` via `Intl.NumberFormat`.
- `formatUtc("2026-11-20T07:35:00Z") → "20 Nov 07:35 UTC"`. Times are shown in UTC and labelled as such: the API returns instants and the frontend has no airport time-zone data. Do not convert to the browser's zone.

### 6. App shell

`src/main.tsx` with a `QueryClientProvider` (`retry: false` for queries — the backend already retries providers, and retrying a 400 is pointless; `refetchOnWindowFocus: false`). `src/App.tsx` renders a heading and a placeholder. Plain CSS in `src/styles.css` with a handful of custom properties; system font stack; works at 360 px wide.

### 7. `AGENTS.md` (this project's, i.e. `frontend/AGENTS.md`)

Short (under 60 lines): what the project is; the dependency allow-list above and "ask before adding one"; the same-origin `/api` rule and why; types mirror the backend DTOs and are updated in the same change as the DTOs; times in UTC; tests with Vitest + Testing Library, `fetch` stubbed with `vi.stubGlobal`, no network in tests; commands. English.

Update the `frontend/` rows in `../AGENTS.md` and `../README.md` so they no longer say pending. Those two files are one level up; change only those rows.

## Tests

- `client.test.ts`: query string omits undefined values and includes the cursor; a 200 resolves to the parsed page; a 400 with Problem Details rejects with `ApiError` carrying `problem.errors`; a rejected `fetch` rejects with `ApiError` and `problem === null`.
- `format.test.ts`: the cases listed in section 5.

## Acceptance

```bash
node -v && npm ci && npm run lint && npm test && npm run build
npm run dev   # then: curl -s 'localhost:5173/api/itineraries?origin=MAD&destination=JFK&date=2026-11-20&limit=1' | jq .freshness
```
The last command must return data through the Vite proxy. Report the outputs.

## Closing

Do not commit. Show `git status --short` (it also lists changes one level up), then `/log-ai-decision`, which writes to `../docs/ai-log.md`.
