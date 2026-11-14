# AI Usage Log

Running record of AI-assisted work on this repository. One entry per significant interaction. Append as work happens; do not reconstruct at the end.

Tooling: OpenCode with Claude as the primary model. Project-level agents, skills and commands live in `.opencode/` directories at the repository root and inside each project. See `docs/SOLUTION.md` for how they fit together.

Entry format:

```
## YYYY-MM-DD — <short title>
**Intent:** what the human wanted
**Prompt (summary):** the gist of what was asked
**Generated:** what the AI produced
**Human review / corrections:** what was changed, rejected or decided by the human
**Artifacts:** files touched
```

---

## 2026-11-13 — Spring Boot version selection

**Intent:** Pick a Spring Boot version.
**Prompt (summary):** "Which Spring Boot version do you recommend?"
**Generated:** Checked the official support calendar via `api.spring.io`. Recommended 4.1.2 (3.5 OSS support expired June 2026; 4.0 expires December 2026; 4.2 not yet released). Noted Boot 4 changes: Jackson 3 (`tools.jackson.*`), modular starters, `RestClient`, JSpecify.
**Human review / corrections:** Accepted. `AGENTS.md` updated with version pin, Boot 4 starter names, `tools.jackson.*` and `org.jspecify.*` in forbidden imports, rule not to upgrade to 4.2 during delivery.
**Artifacts:** `flight-aggregator/AGENTS.md`.

## 2026-11-13 — Maven coordinates, dependencies, module structure

**Intent:** Decide groupId, dependency list, and whether to add a `service` module for controllers.
**Prompt (summary):** Several: "Which groupId?", "Which Spring Boot dependencies?", "Why Flyway?", "Does a `service` module make sense?", "Where do controllers go?"
**Generated:** `io.github.<user>` groupId rationale; per-module dependency lists verified against the Boot 4.1 starter table; Flyway justification (versioned schema, keyset index as reviewable artifact, Compose restarts, Testcontainers parity); recommendation to keep a single `infrastructure` module with package-level in/out separation enforced by ArchUnit rather than a `service` module; controller placement in `infrastructure.web` with rules (only inbound ports, translate-not-decide, own DTOs) and ArchUnit snippets.
**Human review / corrections:** Human chose option A (single infrastructure module). Human created the Maven skeleton by hand from Spring Initializr.
**Artifacts:** none (conversation only).

## 2026-11-13 — Maven skeleton fixed and verified

**Intent:** Add dependencies to the Initializr-generated skeleton and verify the build.
**Prompt (summary):** "Modify the pom.xml files with the suggested dependencies and verify everything works. Only poms and config files for now."
**Generated:** Parent pom cleaned (removed starter dependencies and Boot plugin from parent, added `dependencyManagement` for internal modules and third-party libraries with versions verified on Maven Central that day: Resilience4j 2.4.0, springdoc 3.2.0, WireMock 3.13.2, ArchUnit 1.5.1). Module artifactIds prefixed `flight-aggregator-*`. `infrastructure/application.yaml` with env-var placeholders. Root `src/` from Initializr removed.
**Human review / corrections:** AI initially used Testcontainers 1.x artifact names (`postgresql`, `junit-jupiter`); build failed; corrected to 2.x names (`testcontainers-postgresql`, `testcontainers-junit-jupiter`) and added `com.redis:testcontainers-redis` from the Boot BOM. Verified: `./mvnw test` green, `domain` compile classpath empty.
**Artifacts:** `flight-aggregator/pom.xml`, `flight-aggregator/{domain,application,infrastructure}/pom.xml`, `flight-aggregator/infrastructure/src/main/resources/application.yaml`, `flight-aggregator/AGENTS.md` (package root, artifactIds, Testcontainers note).

## 2026-11-14 — mock-providers skeleton and provider contracts

**Intent:** Create the mock providers project skeleton with documentation.
**Prompt (summary):** "Create the mock service skeleton, Spring MVC, Java 21, Maven. Skeleton only. Add AGENTS.md and whatever documentation you consider."
**Generated:** Single-module Boot 4.1.2 project (`webmvc`, `validation`, `actuator`, `jackson-dataformat-xml`), main class, context test, `application.yaml` with `mock.*` properties (seed, inventory size, overlap ratio, invalid ratio, chaos). `AGENTS.md` defining what the service is/is not, package layout, determinism, overlap/invalidity, chaos rules. `docs/provider-contracts.md` specifying Alpha (JSON, UTC), Beta (XML, local time + IANA zone, out-of-order legs, no leading zeros), Gamma (CSV streamed, `yyyyMMddHHmm`, cents, packed segments) with examples, error formats, cross-provider identity semantics and admin chaos endpoint.
**Human review / corrections:** Accepted. `./mvnw verify` green.
**Artifacts:** `mock-providers/**`, `AGENTS.md` (root, mock-providers row updated).

## 2026-11-14 — Repository published

**Intent:** Push everything to GitHub as `jerojasol`.
**Prompt (summary):** "Push the whole repo to `git@github.com:jerojasol/ota-flights.git`, always as `jerojasol@gmail.com`. `chore: initial commit` to `main`."
**Generated:** Local git identity set per-repo; remote configured through the existing `github.com-jerojasol` SSH alias after verifying authentication; root `.gitignore`; initial commit and push; follow-up `docs: add repository README`.
**Human review / corrections:** None needed; global git identity (a different account) was left untouched.
**Artifacts:** `.gitignore`, `README.md`, git history.

## 2026-11-14 — AI tooling scaffolding

**Intent:** Materialise the planned agents, skills and commands, placed by scope.
**Prompt (summary):** "Add the pending AI tools; keep `.opencode/` at the root and also inside each project, placing each tool where it belongs by function."
**Generated:** This log; `docs/SOLUTION.md` skeleton; root `/log-ai-decision` command; `flight-aggregator/.opencode/` with `domain-guardian` agent, `hexagonal-java` and `testing-conventions` skills, `/check-arch` and `/test-layer` commands; `mock-providers/.opencode/` with `contract-sync` skill.
**Human review / corrections:** pending.
**Artifacts:** `docs/ai-log.md`, `docs/SOLUTION.md`, `.opencode/**`, `flight-aggregator/.opencode/**`, `mock-providers/.opencode/**`.
