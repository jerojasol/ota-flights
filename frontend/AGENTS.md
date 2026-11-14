# AGENTS.md — frontend

Search dashboard for the flight aggregator: one page, React + Vite + TypeScript (strict). It should stay small, correct and boring. It has no UI framework, no state library and no router.

## Dependencies (allow-list)

- Runtime: `react`, `react-dom`, `@tanstack/react-query`.
- Dev: `vite`, `@vitejs/plugin-react`, `typescript`, `vitest`, `jsdom`, `@testing-library/{react,dom,user-event,jest-dom}`, and ESLint with the classic Vite config (`eslint`, `@eslint/js`, `typescript-eslint`, `eslint-plugin-react-hooks`, `eslint-plugin-react-refresh`, `globals`), plus `@types/*`.
- `typescript` is pinned to `~6.0` because `typescript-eslint` does not support TypeScript 7 yet.
- **Ask before adding any other dependency.** Commit `package-lock.json`.
- Node comes from `.tool-versions` (24.x). Do not add `.nvmrc`.

## API access: same-origin `/api` only

The backend has no CORS configuration and must not get one. The browser always calls relative `/api/...` URLs:
- dev: `vite.config.ts` proxies `/api` to `VITE_API_TARGET` (default `http://localhost:8080`);
- container: nginx proxies `/api` to `flight-aggregator:8080`.

So there is no base URL constant and no environment variable is read at runtime.

## Types

`src/api/types.ts` mirrors the DTOs in `../flight-aggregator/infrastructure/src/main/java/.../infrastructure/web/dto/`. If a DTO changes, update the types in the same change. `providers` may be empty, so never assume `ALPHA`/`BETA`/`GAMMA` are present.

## Client

`src/api/client.ts` throws `ApiError` (`status`, `problem: ProblemDetail | null`) for every failure. Problem Details are parsed only when the response is `application/problem+json`. Aborts are rethrown so React Query can cancel searches. Cold searches can take several seconds, so do not add a client timeout under 30 s. Queries use `retry: false`.

## Times

Show times in UTC and label them `UTC` (`formatUtc`). The API returns instants and the frontend has no airport time-zone data, so never convert to the browser's zone.

## Tests

Vitest + Testing Library on jsdom. Stub `fetch` with `vi.stubGlobal` and restore it with `vi.unstubAllGlobals()`. Tests never touch the network. Tests live next to the code (`*.test.ts[x]`).

## Commands

```bash
npm ci
npm run dev        # http://localhost:5173, needs the backend on :8080
npm run lint
npm test
npm run build      # tsc -b && vite build
npm run preview
```
