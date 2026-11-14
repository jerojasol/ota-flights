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

## 2026-11-14 — A1: value objects Airport, Carrier, FlightNumber, Price

**Intent:** Implement the first domain value objects with their invariants (A1, C1, F1, P1–P4) and unit tests.
**Prompt (summary):** `prompts/phase-a-domain/01-value-objects.md`: records with compact-constructor validation and `of(...)` factories, `DomainException` plus one exception per VO, a test `Fixtures` class, leading-zero normalisation in `FlightNumber`, HALF_EVEN scale-2 `Price` with `plus`, `isLowerThan`, `multiply`; then `/check-arch` and this log.
**Generated:** `Airport`, `Carrier`, `FlightNumber(Carrier, int)`, `Price(BigDecimal, Currency)`, abstract `DomainException` and four `Invalid*Exception` types, all in `domain.model`; `Fixtures` and four test classes (94 tests, invariant IDs in trailing comments). AI decisions: exceptions in `domain.model`; flight number stored as `int` in 1..9999 so `IB117` equals `IB0117`; cross-currency operations throw `InvalidPriceException`; negative amounts rejected before rounding.
**Human review / corrections:** AI first wrote a test expecting `FlightNumber.of("6251")` to be rejected; it is valid under C1 (carrier `62`, number `51`), so the test case was wrong and was replaced by a test pinning that parse. `domain-guardian` reported 1 CRITICAL and 4 MINOR. CRITICAL: the 1..9999 range and leading-zero rule were stricter than F1 as documented; human chose to amend F1 in `domain-model.md` rather than relax the code. MINOR fixed: factories uppercased before validating, so non-ASCII input such as `ß` became a valid code (`SS`); they now check ASCII first. MINOR left open: the new exception types and the P4 exception are not named in `domain-model.md`; `plus`/`multiply` naming and whether `plus` is needed. Also found: `/check-arch` ArchUnit step fails without `-am`, and no `ArchitectureTest` exists yet, so import rules were checked by hand.
**Artifacts:** `flight-aggregator/domain/src/main/java/io/github/jerojasol/flights/domain/model/**`, `flight-aggregator/domain/src/test/java/io/github/jerojasol/flights/domain/**`, `flight-aggregator/docs/domain-model.md` (F1).

## 2026-11-14 — A2: Segment, Provider, Offer, ItineraryKey

**Intent:** Implement the Segment (S1–S3), Provider, Offer and ItineraryKey domain types with tests.
**Prompt (summary):** `prompts/domain/02-segment-offer-key.md`: Segment record with `duration()`, Provider enum with documented dedup order, Offer with defensive copy, ItineraryKey as SHA-256 of the §4 canonical form with `fromStored`, tests per invariant, then `/check-arch` and this log.
**Generated:** `Segment`, `Provider`, `Offer`, `ItineraryKey`, `InvalidSegmentException`, `InvalidOfferException`, `InvalidItineraryKeyException`, `Fixtures` additions (`MIA`, `IB6251`, `IB4120`, `segment(...)`), `SegmentTest`, `OfferTest`, `ItineraryKeyTest`. AI decisions: `ItineraryKey` is a final class with a private constructor (a public record cannot hide its canonical constructor, and the prompt forbids a public raw-string one); `canonical(List<Segment>)` is a public static method because the key stores only the hash, so the prompt's instance `canonical()` was not possible.
**Human review / corrections:** AI wrote one test with a five-digit flight number (`IB06251`) that F1 correctly rejects; removed. `domain-guardian` found 0 CRITICAL, 1 MAJOR (mis-tagged `// F1` on a key test, downgraded to cosmetic), 2 MINOR: `ItineraryKey` accepted any non-blank string, now validated as 64 lowercase hex chars (covers `fromStored`); null-field test missed the airports, added. Exception types now listed in `domain-model.md` §3, which also closes the A1 finding about undocumented exceptions. Open for next task: Segment can never be invalid once built, so the DEAD_LEG_* / SEGMENT_DURATION_EXCEEDED rejection reasons must originate in the adapters (catching `InvalidSegmentException`) or in a different validator input; decide before `OfferValidator` (A4). `/check-arch`: no `ArchitectureTest` exists yet, imports checked by hand (`java.*` only).
**Artifacts:** `flight-aggregator/domain/src/main/java/io/github/jerojasol/flights/domain/model/**`, `flight-aggregator/domain/src/test/java/io/github/jerojasol/flights/domain/**`, `flight-aggregator/docs/domain-model.md`.

## 2026-11-14 — A3: Itinerary aggregate, MarkupPolicy, ItineraryFactory

**Intent:** Implement the aggregate root with invariants I1–I12, the markup policy and the factory service.
**Prompt (summary):** `prompts/domain/03-itinerary-aggregate.md`: `MarkupPolicy`, `PercentageMarkupPolicy`, `Itinerary` (private constructor, `from`, `rehydrate`, connection constants, equality on key), `RejectionReason`, `InvalidItineraryException`, `ItineraryFactory`, boundary tests, then `/check-arch`, log, diff summary.
**Generated:** `Itinerary`, `ItineraryFactory`, `MarkupPolicy`, `PercentageMarkupPolicy`, `RejectionReason`, `InvalidItineraryException`, `Fixtures.chain/offer` builders, `ItineraryTest`, `ItineraryFactoryTest`, `PercentageMarkupPolicyTest`. AI decisions: everything in `domain.model` (consistent with A1/A2); 3-arg `from` with explicit `ingestedAt` plus `Clock` in the factory, because `Instant.now()` in the domain is not testable; `InvalidItineraryException.reason()` is an `Optional`, empty for I11 and null-argument errors since §6 has no reason for them; I3 holds by derivation.
**Human review / corrections:** `domain-guardian` found 0 CRITICAL, 2 MAJOR, 5 MINOR. MAJOR: `rehydrate` accepts an external key and final price (I12/I10). The guardian suggested checking the key against the segments; the prompt explicitly says to trust stored key and prices, so the behaviour was kept and the exception is now documented in `domain-model.md` ("Rehydration"); this is a decision worth revisiting when the persistence adapter is written. MAJOR: `ItineraryFactory` signature, `Clock` and constants were missing from the document; updated. MINOR fixed: I11 tests now assert the empty reason, null-argument and null-segments tests added, I1 comment explains why `rehydrate` is used, test names given a `_when_` part, exception Javadoc. MINOR left: the 2-arg `from` and 1-arg `ItineraryFactory` constructor use the system clock; kept because the prompt asked for the 2-arg form, remove once wiring uses an injected `Clock`. `/check-arch`: still no `ArchitectureTest`; imports checked by hand.
**Artifacts:** `flight-aggregator/domain/src/main/java/io/github/jerojasol/flights/domain/model/**`, `flight-aggregator/domain/src/test/java/io/github/jerojasol/flights/domain/**`, `flight-aggregator/docs/domain-model.md`, `flight-aggregator/prompts/domain/03-itinerary-aggregate.md`.
