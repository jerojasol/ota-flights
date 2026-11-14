# G2 — Search form, results list and keyset "load more"

## Where you are

The session runs inside `frontend/`, which is one project of a monorepo. Paths in this prompt are relative to `frontend/` unless they start with `../`. Things that live one level up, in the repository root:

- `../AGENTS.md` — repository rules (service names, ports, deliverables). Read it.
- `../README.md` — repository README.
- `../docs/ai-log.md` — the AI usage log that `/log-ai-decision` appends to.
- `../docker-compose.yml` — the stack. Run every `docker compose ...` command with `--project-directory ..` or from `..`; never copy the compose file into `frontend/`.
- `../flight-aggregator/` and `../mock-providers/` — the backend projects. Read them when a prompt says so; do not edit them from here.

Node is pinned by `.tool-versions` (asdf) in this directory: `nodejs 24.22.3`, the Node 24 LTS line. Do not change it and do not add an `.nvmrc`.

Read `AGENTS.md` (this project's). The scaffold, types, client and formatters from G1 exist. The backend stack is running.

## Task

One page: a search form on top, results below.

### 1. Search state lives in the URL

`src/searchState.ts`: parse and serialise `{ origin, destination, date, maxPrice?, carrier? }` to and from `window.location.search` with `URLSearchParams`. Submitting the form does `history.pushState`; a `popstate` listener keeps back/forward working. A page loaded with a complete query string searches immediately, so a search can be shared as a link. No router library.

### 2. `SearchForm`

Fields:
- Origin, Destination: text, 3 letters, uppercased as typed, required.
- Date: `<input type="date">`, required, defaults to tomorrow.
- Max price: number, optional, positive, labelled with the currency (`EUR`; the backend filters in its reference currency).
- Carrier: text, 2 characters, optional, uppercased.
- Submit button; disabled while a first-page request is in flight.

Client-side validation mirrors the backend's rules only to avoid pointless round trips; the backend stays the authority. Origin equal to destination is rejected client-side with an inline message.

Real `<label>`s, `aria-invalid` and `aria-describedby` on fields with errors. Pressing Enter in any field submits.

### 3. Data — `useItinerarySearch(params)`

`useInfiniteQuery`:
- `queryKey: ["itineraries", params]`
- `queryFn: ({ pageParam, signal }) => searchItineraries({ ...params, cursor: pageParam, limit: 20 }, signal)`
- `initialPageParam: undefined`, `getNextPageParam: (last) => last.nextCursor ?? undefined`
- `enabled` only when origin, destination and date are present.

Expose: the flattened `items`, the **first** page's `freshness`, `aggregatedAt` and `providers` (later pages go straight to the database and their metadata adds nothing), `fetchNextPage`, `hasNextPage`, and the loading/error flags.

### 4. `ResultsList` and `ItineraryCard`

Each card shows:
- final price, prominent; base price smaller ("base €488.00");
- route `MAD → JFK`, total duration, "Non-stop" or "1 connection" / "2 connections";
- one line per segment: flight number, `from → to`, departure and arrival in UTC;
- the provider that supplied the price (`source`) as a small tag.

Key the list by `id`. Results arrive ordered by price from the API; do not re-sort client-side.

Below the list: a "Load more" button when `hasNextPage`, disabled and labelled "Loading…" while the next page loads; "End of results" otherwise. A count line above the list: "Showing N itineraries" — never a total, the API does not provide one and that is a deliberate property of keyset pagination.

### 5. States

Every one of these must be visibly distinct:
- **Idle**: no search yet → a one-line hint.
- **First load**: a skeleton or a text "Searching providers…" with a note that the first search of a route can take a few seconds. This is a real case: a route never searched before makes the backend query three slow providers.
- **Empty**: 200 with no items → "No itineraries found", and the filters used.
- **Validation error (400)**: show `problem.errors` next to the matching form fields; unknown keys go in a summary above the form.
- **Other errors**: `problem.detail` or a generic message, the `correlationId` when present, and a "Try again" button that refetches.
- **Load-more error**: keep the items already shown; show the error next to the button with a retry.

Changing the search while one is in flight must not let the older response overwrite the newer one. React Query's key change plus `signal` covers this; add a test that proves it.

## Tests (Vitest + Testing Library, `fetch` stubbed)

- Submitting a valid form calls the API with the expected query string and updates the URL.
- A URL with a complete query string triggers a search on mount and fills the form.
- Two pages: the first render shows page 1; clicking "Load more" requests `cursor=<nextCursor of page 1>` and appends without duplicates; with `nextCursor: null` the button is replaced by "End of results".
- Origin equal to destination shows an inline error and makes no request.
- A 400 with `errors: { date: "..." }` shows the message at the date field.
- A 500 shows the generic error and "Try again" refetches.
- An empty page shows the empty state.
- A slow first response followed by a new search: only the second search's results are rendered.

## Acceptance

```bash
npm run lint && npm test && npm run build
```
Then, with `npm run dev`, search `MAD → JFK` for `2026-11-20` and report: number of items on the first page, that "Load more" appends 20 more with no repeated `id` (check in the React Query devtools or by logging once and removing the log), and what a search with `maxPrice=1` shows.

## Closing

Do not commit. Show `git status --short` (it also lists changes one level up), then `/log-ai-decision`, which writes to `../docs/ai-log.md`.
