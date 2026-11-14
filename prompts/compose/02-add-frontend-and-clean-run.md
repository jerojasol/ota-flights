# F2 — Add the frontend to Compose and prove the stack from a clean clone

Run from the repository root. F1 (backend stack) and G1–G4 (frontend, including its Dockerfile and nginx.conf) are done.

This is the last engineering step before the write-up. Its purpose is to make `docker compose up --build` succeed for someone who has never seen this machine.

## Task

### 1. `frontend` service in `docker-compose.yml`

- build `./frontend`
- ports `"3000:80"`
- `depends_on: flight-aggregator: condition: service_healthy` (nginx resolves the upstream at startup)
- healthcheck: the Dockerfile's `/healthz` one; do not duplicate it
- `restart: unless-stopped`

Add the frontend's host port to `.env.example` as `FRONTEND_PORT` (default 3000) and make the three published ports configurable the same way (`AGGREGATOR_PORT`, `MOCK_PROVIDERS_PORT`), so a reviewer with a busy port 8080 can still boot the stack.

### 2. Extend `scripts/smoke.sh`

After the existing checks, with the frontend URL as a third optional argument (default `http://localhost:3000`):
- `GET /healthz` → 200.
- `GET /` → 200 and contains the root element of the app.
- `GET /api/itineraries?origin=MAD&destination=JFK&date=<date>&limit=1` **through the frontend** → 200 with `items`. This proves the nginx proxy, which no unit test covers.

### 3. Root `README.md`

Rewrite the "Running" section as a quick start that works by copy-paste:

- prerequisites (Docker with Compose v2; nothing else);
- `docker compose up --build`, and how long the first build takes on your machine (measure it);
- the three URLs: dashboard, API docs (`/swagger-ui.html` on the aggregator), mock providers;
- a first search to try (`MAD → JFK`, a date) and what to expect: the first search of a route takes a few seconds because providers are slow by design;
- a "See the resilience" block with the `PUT /admin/chaos` command and what to watch in the dashboard;
- how to run quietly (`MOCK_CHAOS_ENABLED=false`), how to reset (`docker compose down -v`), how to change ports;
- running the tests of each project (one command each; note that the aggregator's integration tests need Docker).

Remove every "(pending)" marker that is no longer true. Keep it to one screen and a half.

### 4. Clean-clone verification

This is the acceptance test of the whole repository. Do it in a temporary directory, not in the working tree:

```bash
tmp=$(mktemp -d) && git clone --branch "$(git branch --show-current)" . "$tmp/ota-flights" && cd "$tmp/ota-flights"
docker compose down -v 2>/dev/null; docker compose build --no-cache && docker compose up -d
docker compose ps
./scripts/smoke.sh
```

`git clone` of the local repository only contains committed files. If the working tree has uncommitted or untracked files the stack needs, the clone will fail to build: that is the point. **Do not commit to make it pass.** Stop, list exactly which files are missing from the clone, and ask me.

Things this run typically exposes — check each explicitly and report:
- a file the build needs that is git-ignored (root `.gitignore` ignores `prompts/` and `.env`; neither should be needed);
- `mvnw` or a script without the executable bit in git (`git ls-files -s mvnw scripts/*.sh` → mode `100755`);
- a Maven wrapper that downloads on every build because `.mvn/wrapper/maven-wrapper.properties` is missing from the image context;
- a `package-lock.json` out of sync with `package.json` (`npm ci` fails);
- a build that only worked because of a warm local cache.

Afterwards: `docker compose down -v` in the temporary clone and remove the directory, then bring the stack back up in the working tree.

## Acceptance

- All five services healthy in `docker compose ps` from the clean clone.
- `scripts/smoke.sh` all PASS, including the checks through the frontend.
- Open `http://localhost:3000`, search `MAD → JFK`, load a second page: report what you see (item counts, freshness, provider statuses).
- Total time of `docker compose build --no-cache` and of the subsequent `up` until all services are healthy.

Report real outputs. If something failed and you fixed it, say what failed first.

## Closing

Do not commit. Show `git status --short`, then `/log-ai-decision`.
