# G4 — Frontend container: multi-stage build and nginx

## Where you are

The session runs inside `frontend/`, which is one project of a monorepo. Paths in this prompt are relative to `frontend/` unless they start with `../`. Things that live one level up, in the repository root:

- `../AGENTS.md` — repository rules (service names, ports, deliverables). Read it.
- `../README.md` — repository README.
- `../docs/ai-log.md` — the AI usage log that `/log-ai-decision` appends to.
- `../docker-compose.yml` — the stack. Run every `docker compose ...` command with `--project-directory ..` or from `..`; never copy the compose file into `frontend/`.
- `../flight-aggregator/` and `../mock-providers/` — the backend projects. Read them when a prompt says so; do not edit them from here.

Node is pinned by `.tool-versions` (asdf) in this directory: `nodejs 24.22.3`, the Node 24 LTS line. Do not change it and do not add an `.nvmrc`.

G1–G3 are done and `npm run build` produces `dist/`.

## Task

### 1. `Dockerfile`

```
stage build:  node:24-alpine   (same major as .tool-versions)
              COPY package.json package-lock.json → npm ci
              COPY the rest → npm run build
stage run:    nginx:stable-alpine
              COPY nginx.conf → /etc/nginx/conf.d/default.conf
              COPY --from=build dist → /usr/share/nginx/html
              EXPOSE 80
              HEALTHCHECK: wget -q --spider http://localhost/healthz
```
Copy the lockfile and run `npm ci` before copying sources, so dependency layers are cached. Tests do not run in the image build.

### 2. `nginx.conf`

One `server` block on port 80:

- `location = /healthz` → `return 200 "ok"`, `access_log off`. The healthcheck must not depend on the API being up.
- `location /api/` → `proxy_pass http://flight-aggregator:8080;` (no trailing path, so `/api/...` is forwarded unchanged), with `proxy_set_header Host`, `X-Forwarded-For`, `X-Forwarded-Proto`, and `proxy_read_timeout 60s`. The first search of a route blocks while the backend queries three slow providers, and a manual aggregation does the same; the default timeout is enough today but leaves no margin.
- nginx resolves `flight-aggregator` once at startup and exits if it cannot. Compose starts the frontend only after the aggregator is healthy (step F2), so that is acceptable; say so in a comment rather than adding a resolver.
- `location /assets/` → `expires 1y; add_header Cache-Control "public, immutable"` (Vite fingerprints these files).
- `location /` → `try_files $uri /index.html;` with `Cache-Control: no-cache` on `index.html`, so a new build is picked up.
- `gzip on` for text, CSS, JS, JSON.

The upstream host is fixed to the compose service name on purpose: `../AGENTS.md` makes service names the contract between projects.

### 3. `.dockerignore`

`node_modules`, `dist`, `.git`, `coverage`, `*.log`, `prompts`.

## Acceptance

```bash
docker build -t ota-frontend .
docker run --rm ota-frontend nginx -t        # will fail to resolve the upstream outside compose: report it and move on
```
The real check is in F2, inside the compose network. Report the image size and the build time of a second build with no changes (it should be all cache hits).

## Closing

Do not commit. Show `git status --short` (it also lists changes one level up), then `/log-ai-decision`, which writes to `../docs/ai-log.md`. The next step, F2, adds this service to `../docker-compose.yml`; it is run from the repository root, not from here.
