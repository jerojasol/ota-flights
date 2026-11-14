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

## 2026-11-14 — A4: OfferValidator, ItineraryDeduplicator

**Intent:** Implement total offer validation and O(n) deduplication.
**Prompt (summary):** `prompts/domain/04-validator-deduplicator.md`: `OfferValidation` sealed interface, `OfferValidator` (total, first failing reason, `validateAll`), `ItineraryDeduplicator` (hash grouping, cheapest wins, provider-order tie-break, deterministic order), tests incl. a 10,000-offer sanity test; decide what to do with the segment-level rejection reasons and record it.
**Generated:** `OfferValidation` (sealed, nested `Valid`/`Rejected`), `ValidationSummary`, `OfferValidator`, `ItineraryDeduplicator`, `OfferValidatorTest`, `ItineraryDeduplicatorTest`; `Itinerary.findViolation` as the single source of structural rules (`Itinerary.from` and the validator both use it); `InvalidSegmentException` now carries its `RejectionReason`.
**Decision — segment-level reasons:** kept DEAD_LEG_SAME_AIRPORT, DEAD_LEG_NON_POSITIVE_DURATION and SEGMENT_DURATION_EXCEEDED in the enum (they are in §6) rather than dropping them, and did NOT add a dead branch to the validator, because an `Offer` cannot hold an invalid `Segment`. Instead `InvalidSegmentException.reason()` lets an adapter that fails to build a segment count the offer under the documented reason. Consequence: per-provider rejection counts come from two sources (validator and adapter catch blocks); an infrastructure test must cover the adapter side.
**Human review / corrections:** AI reordered the checks in `Itinerary` to match the prompt (I4 before I5; A3 had I4 after the pair checks). `domain-guardian` found 0 CRITICAL, 0 MAJOR, 7 MINOR. Fixed: §6 wrongly said the validator raises NO_SEGMENTS; §7 said it applies S1–S3; §5 "O(n)" reworded to grouping O(n) plus O(k log k) sort of survivors; same-provider same-price offers were input-order dependent, now tie-broken on earlier final arrival with a test; mixed-currency behaviour pinned by a test (P4); I7/I8 and I8/I9 ordering tests added; `InvalidSegmentException` rejects a null reason. Left open: the sanity test asserts a 1 s wall clock (kept as the prompt specified; may flake on a loaded CI, drop the timing assertion if it does); the adapter-level mixed-currency rejection promised by §10 does not exist yet. `/check-arch`: still no `ArchitectureTest`; imports checked by hand.
**Artifacts:** `flight-aggregator/domain/src/main/java/io/github/jerojasol/flights/domain/model/**`, `flight-aggregator/domain/src/test/java/io/github/jerojasol/flights/domain/model/**`, `flight-aggregator/docs/domain-model.md`, `flight-aggregator/prompts/domain/04-validator-deduplicator.md`.

## 2026-11-14 — A5: ports and supporting types (closes Phase A)

**Intent:** Complete the domain's public surface (query/result types, inbound and outbound ports) so the application layer can be written against it.
**Prompt (summary):** `prompts/domain/05-ports-and-support-types.md`: `SearchCriteria`, `ItineraryFilter`, `PageCursor`, `ItineraryPage`, `SearchResult`, `AggregationResult`, `ProviderOutcome`, `ProviderStatus`, `Freshness`, `ProviderUnavailableException`, two use-case ports and three outbound ports; update `domain-model.md` §8 first; record the Optional decision and the Freshness addition.
**Generated:** the types above in `domain.model`, ports in `domain.port.in` / `domain.port.out`, `InvalidSearchException`, `InvalidAggregationException`, tests for criteria, filter, aggregation result and page; `domain-model.md` §8 rewritten (ports, support types, Freshness table, Optional convention) before the code was written.
**Decision — Optional in records:** `Optional` is used as a component of `ItineraryFilter`, `ItineraryPage`, `SearchResult` and as the `Optional<PageCursor>` port parameter, with compact constructors rejecting a null `Optional`. This deliberately breaks AGENTS §6 ("no Optional as field or parameter"); the alternative (nullable fields with Optional accessors) would need hand-written classes instead of records. Documented in `domain-model.md` §8; `AGENTS.md` was not edited and still says the opposite (pending: add a pointer to §8).
**Decision — Freshness:** added `FRESH, STALE, REFRESHING, MISSING` with a behaviour table in §8 (a cursor request never triggers aggregation).
**Human review / corrections:** `domain-guardian` found 0 CRITICAL, 1 MAJOR, 4 MINOR. MAJOR: `PageCursor(Price, long lastId)` put a database id in the domain, against AGENTS §4; the human chose `PageCursor(Price lastFinalPrice, ItineraryKey lastKey)` instead of the prompt's `long`, so pages are ordered by (final price, key) and §8 was updated. MINOR fixed: `withX(null)` behaviour documented and tested, `succeeded()`/`empty()` documented, `limit` validation assigned to the application layer, `SearchResult` documented as a plain carrier, "journey" removed from §2 and a Javadoc, test constant `ROUTE` renamed. Also fixed a bug from A4 found while writing this: `ValidationSummary` threw on an empty non-EnumMap (EnumMap copy constructor); now copies via `putAll`. Open: persistence must order by `(final_price, itinerary_key)` and the keyset index should use that pair; `/check-arch` still has no `ArchitectureTest`, imports checked by hand.
**Artifacts:** `flight-aggregator/domain/src/main/java/io/github/jerojasol/flights/domain/**`, `flight-aggregator/domain/src/test/java/io/github/jerojasol/flights/domain/**`, `flight-aggregator/docs/domain-model.md`, `flight-aggregator/prompts/domain/05-ports-and-support-types.md`.

## 2026-11-14 — B1: AggregateFlightsService

**Intent:** Implement the aggregation use case (fan-out, validate, deduplicate, markup, persist) in the application layer with fakes-based tests.
**Prompt (summary):** `prompts/application/01-aggregate-flights-service.md`: `AggregateFlightsService implements AggregateFlightsUseCase` with injected providers, domain services, repository, `Executor`, `Duration providerBudget` and `Clock`; one `CompletableFuture` per provider that never fails, `orTimeout(providerBudget)` with `TIMEOUT` for stragglers, per-provider rejection counts, no logging framework; hand-written `StubProvider` and `InMemoryItineraryRepository`; seven scenarios; then `/check-arch`, this log, diff summary.
**Generated:** `AggregateFlightsService` (private nested `ProviderFetch` record), `AggregateFlightsServiceTest` (11 tests), application test `Fixtures`, `fakes/StubProvider`, `fakes/InMemoryItineraryRepository`. AI decisions: `validateAll` is called once per provider, because `ValidationSummary` carries no provider attribution and that is the only way to count rejections per provider; `allOf(...).orTimeout(budget)` followed by `getNow(TIMEOUT)` per future, so a late answer is ignored and a timed-out outcome reports `elapsed = budget`; elapsed time uses `System.nanoTime()`, the `Clock` only stamps `aggregatedAt`; a null list from a provider, an `Error` in the task and an executor rejection all become `ERROR`; `upsertAll` is skipped when there is nothing to persist; the constructor rejects an empty provider list, a provider registered twice and a non-positive budget (`IllegalArgumentException`); the domain test `Fixtures` is not visible from `application`, so a small local one was written. Extra tests beyond the prompt: cheapest offer persisted with markup, `CIRCUIT_OPEN` status passed through, executor rejection, duplicate provider.
**Human review / corrections:** No code corrections yet. `domain-guardian` (static review, it could not run Maven) found 0 CRITICAL, 2 MAJOR, 11 MINOR; none fixed in this task, pending human decision. MAJOR: two providers quoting different currencies for the same Itinerary Key make `ItineraryDeduplicator` throw `InvalidPriceException`, which aborts the whole aggregation; §10 assigns the check to adapters, but a single adapter cannot see the other providers' currency, so the rule needs a home in `domain-model.md` first. MAJOR: unexpected `RuntimeException`s and `Error`s become `ERROR` with the cause dropped, and `application` cannot log; the prompt asked for the `RuntimeException` mapping, converting `Error` was the AI's addition. MINOR, open: §9 does not describe the application-level budget; a timed-out provider call is abandoned, not cancelled, so adapters need their own timeout no longer than the budget; `Offer.provider` is not checked against the port that returned it; wall-clock assertions (750 ms, 2 s) may flake on a loaded CI; constructor guards, null provider list and null criteria are untested; `mockito-core` is unused; `InMemoryItineraryRepository.findBy`, `find` and `StubProvider.calls` were written ahead of B2 and are untested; `ProviderOutcome` accepts `offersRejected > offersReceived`; "Flight" in `AggregateFlightsUseCase` / `FlightProviderPort` contradicts AGENTS §5; `Segment.carrier` in §2 is stale; stale Javadoc on `ItineraryKey.fromStored`; two-argument `Itinerary.from` still uses the system clock. `/check-arch`: still no `ArchitectureTest`, imports checked by grep (no Spring, SLF4J or Resilience4j in `application`). The acceptance command `./mvnw -q -pl application test` fails to resolve the domain artifact unless `-am` is added or the domain is installed; tests were run with `-pl domain,application -am`.
**Artifacts:** `flight-aggregator/application/src/main/java/io/github/jerojasol/flights/application/AggregateFlightsService.java`, `flight-aggregator/application/src/test/java/io/github/jerojasol/flights/application/**`, `flight-aggregator/prompts/application/01-aggregate-flights-service.md`.

## 2026-11-14 — B1 follow-up: reference currency (O1) and Error propagation

**Intent:** Resolve the two MAJOR `domain-guardian` findings from B1.
**Prompt (summary):** Human decisions after the B1 review: (1) reference currency as an `OfferValidator` constructor argument, new invariant O1 and `RejectionReason.CURRENCY_MISMATCH`, checked before I1–I9, `domain-model.md` §3, §6, §7, §10 updated before the code; (2) let `Error` propagate out of `aggregate()`, catch `Exception` only and unwrap `CompletionException`.
**Generated:** `domain-model.md`: new "Offer" invariant O1 in §3, `CURRENCY_MISMATCH` and the check order in §6, validator row in §7, §10 rewritten (mismatching offers are rejected by the validator, not by adapters), plus the one-currency sentence in §5. Code: `OfferValidator(Currency referenceCurrency)` (null rejected with `InvalidOfferException`), `RejectionReason.CURRENCY_MISMATCH`; `AggregateFlightsService` no longer converts a failed future into `ERROR` and rethrows the `Error` unwrapped from the `CompletionException`. Tests: `OfferValidatorTest` (O1 rejected and counted, O1 before structural rules, null reference currency), `AggregateFlightsServiceTest` (same-key USD offer is rejected, counted against its provider and never reaches the deduplicator; a provider throwing `StackOverflowError` makes `aggregate()` throw); `StubProvider.failing` accepts an `Error`.
**Human review / corrections:** Both decisions were the human's; the B1 prompt wording ("never let an exception escape the future") was corrected by the human. AI choices to confirm: O1 lives in the validator only, so `Offer` still accepts any currency; the no-arg `OfferValidator` constructor was removed, so infrastructure wiring must supply the currency; an `Error` surfaces only after the fan-out wait ends (all providers answered or the budget expired), and an `Error` thrown after the budget is lost with the abandoned call. §9 does not yet mention the `Error` rule or the application-level budget. `domain-guardian` was not re-run after this change. Remaining B1 minor findings are still open.
**Artifacts:** `flight-aggregator/docs/domain-model.md`, `flight-aggregator/domain/src/main/java/io/github/jerojasol/flights/domain/model/OfferValidator.java`, `flight-aggregator/domain/src/main/java/io/github/jerojasol/flights/domain/model/RejectionReason.java`, `flight-aggregator/domain/src/test/java/io/github/jerojasol/flights/domain/model/OfferValidatorTest.java`, `flight-aggregator/application/src/main/java/io/github/jerojasol/flights/application/AggregateFlightsService.java`, `flight-aggregator/application/src/test/java/io/github/jerojasol/flights/application/**`.

## 2026-11-14 — B2: SearchItinerariesService (closes Phase B)

**Intent:** Implement the read path (FRESH / STALE / REFRESHING / MISSING) on top of the repository, the freshness store and the aggregation use case.
**Prompt (summary):** `prompts/application/02-search-itineraries-service.md`: `SearchItinerariesService` with the agreed read-path flow (cursor never aggregates; cold miss aggregates synchronously under the lock; stale serves first and refreshes on `refreshExecutor`), lock always released, search never fails because providers failed, `lastOutcomes` source order, `limit` clamped to [1, 100]; fakes `InMemoryFreshness`, `RecordingAggregator`, `CapturingExecutor`; nine scenarios; `domain-guardian` clean of CRITICAL/MAJOR.
**Generated:** `SearchItinerariesService`, `SearchItinerariesServiceTest` (20 tests), fakes `InMemoryFreshness`, `RecordingAggregator`, `CapturingExecutor`; `InMemoryItineraryRepository` gained `findBy` call/limit recording and a synchronized `upsertAll`, and lost unused `find`; unused `StubProvider.calls` removed. After review: `domain.model.LockToken` + `LockTokenTest`, `FreshnessPort.tryLock` → `Optional<LockToken>`, `release(criteria, token)`.
**AI decisions:** `markFresh` only when the aggregation `anySucceeded()`, so an outage is not cached as fresh and a cold miss then reports `MISSING` (prompt said to always mark fresh); an `Error` from the aggregator propagates after the lock is released (consistent with B1); a failed background refresh is rethrown to the executor after releasing the lock, so infrastructure can log it; any `RuntimeException` from `execute()` releases the lock.
**Human review / corrections:** `domain-guardian` first pass: 0 CRITICAL, 1 MAJOR, 7 MINOR. MAJOR: `release(criteria)` had no owner, so an aggregation that outlived `lockTtl` could delete another caller's lock. Human chose an owner token and **approved a scope exception** (B2 was "application only"): `domain-model.md` §8 updated first (port signatures, `LockToken`, new "Aggregation lock ownership" subsection that also records the all-failed rule and the `lastOutcomes` preference), then domain port, service, fake (compare-and-release, `expireLock()`) and a test where the lock expires mid-aggregation and the first caller's release leaves the new holder's lock intact. MINOR fixed: lock leak on non-rejection executor failures; silent background failures (the old comment contradicted the code); `lastOutcomes` now prefers the aggregation just performed over a stored result (AI originally followed the prompt's order, which could hide a failure that just happened); missing tests (all-failed background refresh, `Error` propagation, negative and in-range limits). Second pass: 0 CRITICAL, 0 MAJOR, 1 MINOR (exception list in §3 did not mention lock tokens), fixed. Deferred: `FreshnessPort` failures (Redis down) fail the search, to be handled in the Redis adapter; backoff after an all-failed aggregation and a shorter TTL for partial results; "Flight" in port names vs AGENTS §5. Note: two aggregations can still overlap after a TTL expiry; accepted because the cheaper-Final-Price-wins upsert keeps the result correct. `/check-arch`: still no `ArchitectureTest`, imports checked by grep; tests run with `-pl domain,application -am` (plain `-pl application` cannot resolve the domain artifact).
**Artifacts:** `flight-aggregator/application/src/main/java/io/github/jerojasol/flights/application/SearchItinerariesService.java`, `flight-aggregator/application/src/test/java/io/github/jerojasol/flights/application/**`, `flight-aggregator/domain/src/main/java/io/github/jerojasol/flights/domain/model/LockToken.java`, `flight-aggregator/domain/src/main/java/io/github/jerojasol/flights/domain/port/out/FreshnessPort.java`, `flight-aggregator/domain/src/test/java/io/github/jerojasol/flights/domain/model/LockTokenTest.java`, `flight-aggregator/docs/domain-model.md`.

## 2026-11-14 — C1: Deterministic inventory generator (mock-providers)

**Intent:** Give `mock-providers` a seeded, provider-independent inventory from which Alpha, Beta and Gamma will later be rendered.
**Prompt (summary):** `mock-providers/prompts/01-generator.md`: generator-only model (`GeneratedSegment`, `GeneratedItinerary`, `ProviderId`, `InvalidKind`), `InventoryGenerator.generate(origin, destination, date, provider)` seeded with `Objects.hash(seed, origin, destination, date)`; 60/30/10 non-stop/1/2 connections, overlap with ±5% per-provider prices, invalid mutations; `MockProperties` with ratio validation; `InventoryGeneratorTest`.
**Generated:** `config/MockProperties` (validated record with nested `Chaos`, `@ConfigurationPropertiesScan` on the application), the four generator types, `InventoryGenerator`, `InventoryGeneratorTest` (10 tests). AI decisions: the full inventory is built once per query and filtered, so random consumption never depends on the provider; overlap and invalid sets are exact counts (`round(size × ratio)`) picked by shuffle, not per-item coin flips; invalid kinds are assigned round-robin so every kind appears; a connection kind landing on a non-stop itinerary regenerates it as a one-connection itinerary; overlap/too-short mutations shift all later segments so only one rule is broken by construction (too-short = 5–15 min, overlap = 5–60 min); per-provider prices are retried until distinct; canonical IDs are made unique at generation; added public `baseInventory(...)` (base price, all providers) for the union test; `canonicalId` format `IB6251@2026-11-20T07:35Z>MAD-JFK|…`; currency fixed to EUR; hubs equal to origin/destination are excluded.
**Human review / corrections:** None yet. Notes: `DEAD_LEG_SAME_AIRPORT` on the first leg of a connecting itinerary also breaks the connection airport check (still one declared kind). No change to `docs/provider-contracts.md` (internal model only), so the aggregator fixtures are not affected.
**Artifacts:** `mock-providers/src/main/java/io/github/jerojasol/mockproviders/config/MockProperties.java`, `mock-providers/src/main/java/io/github/jerojasol/mockproviders/generator/**`, `mock-providers/src/main/java/io/github/jerojasol/mockproviders/MockProvidersApplication.java`, `mock-providers/src/test/java/io/github/jerojasol/mockproviders/generator/InventoryGeneratorTest.java`, `mock-providers/prompts/01-generator.md`.

## 2026-11-14 — C2: Alpha controller (JSON)

**Intent:** Expose the generated inventory as Alpha's flat JSON contract, with strict validation and a chaos error body ready for C5.
**Prompt (summary):** `mock-providers/prompts/02-alpha-controller.md`: Alpha-only records (`AlphaItinerary`, `AlphaSegment`, `AlphaError`), `GET /alpha/flights?from&to&date` mapped through a package-private `AlphaMapper`, 400 `BAD_REQUEST` on bad params (no uppercase normalisation), `[]` when empty, an `AlphaErrorWriter` for chaos 500s, `@WebMvcTest` tests.
**Generated:** `alpha/AlphaItinerary`, `AlphaSegment`, `AlphaError` (`@JsonInclude(NON_NULL)` so a 400 has no `ref`, as in the contract), `AlphaMapper` (price `BigDecimal.valueOf(cents, 2)`, `flightNo` unpadded, `id = "ALP-" + sha256(canonicalId)[0..6]`), `AlphaController` (params read as optional strings and validated by hand, so missing and malformed params share one 400 path; date parsed with strict `uuuu-MM-dd`), `AlphaErrorWriter` (`@Component`, writes the 500 via the Jackson 3 `JsonMapper`); `AlphaControllerTest` (6 tests, `@MockitoBean` generator) and `AlphaErrorWriterTest`.
**Human review / corrections:** None yet. Deviation from the prompt: `AlphaErrorWriter` is **public**, not package-private, because the chaos filter lives in `chaos/` and could not call it otherwise. Jackson 3 already writes `Instant` as ISO-8601 with `Z`, so `write-dates-as-timestamps` was not set. Verified live with chaos off: `.[0]` has exactly the contract's fields, `"price":983.40` keeps two decimals, `from=mad` returns 400. No contract change (the contract already allows unpadded `flightNo`), so aggregator fixtures are unaffected.
**Artifacts:** `mock-providers/src/main/java/io/github/jerojasol/mockproviders/alpha/**`, `mock-providers/src/test/java/io/github/jerojasol/mockproviders/alpha/**`, `mock-providers/prompts/02-alpha-controller.md`.

## 2026-11-14 — C3: Beta controller (XML)

**Intent:** Expose the generated inventory as Beta's legacy, namespaced XML contract, keeping its traps (local time + zone, unpadded numbers, string amounts, out-of-order legs).
**Prompt (summary):** `mock-providers/prompts/03-beta-controller.md`: Jackson 3 XML model mirroring the contract, `BetaMapper` (airport-local `LocalDateTime` + IANA `TimeZone`, `UTC` for unknown airports, unpadded `Number`, two-decimal string `amount`, deterministic leg shuffle guaranteeing at least one out-of-order multi-leg itinerary, `ref = "BET-" + sha256[0..4]`, `RequestId = BET-yyyyMMdd-NNNN`), `GET /beta/search`, `INVALID_REQUEST` faults, a chaos fault writer, `@WebMvcTest` + mapper tests.
**Generated:** `beta/BetaXml` (records nested in one holder: `SearchResponse`, `Meta`, `Itineraries`, `Itinerary`, `Pricing`, `Total`, `Leg`, `Flight`, `Endpoint`, `Code`, `Fault`), `BetaMapper` (package-private, 17-airport zone map, legs shuffled with `Random(Objects.hash(seed, canonicalId))`; if no multi-leg itinerary ended out of order, the first one is reversed), `BetaController` (built-in `BetaMapper` seeded from `MockProperties`, `RequestId` counter wraps at 10,000, `RequestId` date = the requested date), `BetaFaultWriter`, `config/XmlConfig` (`XmlMapperBuilderCustomizer` enabling the XML declaration); tests `BetaControllerTest` (5), `BetaMapperTest` (6), `BetaFaultWriterTest` (1).
**Human review / corrections:** None yet. AI caught one issue during development: with the namespace only on the root, Jackson wrote `xmlns=""` on every child, moving them out of `urn:beta-gds:search:v2`; fixed by declaring the namespace on every element property and on the `Legs` wrapper. Deviation from the prompt: `BetaFaultWriter` is **public** (the chaos filter lives in `chaos/`). Records worked for the whole model, so no mutable POJOs were needed. Woodstox writes the declaration with single quotes (`<?xml version='1.0' encoding='UTF-8'?>`); it is equivalent XML but not byte-identical to the contract example. `RequestId` comes from a process-wide counter, so two identical queries return the same body except for that element. Verified live with chaos off (`xmllint --format` matches the contract structure; missing `origin` returns a 400 `INVALID_REQUEST` fault). No contract change, so aggregator fixtures are unaffected.
**Artifacts:** `mock-providers/src/main/java/io/github/jerojasol/mockproviders/beta/**`, `mock-providers/src/main/java/io/github/jerojasol/mockproviders/config/XmlConfig.java`, `mock-providers/src/test/java/io/github/jerojasol/mockproviders/beta/**`, `mock-providers/prompts/03-beta-controller.md`.

## 2026-11-14 — C4: Gamma controller (streamed CSV)

**Intent:** Expose the generated inventory as Gamma's streamed CSV contract, with plain-text errors and a chaos error writer ready for C5.
**Prompt (summary):** `mock-providers/prompts/04-gamma-controller.md`: `GammaLineFormatter` (`GAM-` + sha256[0..4], cents, `|`/`;` packing, 4-digit zero-padded `flightNo`, `yyyyMMddHHmm` UTC, header and `#END`), `GET /gamma/export?o&d&dt` as `StreamingResponseBody` flushing every 200 lines, `ERR 400 …` text/plain on bad params, `GammaErrorWriter` for `ERR 500 export failed ref=…`, `@WebMvcTest` with `asyncDispatch` + formatter unit tests.
**Generated:** `gamma/GammaLineFormatter` (package-private, static), `GammaController` (validation, then a `StreamingResponseBody` writing through a `BufferedWriter`, flush every 200 lines and at the end; lines end in `\n`), `GammaErrorWriter`; tests `GammaControllerTest` (6, incl. a 450-line inventory crossing the flush boundary and the exact `ERR 400 missing parameter: o` body), `GammaLineFormatterTest` (4, incl. the contract's two-segment line), `GammaErrorWriterTest` (1). Contract: one sentence added to Gamma's 400 section, `ERR 400 invalid parameter: dt` for present-but-malformed params (the contract only defined the "missing" wording), written before the code.
**Human review / corrections:** None yet. AI corrected its first attempt: returning `ResponseEntity<?>` (to mix the CSV body and a `String` 400) made Spring skip the streaming return-value handler, so async never started and 4 tests failed; the method now returns `ResponseEntity<StreamingResponseBody>` and the 400s are a private `GammaBadRequest` exception mapped by a controller-local `@ExceptionHandler`. Deviation from the prompt: `GammaErrorWriter` is **public** (the chaos filter lives in `chaos/`). Verified live with chaos off: `Content-Type: text/csv;charset=UTF-8`, `Transfer-Encoding: chunked`, header + lines + `#END` (243 lines), `dt=2026-11-20` → 400 `ERR 400 invalid parameter: dt`. The contract addition only documents a new 400 wording; the aggregator's success-path fixtures are unaffected, but any aggregator test matching the exact 400 body would need it.
**Artifacts:** `mock-providers/src/main/java/io/github/jerojasol/mockproviders/gamma/**`, `mock-providers/src/test/java/io/github/jerojasol/mockproviders/gamma/**`, `mock-providers/docs/provider-contracts.md`, `mock-providers/prompts/04-gamma-controller.md`.

## 2026-11-14 — C5: Chaos filter and admin endpoint

**Intent:** Inject latency and provider-flavoured 500s on the three provider paths and let a live demo retune chaos without a restart.
**Prompt (summary):** `mock-providers/prompts/05-chaos-and-admin.md`: `ChaosState` (`AtomicReference<ChaosConfig>`, validated), `ChaosFilter` registered via `FilterRegistrationBean` for `/alpha/*`, `/beta/*`, `/gamma/*` only (uniform latency, then 500 with probability `errorRate` through the provider's writer, one log line per request), 30 s cap on `latencyMax`, `GET`/`PUT /admin/chaos` merging partial JSON with `DurationStyle.detectAndParse`; tests for each provider's 500, pass-through, latency, health and admin.
**Generated:** `chaos/ChaosConfig` (compact constructor: no negative latency, `min ≤ max`, `max ≤ 30s`, `errorRate ∈ [0,1]`, NaN rejected), `ChaosState` (`update` uses `updateAndGet`, so a rejected patch leaves the state unchanged), `ChaosFilter` (not a `@Component`, so it cannot be auto-registered on every path), `ChaosConfiguration` (state bean + registration), `ChaosAdminController` (`ChaosPatch` with nullable fields, `ChaosView` returning durations as `"<n>ms"`, `IllegalArgumentException` → 400 `{error: BAD_REQUEST, message}`); `ChaosFilterTest` (13, `@SpringBootTest` + MockMvc) and `ChaosConfigTest` (3). `MockProvidersApplicationTest` now sets `mock.chaos.enabled=false` (AGENTS §7). Contract "Admin" section extended first: partial updates, accepted duration formats, response shape and the 400 body.
**Decisions:** Chaos `Random` is **seeded with `mock.seed + 1`** (separate from the generator), so a run of sequential requests is reproducible; concurrent requests still interleave non-deterministically. The 500 decision is drawn before sleeping, and errors sleep too (AGENTS §5). The 6-hex `ref` comes from the same chaos `Random`. A request that fails is logged `outcome=ERROR`; an `OK` line means "passed to the controller" (a 400 from the controller is still `OK`). An invalid `mock.chaos.*` at startup now fails boot (`latency-max` over 30 s, for example).
**Human review / corrections:** None yet. Verified live with defaults: 20 Alpha calls gave 18×200 / 2×500 with latencies 0.42–4.68 s; after `PUT {"errorRate":1}` Beta returned the namespaced `SYSTEM_ERROR` fault; `/actuator/health` answered 200 in 2 ms. Note: chaos 500s for Beta now carry the XML declaration (Boot's `XmlMapper` with `XmlConfig`), the same as 200s. The aggregator's adapters and fixtures are unaffected; the admin API is only used by humans.
**Artifacts:** `mock-providers/src/main/java/io/github/jerojasol/mockproviders/chaos/**`, `mock-providers/src/test/java/io/github/jerojasol/mockproviders/chaos/**`, `mock-providers/src/test/java/io/github/jerojasol/mockproviders/MockProvidersApplicationTest.java`, `mock-providers/docs/provider-contracts.md`, `mock-providers/prompts/05-chaos-and-admin.md`.

## 2026-11-14 — C6: Dockerfile, contract verification, fixture export (closes Phase C)

**Intent:** Make `mock-providers` shippable in compose, give a repeatable check that a running instance honours `provider-contracts.md`, and give the aggregator a sanctioned, file-only way to get real provider payloads as test fixtures.
**Prompt (summary):** `mock-providers/prompts/06-dockerfile-and-verify.md`: multi-stage `Dockerfile` (temurin 21 jdk → jre, non-root, `EXPOSE 8081`, curl healthcheck) + `.dockerignore`; `scripts/verify-contract.sh` (no startup, `MOCK_URL`, status/`Content-Type`/structure/400 per provider, PASS/FAIL, non-zero exit); `scripts/export-fixtures.sh` (chaos off, size 2, `two-itineraries.*` and `empty.*` into `../flight-aggregator/infrastructure/src/test/resources/fixtures/`, diff summary, header stating fixtures are the only sanctioned channel); one-page `README.md`.
**Generated:** `Dockerfile` (dependency layer via `dependency:go-offline` before copying `src`; runtime installs `curl`, user `app` uid 10001; healthcheck with interval/start-period), `.dockerignore` (prompt's list plus `scripts/`, `.opencode/`, `AGENTS.md`, `README.md`, `HELP.md`), `scripts/verify-contract.sh` (25 checks: 8 Alpha with `jq`, 10 Beta with namespace-agnostic `xmllint --xpath` via `local-name()`, 7 Gamma with `awk`), `scripts/export-fixtures.sh`, `README.md`, and the six fixture files. `MockProperties.inventorySize` changed from `@Positive` to `@PositiveOrZero` so `MOCK_INVENTORY_SIZE=0` can produce the `empty.*` fixtures.
**Decisions:** The prompt's "size 2" does not guarantee two itineraries per provider (each base itinerary is split across providers). AI searched the generator offline and fixed **`MOCK_SEED=17`, `MOCK_OVERLAP_RATIO=1.0`, `MOCK_INVALID_RATIO=0`**: both itineraries are sold by all three providers, one non-stop and one three-leg (Beta emits it as `2,1,3`, so the fixture exercises sequence reordering). The script fails loudly if a future generator change breaks that. The export script starts its own jar instances (port 18081; builds the jar if missing) instead of assuming a running service, because it needs two configurations; it restarts before exporting so Beta's `RequestId` is always `BET-20261120-0000`, which makes the export byte-reproducible (second run: all six "unchanged"). Fixtures are the raw response bodies, not pretty-printed.
**Human review / corrections:** The human started the Docker daemon, which was down at first. AI fixed three false positives in its own `verify-contract.sh` by running it against a dead URL: two Beta checks compared empty strings, and the Gamma line check passed on an empty body; all now require content (25/25 fail with no server). Verified: `./mvnw verify` green; `docker build -t mock-providers .` succeeds (367 MB); the container reports `healthy`, runs as `app`, and `verify-contract.sh` passes 25/25 against it with chaos off; the same against the local jar. The aggregator has no mapper tests yet, so "fixtures pass the mapper tests" is still open. The fixtures directory was created by the script in `flight-aggregator`.
**Artifacts:** `mock-providers/Dockerfile`, `mock-providers/.dockerignore`, `mock-providers/scripts/verify-contract.sh`, `mock-providers/scripts/export-fixtures.sh`, `mock-providers/README.md`, `mock-providers/src/main/java/io/github/jerojasol/mockproviders/config/MockProperties.java`, `flight-aggregator/infrastructure/src/test/resources/fixtures/{alpha,beta,gamma}/{two-itineraries,empty}.*`, `mock-providers/prompts/06-dockerfile-and-verify.md`.

## 2026-11-14 — D1: Failsafe activation and ArchitectureTest

**Intent:** Make integration tests actually run and lock the hexagonal structure with ArchUnit before any infrastructure code exists.
**Prompt (summary):** `flight-aggregator/prompts/infrastructure/01-architecture-test.md`: declare `maven-failsafe-plugin` (excluding `perf`) plus a `perf` profile; update AGENTS §8 commands; prove Failsafe with a throwaway `SmokeIT`; write `ArchitectureTest` with 10 rules (domain/application dependency limits, no framework annotations in domain, web isolated from outbound adapters, controllers limited to inbound ports, JPA entities confined to persistence, provider packages confined to their adapter or `config`, ports are interfaces, no `@Autowired` fields, layered architecture); prove that a Spring annotation in domain breaks rules 1 and 3.
**Generated:** Failsafe plugin + `perf` profile (`<excludedGroups combine.self="override"/>`, `<groups>perf</groups>`) in `infrastructure/pom.xml`; AGENTS §8 commands (`verify`, `verify -DskipITs`, `verify -Pperf`; `-Pintegration` removed); `ArchitectureTest` (10 `@ArchTest` fields; rule 7 is a `CompositeArchRule` over alpha/beta/gamma; rules that target still-empty infrastructure packages use `allowEmptyShould(true)`; the layered rule uses `withOptionalLayers(true)`, so an empty Infrastructure layer does not fail).
**Human review / corrections:** None yet. Findings during verification: (1) `./mvnw -pl infrastructure …` alone does not resolve because the parent POM is not installed; use `-am`. (2) `infrastructure` has no `@SpringBootApplication` yet, so `spring-boot:repackage` fails in `package` before Failsafe runs; Failsafe was verified with `-Dspring-boot.repackage.skip=true` (`SmokeIT`: Tests run: 1; `-Pperf`: Tests run: 0), and `SmokeIT` was deleted. `./mvnw verify` stays red until the application class exists (next step). (3) An unused import produces no bytecode dependency, and `domain/` cannot compile against Spring, so the negative check used a temporary `@Service`-annotated class in package `…domain.model`, placed in infrastructure sources. Rules 1 and 3 failed with readable messages; the class was then removed.
**Artifacts:** `flight-aggregator/infrastructure/pom.xml`, `flight-aggregator/AGENTS.md`, `flight-aggregator/infrastructure/src/test/java/io/github/jerojasol/flights/infrastructure/ArchitectureTest.java`, `flight-aggregator/prompts/infrastructure/01-architecture-test.md`.

## 2026-11-14 — D1 follow-up: `/check-arch` missing `-am` (AI tooling failure, corrected)

**Intent:** Make `/check-arch` work.
**Prompt (summary):** Human asked to add `-am` to the ArchUnit command in `/check-arch`, keep the domain/application command as it is, rerun `/check-arch`, and record this as an AI tooling failure.
**Generated:** One-line change in `flight-aggregator/.opencode/commands/check-arch.md`: `./mvnw -q -pl infrastructure -am test -Dtest=ArchitectureTest -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | tail -40`.
**Human review / corrections:** **AI tooling failure.** The AI wrote the original `/check-arch` command without `-am`. The parent POM `io.github.jerojasol:flight-aggregator` is not installed in the local repository, so Maven could not resolve it and failed before running any test. The command looked like it ran the architecture check, but it never did. The AI found this while doing D1 and proposed the fix; the human approved it. With the fix, the ArchUnit command exits 0 (10/10 rules) and `./mvnw -q -pl domain,application test` exits 0 (unchanged; it already worked because it sits under the reactor root). Lesson: test every new command once and check its exit code; a quiet `-q … | tail` can hide a resolution failure.
**Artifacts:** `flight-aggregator/.opencode/commands/check-arch.md`.

## 2026-11-14 — D1 follow-up: fix the three domain-guardian findings

**Intent:** Fix the three MINOR findings from the first full `/check-arch` run before starting D2.
**Prompt (summary):** Human asked for three fixes. (1) Delete `Itinerary.from(Offer, MarkupPolicy)`, which called `Instant.now()`; do not document it; update its only caller, `ItineraryPageTest`. (2) Remove the `Optional<AggregationResult>` parameter from the private `SearchItinerariesService.result(...)` by overloading it, keep `Optional<PageCursor>` (it mirrors the port and is covered by the domain-model.md §8 exception), and keep the behaviour exactly as it was, including when aggregation throws in `coldMiss`. (3) Correct the `ItineraryKey.fromStored` Javadoc.
**Generated:**
- (1) The overload is gone. `ItineraryPageTest` passes `Instant.parse("2026-11-14T10:00:00Z")`. The only production path is `ItineraryFactory`, which uses its `Clock`.
- (2) There are now two private overloads: `result(…, reported)` reads `freshness.lastResult(criteria)`, and `result(…, reported, AggregationResult justAggregated)` uses the fresh result without reading `lastResult` (previously `Optional.or` was lazy, so this matches). In `coldMiss`, the `catch` returns the fallback overload with `MISSING`; the `finally` still releases the lock before the repository is read. On success, `reported` is `FRESH` if `anySucceeded()` and `MISSING` otherwise, the same as before.
- (3) The Javadoc now says `fromStored` checks the value against `[0-9a-f]{64}` and throws `InvalidItineraryKeyException`; what it trusts is that the key was derived from the segments (the I12 rehydration exception).
**Human review / corrections:** The human chose all three fixes, including deleting the overload rather than documenting it and keeping `Optional<PageCursor>`. These were AI-written defects found by the AI's own `domain-guardian` review: an undocumented convenience factory that went around the injected `Clock`, an `Optional` parameter outside the documented exception, and a Javadoc that contradicted the code. No test was weakened; the existing `coldMiss` tests passed without changes. Verified: `./mvnw -q -pl domain,application test` passes (215 + 33) and ArchUnit passes 10/10. Not committed.
**Artifacts:** `flight-aggregator/domain/src/main/java/io/github/jerojasol/flights/domain/model/Itinerary.java`, `flight-aggregator/domain/src/main/java/io/github/jerojasol/flights/domain/model/ItineraryKey.java`, `flight-aggregator/domain/src/test/java/io/github/jerojasol/flights/domain/model/ItineraryPageTest.java`, `flight-aggregator/application/src/main/java/io/github/jerojasol/flights/application/SearchItinerariesService.java`.

## 2026-11-14 — Correction to "fix the three domain-guardian findings": `coldMiss` regression

**Intent:** Undo a behaviour change the AI introduced while removing the `Optional<AggregationResult>` parameter, and correct the claim in the previous entry.
**Prompt (summary):** `domain-guardian` re-reviewed the three fixes. It confirmed all three findings were resolved and raised two MAJOR regressions in `SearchItinerariesService.coldMiss`.
**Generated:**
- `coldMiss` is back to its original try/catch/finally shape, using a method-local `AggregationResult aggregated = null` (it never leaves the method). After the `finally`, the method picks an overload: `aggregated == null ? result(…, reported) : result(…, reported, aggregated)`.
- Two regression tests in `SearchItinerariesServiceTest`: `should_release_lock_before_reading_repository_when_synchronous_aggregation_throws` and `should_report_just_aggregated_outcomes_as_missing_when_mark_fresh_fails`.
- Test-fake hooks: `InMemoryItineraryRepository.onFindBy(Runnable)`, `InMemoryFreshness.failingMarkFreshWith(RuntimeException)` and `lastResultCalls()`.
**Human review / corrections:** **The previous entry was wrong.** It said the lock was still released before the repository read when aggregation throws, and that existing tests proved the behaviour unchanged. Both claims were false:
1. The AI put `return result(...)` inside the `catch`. Java evaluates the return expression before `finally`, so `repository.findBy` and `freshness.lastResult` ran while the aggregation lock was still held. During that window, concurrent cold misses would get `REFRESHING`.
2. `markFresh` sat in the same `try` as `aggregate`, so a failing freshness store (for example Redis down) made the response drop the outcomes it had just aggregated and serve the older `lastResult` instead.

The existing tests did not catch either problem: they recorded the order of freshness calls but not where the repository read fell relative to them, and nothing simulated `markFresh` failing. Both new tests failed against the faulty code ("lock held during repository read") before the fix, and pass after it. Verified: 215 domain + 35 application tests and ArchUnit 10/10 pass. Lesson: when a refactor has to keep behaviour the same, first check that tests cover each path being moved (here the exception path, and the order of side effects around `finally`). Not committed.
**Artifacts:** `flight-aggregator/application/src/main/java/io/github/jerojasol/flights/application/SearchItinerariesService.java`, `flight-aggregator/application/src/test/java/io/github/jerojasol/flights/application/SearchItinerariesServiceTest.java`, `flight-aggregator/application/src/test/java/io/github/jerojasol/flights/application/fakes/InMemoryItineraryRepository.java`, `flight-aggregator/application/src/test/java/io/github/jerojasol/flights/application/fakes/InMemoryFreshness.java`.

## 2026-11-14 — D2: Persistence (Flyway schema, JPA read side, JDBC upsert, keyset pagination)

**Intent:** Implement `ItineraryRepositoryPort` on PostgreSQL: one row per Itinerary Key, cheapest wins, and keyset pagination whose cost does not grow with page depth.
**Prompt (summary):** `flight-aggregator/prompts/infrastructure/02-persistence.md`: Flyway `V1__create_itineraries.sql` with commented indexes; package-private `ItineraryJpaEntity` (read side); `ItineraryJpaRepository` with one native keyset `@Query` fetching `limit + 1`; `ItineraryJdbcUpserter` (`batchUpdate`, chunks of 1,000, `ON CONFLICT … WHERE EXCLUDED.final_price < itineraries.final_price`); `ItineraryPersistenceMapper` with a private `SegmentJson` record and Jackson 3; `ItineraryRepositoryAdapter`; `PersistenceConfiguration`; `ItineraryRepositoryAdapterIT` (Testcontainers PostgreSQL 16) and a `@Tag("perf")` `KeysetPaginationPerfIT` on 1M rows.
**Generated:**
- `V1__create_itineraries.sql`; every index has a comment saying why it exists (for `SOLUTION.md`).
- `persistence/`: `ItineraryJpaEntity`, `ItineraryJpaRepository`, `ItineraryJdbcUpserter`, `ItineraryPersistenceMapper` (with an `ItineraryRow` record for the write side), `ItineraryRepositoryAdapter`, `PersistenceConfiguration`. Everything is package-private except `PersistenceConfiguration`; only `ItineraryRepositoryPort` leaves the package.
- `FlightAggregatorApplication`: the `@SpringBootApplication` class the slice tests need. It also makes `spring-boot:repackage` work, so `./mvnw verify` no longer needs `-Dspring-boot.repackage.skip` (open since D1).
- Tests: `ItineraryRepositoryAdapterIT` (9) and `KeysetPaginationPerfIT` (1, perf only).
**Decisions:**
- **Keyset on `(final_price, itinerary_key)`, not `(final_price, id)` as the prompt said.** The domain's `PageCursor` is `(lastFinalPrice, lastKey)`, `domain-model.md` §8 defines page order as (final price, key), and AGENTS.md §4 forbids database ids in the domain. `ix_itineraries_search` therefore ends in `itinerary_key`. The key is unique, so it is as good a tie-break as `id`, and the in-memory test repository in `application` already used the same order.
- **Carrier filter uses `carriers @> ARRAY[:carrier]`, not `:carrier = ANY(carriers)`.** Both give the same results, but only `@>` can use the GIN index. With `= ANY`, the index comment would have been false.
- Null optional filters are passed as `CAST(:p AS type) IS NULL` so Postgres can type a null parameter.
- The entity maps only the columns needed to rebuild an Itinerary. `CHAR(n)` columns use `@JdbcTypeCode(SqlTypes.CHAR)` and `segments` uses `@JdbcTypeCode(SqlTypes.JSON)` on a `String`.
- Segment JSON stores instants as ISO strings, so the stored format never depends on mapper settings.
- `ItineraryFilter.origin/destination` that contradict the criteria return an empty page without a query; matching ones are no-ops.
- `PersistenceConfiguration` lives in `persistence/`, not `config/`, because the types it wires are package-private.
- The IT runs with `@Transactional(NOT_SUPPORTED)` and truncates the table before each test. With a shared rolled-back test transaction, Hibernate could serve cached entities after a JDBC upsert and hide what the database actually holds.
**Human review / corrections:** None yet.
- The `(final_price, itinerary_key)` deviation is reported for human confirmation.
- Verified: 215 domain + 35 application tests, ArchUnit 10/10, and 9/9 ITs pass under `./mvnw -pl infrastructure -am verify`. `-Pperf` runs only the perf test.
- Perf on 1,000,000 rows of one route and date, page of 20 at depth 900,000, Postgres 16 in Testcontainers on a laptop: keyset **0.145 ms** using `ix_itineraries_search`, `OFFSET 900000` **444.136 ms**. These numbers are for `SOLUTION.md` §4.3.
- `ddl-auto=validate` was shown to be active: renaming a mapped column made all 9 ITs fail with `Schema validation: missing column [source_x]`, and the column was then restored.
- **AI tooling defect found:** `/test-layer` has the same missing `-am` as `/check-arch` had. Its `application`, `infrastructure` and `it` mappings fail before running any test (checked: `-pl infrastructure verify` and `-pl application test` both exit 1). Not fixed yet; proposed to the human.
**Artifacts:** `flight-aggregator/infrastructure/src/main/resources/db/migration/V1__create_itineraries.sql`, `flight-aggregator/infrastructure/src/main/java/io/github/jerojasol/flights/infrastructure/FlightAggregatorApplication.java`, `flight-aggregator/infrastructure/src/main/java/io/github/jerojasol/flights/infrastructure/persistence/**`, `flight-aggregator/infrastructure/src/test/java/io/github/jerojasol/flights/infrastructure/persistence/**`, `flight-aggregator/prompts/infrastructure/02-persistence.md`.

## 2026-11-14 — D2 follow-up: keyset query plan, currency in price filters, update counts

**Intent:** Fix two `domain-guardian` findings and a query-plan problem in the persistence adapter before committing D2.
**Prompt (summary):** Human asked for three fixes:
1. Split the keyset query into a first-page query and a later-pages query with a plain row comparison, because `(:cursorPrice IS NULL OR (final_price, itinerary_key) > …)` cannot be an index condition under a generic plan. The human measured 9,256 vs 55 buffers for a page 90% deep on 1M rows. Add an IT that forces a generic plan and asserts the row comparison is in `Index Cond`, not `Filter`.
2. Currency option (a): filter by the currency of `maxPrice` and of the cursor price, and return an empty page when the two differ. Add an IT for a `maxPrice` in another currency.
3. Throw on a negative JDBC update count instead of counting 0, with a unit test on a stubbed `JdbcTemplate` (no `reWriteBatchedInserts` IT).

Also: correct the D2 prompt so the cursor is `(final_price, itinerary_key)`.
**Generated:**
- `ItineraryJpaRepository` now has `FIRST_PAGE` and `NEXT_PAGE` as public constants on the interface, so the plan test EXPLAINs the exact SQL that runs, with `findFirstPage` / `findNextPage` using them. A `currency` filter is added, and route parameters are cast to `char(3)`.
- `ItineraryRepositoryAdapter` resolves the currency from `maxPrice` or the cursor; a mismatch returns `ItineraryPage.empty()` without querying.
- `ItineraryJdbcUpserter` throws `IllegalStateException` on a negative count.
- Tests:
  - `ItineraryJdbcUpserterTest` (2 unit tests).
  - 3 new `ItineraryRepositoryAdapterIT` cases: `maxPrice` in USD, cursor in USD, mismatch.
  - `KeysetQueryPlanIT`: 50k rows plus `ANALYZE`; one connection runs `SET plan_cache_mode = force_generic_plan`, then `PREPARE` from `NEXT_PAGE` with `:name` turned into `$n`, typed as pgjdbc binds them, then `EXPLAIN (FORMAT JSON) EXECUTE`; it walks the plan JSON.
- `KeysetPaginationPerfIT` now uses the `NEXT_PAGE` predicates and binds the route as parameters.
- D2 prompt: cursor and index are `(final_price, itinerary_key)`, with a note on why the cursor must not use `IS NULL OR`.
**Human review / corrections:** The human found the generic-plan problem; the AI had written the cursor in the `IS NULL OR` form the prompt suggested, and its perf test did not catch it, because it ran `EXPLAIN ANALYZE` with literals, which get a custom plan.

**A second, worse bug, which the new plan IT found on its first run:** `origin` and `destination` are `CHAR(3)`, and pgjdbc binds Java Strings as `varchar`. So `origin = :origin` became `(origin)::text = $1`, which casts the indexed column. Postgres could not use `ix_itineraries_search` at all, and the plan was a **Seq Scan + Sort for every search, first page included**. The original IT and the perf IT both missed it because they used literals (`'MAD'` is typed `char`) or only checked results. Fix: `CAST(:origin AS char(3))`, `CAST(:destination AS char(3))`.

Verified:
- With the old `IS NULL OR` cursor put back temporarily, `KeysetQueryPlanIT` fails (no `Index Cond` contains `ROW(final_price, itinerary_key) > ROW(`); it was then restored.
- `./mvnw -pl infrastructure -am verify` passes: 215 domain, 35 application, 12 infrastructure unit tests (ArchUnit 10/10 + upserter 2), and 13 ITs (12 adapter + 1 plan).
- Perf with the route bound as varchar parameters: keyset **0.209 ms** vs `OFFSET 900000` **550.967 ms**. These replace the D2 entry's 0.145 / 444.136 ms, which were measured with literals and so did not reflect real binding.

Lesson: a plan assertion must use the same parameter types and plan mode as production; literals in EXPLAIN can hide both bugs. Not committed.
**Artifacts:** `flight-aggregator/infrastructure/src/main/java/io/github/jerojasol/flights/infrastructure/persistence/ItineraryJpaRepository.java`, `…/persistence/ItineraryRepositoryAdapter.java`, `…/persistence/ItineraryJdbcUpserter.java`, `flight-aggregator/infrastructure/src/test/java/io/github/jerojasol/flights/infrastructure/persistence/ItineraryJdbcUpserterTest.java`, `…/persistence/ItineraryRepositoryAdapterIT.java`, `…/persistence/KeysetQueryPlanIT.java`, `…/persistence/KeysetPaginationPerfIT.java`, `flight-aggregator/prompts/infrastructure/02-persistence.md`.

## 2026-11-14 — D2 follow-up: upsert must not compare prices across currencies

**Intent:** Fix the MAJOR P4 finding from `domain-guardian`'s third D2 review: the upsert compared prices across currencies.
**Prompt (summary):** Human approved the AI's recommendation: add a currency-equality condition to the upsert's conflict update, add the reviewer's IT, and record the remaining mixed-currency ordering gap in `SOLUTION.md` instead of fixing it now.
**Generated:**
- `ItineraryJdbcUpserter`: `AND EXCLUDED.currency = itineraries.currency` in the `ON CONFLICT … DO UPDATE WHERE`, and the class Javadoc now explains it. A conflict in another currency writes nothing and counts 0.
- `ItineraryRepositoryAdapterIT.should_leave_row_untouched_when_same_key_arrives_cheaper_in_another_currency`: stores EUR 100, upserts the same key at USD 50, and asserts count 0, base price still EUR 100, final price and source unchanged.
- `docs/SOLUTION.md` §6: under the single-currency bullet, how currency is handled and the known gap (an unfiltered first page orders mixed-currency rows by raw amount if the reference currency ever changes).
**Human review / corrections:** The defect was in the AI's original D2 upsert. Before the fix, a cheaper USD offer overwrote a EUR row and kept `currency = 'EUR'`, re-labelling the price. The reviewer missed it in its first D2 review and caught it on the second, which is why reviewing again after each fix was worth it. The human chose skipping the update over throwing, so one row in another currency cannot fail a whole aggregation. Verified: with the new condition removed temporarily, the new IT fails (`expected: 0 but was: 1`); it was then restored. `./mvnw -pl infrastructure -am verify` passes: 215 domain, 35 application, 12 infrastructure unit, 14 ITs. `/check-arch` passes (ArchUnit 10/10; domain/application unchanged). Not committed.
**Artifacts:** `flight-aggregator/infrastructure/src/main/java/io/github/jerojasol/flights/infrastructure/persistence/ItineraryJdbcUpserter.java`, `flight-aggregator/infrastructure/src/test/java/io/github/jerojasol/flights/infrastructure/persistence/ItineraryRepositoryAdapterIT.java`, `docs/SOLUTION.md`.

## 2026-11-14 — `/test-layer` missing `-am` (AI tooling failure, corrected)

**Intent:** Make every `/test-layer` option run its tests.
**Prompt (summary):** Human approved adding `-am`, as was done for `/check-arch`.
**Generated:** In `flight-aggregator/.opencode/commands/test-layer.md`, the `application`, `infrastructure` and `it` mappings now use `-pl <module> -am`. `domain` and `all` are unchanged; they already worked.
**Human review / corrections:** **AI tooling failure, the same as `/check-arch`.** The command was written without `-am`, so any module that depends on another (`application`, `infrastructure`) could not resolve the uninstalled parent POM, and Maven failed before running any test. Found during D2. Verified each mapping by exit code: `-pl domain test` 0; `-pl application test` 1 before, `-pl application -am test` 0 after; `-pl infrastructure -am test` 0; `-pl infrastructure -am verify` 0; `verify` 0. Side effect: `-am` also runs the tests of upstream modules, so the `application` and `infrastructure` layers report domain (and application) counts too. A first check loop reported every command as failing; that was the AI's shell script (zsh does not word-split `$c`), not Maven, and was rerun with `${=c}`.
**Artifacts:** `flight-aggregator/.opencode/commands/test-layer.md`.

## 2026-11-14 — D3 (Alpha): provider adapter, and a port change so dead legs are counted

**Intent:** Implement the Alpha provider adapter (fixtures, DTOs, mapper, client, resilience, wiring), the first of three D3 runs, and make itineraries that cannot be mapped visible in the per-provider counts.
**Prompt (summary):** `flight-aggregator/prompts/infrastructure/03-provider-adapter.md` for Alpha: fixtures first; DTOs in Alpha's vocabulary; `AlphaOfferMapper` returning offers plus a skipped count; `AlphaClient` on `RestClient`; `AlphaProviderAdapter` with programmatic Retry(CircuitBreaker(TimeLimiter)); `ProviderConfiguration`; `AlphaOfferMapperTest` with fixtures and `AlphaProviderAdapterIT` with WireMock.
**Decisions:**
- **Port change, chosen by the human from three options.** `FlightProviderPort.fetch` returned `List<Offer>`, so a skipped count could not reach the application. The provider contract says dead legs must be "dropped and counted per provider", and dead legs fail `Segment` construction (S1–S3) inside the mapper. The new domain record `ProviderResponse(List<Offer> offers, int unmappable)` is returned by the port, and the application counts unmappable itineraries as received and rejected. `domain-model.md` §8–§9 was updated before the code (AGENTS §9). The prompt's `MappingResult` is therefore the domain's `ProviderResponse`; there is no extra infrastructure type.
- **Skipped means folded into `offersRejected`** (the prompt asked for this decision): `offersReceived = offers + unmappable`, `offersRejected = OfferValidator rejections + unmappable`.
- The mapper skips an itinerary on any `DomainException` or `DateTimeParseException` while building it (dead legs, malformed codes or timestamps). Connection rules are still checked only by `OfferValidator`, so each rule is enforced in one place.
- **Fixtures:** `two-itineraries.json` and `empty.json` are the files exported by `mock-providers/scripts/export-fixtures.sh` (the sanctioned channel), so they were kept. The contract's example, copied verbatim, went into a new `contract-example.json`; it already contains the Alpha trap (`"flightNo": "0117"`), so it is also the trap fixture. New: `error-500.json` (contract body) and `dead-legs.json` (MAD→MAD, and arr = dep).
- Alpha's body is a top-level array, so there is no `AlphaSearchResponse` wrapper; `AlphaItinerary` / `AlphaSegment` records hold raw strings and the mapper normalises. `AlphaResponseParser` is shared by the client and the tests, with exact BigDecimal prices and unknown fields ignored.
- Resilience lives in a shared `provider/ProviderResilience` (plus `ResilienceSettings.defaults`: window 10, 50%, 30 s open, 2 attempts) so Beta and Gamma reuse it. ArchUnit rule 7 constrains only `provider.<name>`. Retry covers only `ERROR`, which includes 4xx because the prompt maps 4xx to `ERROR`; `TIMEOUT` and `CIRCUIT_OPEN` are never retried.
- `ProviderConfiguration` and `ProviderProperties` live in `infrastructure.config`. A virtual-thread executor runs calls under the TimeLimiter. Only the Alpha bean exists so far.
**Human review / corrections:** The human chose the port change and the stop after Alpha. **AI defect found by the tests:** the HTTP read timeout was set equal to the TimeLimiter budget. The HTTP client sometimes timed out first, so the attempt failed as an I/O error, was classified `ERROR` and retried. A slow provider then took two full budgets (2.02 s for a 1 s budget) and was reported as `ERROR` instead of `TIMEOUT`. Fix: the read timeout is now a backstop at 2× the budget, and an `HttpTimeoutException` / `SocketTimeoutException` anywhere in the cause chain is classified as `TIMEOUT`. The IT asserts exactly one request on timeout. Separately, a 300 ms test timeout was too short for the first HTTP call in a fresh JVM (class loading); the tests now use 1 s. Verified: `AlphaProviderAdapterIT` passed 4 runs in a row; full `./mvnw -pl infrastructure -am verify` passes (219 domain, 36 application, 17 infrastructure unit tests, 20 ITs); ArchUnit 10/10.
**Contract ambiguity (Alpha):** nothing blocked the work. Two points are unspecified:
1. the `Content-Type` of the 400 and 500 bodies (the adapter does not depend on it);
2. whether Alpha may add fields to an itinerary (the adapter ignores unknown fields).

Proposed wording for `provider-contracts.md`, Alpha: "Error responses (`400`, `500`) are sent with `Content-Type: application/json`. Clients must ignore unknown fields in both success and error bodies."
**Artifacts:** `flight-aggregator/docs/domain-model.md`, `flight-aggregator/domain/src/main/java/io/github/jerojasol/flights/domain/model/ProviderResponse.java`, `flight-aggregator/domain/src/main/java/io/github/jerojasol/flights/domain/port/out/FlightProviderPort.java`, `flight-aggregator/domain/src/test/java/io/github/jerojasol/flights/domain/model/ProviderResponseTest.java`, `flight-aggregator/application/src/main/java/io/github/jerojasol/flights/application/AggregateFlightsService.java`, `flight-aggregator/application/src/test/java/io/github/jerojasol/flights/application/{AggregateFlightsServiceTest,fakes/StubProvider}.java`, `flight-aggregator/infrastructure/src/main/java/io/github/jerojasol/flights/infrastructure/provider/{ProviderResilience,ResilienceSettings}.java`, `flight-aggregator/infrastructure/src/main/java/io/github/jerojasol/flights/infrastructure/provider/alpha/**`, `flight-aggregator/infrastructure/src/main/java/io/github/jerojasol/flights/infrastructure/config/{ProviderConfiguration,ProviderProperties}.java`, `flight-aggregator/infrastructure/src/test/java/io/github/jerojasol/flights/infrastructure/provider/alpha/**`, `flight-aggregator/infrastructure/src/test/resources/fixtures/alpha/{contract-example,error-500,dead-legs}.json`, `flight-aggregator/prompts/infrastructure/03-provider-adapter.md`.

## 2026-11-14 — D3 (Alpha) follow-up: null entries, unmappable wording, invariant IDs

**Intent:** Fix the three MINOR `domain-guardian` findings on D3 (Alpha) before starting Beta, so Beta and Gamma inherit the corrected pattern.
**Prompt (summary):** Human approved the AI's recommended order:
1. Explicit null checks in `AlphaOfferMapper` (not `catch (RuntimeException)`), with a `null-entries.json` fixture and a test.
2. Carry the rule into the D3 prompt for Beta and Gamma.
3. Make `domain-model.md` §8 and §6 describe what "unmappable" really covers, with no per-reason breakdown.
4. Add invariant IDs R1, R2 (`ProviderResponse`) and PO1 (`ProviderOutcome`) to §3, and tag the tests.
**Generated:**
- `AlphaOfferMapper`: a null itinerary and a null segment entry are counted as unmappable and logged without dereferencing them. Still only `DomainException` / `DateTimeParseException` are caught. The null-segment check uses `stream().anyMatch(Objects::isNull)`, because `List.contains(null)` throws on immutable JDK lists.
- `fixtures/alpha/null-entries.json` and test `should_skip_and_count_null_entries_without_failing_the_response` (1 offer, 2 unmappable).
- `prompts/infrastructure/03-provider-adapter.md`:
  - The mapper returns the domain's `ProviderResponse`; the old `MappingResult` wording is gone.
  - One bad itinerary never fails the response, and nulls and structural problems are checked explicitly.
  - Catch only `DomainException` plus the format's parse exception (Beta `DateTimeException`, Gamma `NumberFormatException` / `DateTimeParseException`).
  - Fixtures: do not overwrite the exported `two-itineraries.*` / `empty.*`; each provider gets `dead-legs.*` and a malformed-entries fixture with a test.
- `domain-model.md`: the §8 `ProviderResponse` entry covers S1–S3 and malformed or missing fields; §6 says Segment failures go to `ProviderResponse.unmappable` with no per-reason counts; §3 adds R1, R2 and PO1.
- Tests: `// R1` / `// R2` tags in `ProviderResponseTest`, a `// PO1` tag in `AggregationResultTest`, and a new `ProviderOutcomeTest` (4 tests) for the parts of PO1 that had no test (negative rejected count, null status, null elapsed, zero values accepted).
**Human review / corrections:** The human approved all three findings and the order. The null-entry gap was an AI defect: the mapper's own Javadoc promised "never fails the whole response", but a single `null` in Alpha's array would have dropped every valid Alpha offer, with Alpha reported as `ERROR`. With the null checks removed temporarily, the new test failed with exactly that `NullPointerException`; the checks were then restored. While tagging, the AI found that PO1 was only partly tested (received count and elapsed only), hence `ProviderOutcomeTest`. Verified: `./mvnw -pl infrastructure -am verify` passes (223 domain, 36 application, 18 infrastructure unit tests, 20 ITs); `/check-arch` commands pass (ArchUnit 10/10). Not committed.
**Artifacts:** `flight-aggregator/infrastructure/src/main/java/io/github/jerojasol/flights/infrastructure/provider/alpha/AlphaOfferMapper.java`, `flight-aggregator/infrastructure/src/test/java/io/github/jerojasol/flights/infrastructure/provider/alpha/AlphaOfferMapperTest.java`, `flight-aggregator/infrastructure/src/test/resources/fixtures/alpha/null-entries.json`, `flight-aggregator/prompts/infrastructure/03-provider-adapter.md`, `flight-aggregator/docs/domain-model.md`, `flight-aggregator/domain/src/test/java/io/github/jerojasol/flights/domain/model/{ProviderResponseTest,AggregationResultTest,ProviderOutcomeTest}.java`.

## 2026-11-14 — D3 (Alpha) follow-up: unreadable prices are unmappable, not a failed response

**Intent:** Fix `domain-guardian`'s MINOR finding: an unreadable amount failed Alpha's whole response, although §8 says unparseable amounts count as unmappable.
**Prompt (summary):** Human chose option (a): keep the price as raw text in the DTO and parse it in the mapper. Add a fixture and test, and a prompt line so Beta and Gamma do the same.
**Generated:**
- `AlphaItinerary.price` is now a `String`; `AlphaOfferMapper` calls `Price.of(price, currency)` per itinerary, so a bad amount becomes `InvalidPriceException` and is counted as unmappable.
- `AlphaResponseParser` no longer needs `USE_BIG_DECIMAL_FOR_FLOATS`.
- `fixtures/alpha/malformed-values.json` (`"abc"`, `""` and one valid itinerary) with test `should_skip_and_count_itineraries_with_unreadable_price_without_failing_the_response` (1 offer, 2 unmappable).
- `should_keep_exact_decimal_text_when_price_is_a_json_number` (`1465.90` stays exact).
- `prompts/infrastructure/03-provider-adapter.md` §2: DTO fields hold raw strings; numbers, amounts, dates and timestamps are parsed in the mapper, never by the format parser.
**Human review / corrections:** The human chose option (a) over narrowing §8 (option b). This was an AI defect of the same kind as the null entries: timestamps were already kept raw, but the price was typed `BigDecimal`, so Jackson converted it while reading the whole body. With the old `BigDecimal` field restored temporarily, the new test failed with `InvalidFormat: Cannot deserialize value of type BigDecimal from String "abc"`; it was then restored. Note: a body that is not valid JSON, or a price that is a JSON object, still fails the whole response as `ERROR`; that is the format parser's job and the existing IT covers the non-JSON case. Verified: `./mvnw -pl infrastructure -am verify` passes (223 domain, 36 application, 20 infrastructure unit tests, 20 ITs); `/check-arch` commands pass (ArchUnit 10/10). Not committed.
**Artifacts:** `flight-aggregator/infrastructure/src/main/java/io/github/jerojasol/flights/infrastructure/provider/alpha/{AlphaItinerary,AlphaOfferMapper,AlphaResponseParser}.java`, `flight-aggregator/infrastructure/src/test/java/io/github/jerojasol/flights/infrastructure/provider/alpha/AlphaOfferMapperTest.java`, `flight-aggregator/infrastructure/src/test/resources/fixtures/alpha/malformed-values.json`, `flight-aggregator/prompts/infrastructure/03-provider-adapter.md`.

## 2026-11-14 — D3 (Beta): XML provider adapter

**Intent:** Implement the Beta provider adapter: XML over Jackson 3, local time plus IANA zone converted to instants, legs sorted by sequence, and the same resilience and mapping rules as Alpha.
**Prompt (summary):** `flight-aggregator/prompts/infrastructure/03-provider-adapter.md` for Beta: fixtures first; Jackson XML response DTOs in Beta's vocabulary; `BetaOfferMapper` returning `ProviderResponse`; `BetaClient` on `RestClient`; `BetaProviderAdapter` with the shared resilience; mapper and WireMock tests.
**Generated:**
- Fixtures (`fixtures/beta/`): `contract-example.xml` (the contract's example, verbatim), `error-500.xml` (the contract's Fault), `dead-legs.xml`, `malformed-entries.xml` (an itinerary without `Pricing`, one leg without `Flight`, one with the unknown zone `Mars/Olympus`, one valid itinerary). `two-itineraries.xml` and `empty.xml` are the exported files, which already have the trap (legs 2, 1, 3), so no separate trap file was needed.
- DTOs: `BetaSearchResponse` as records with `@JacksonXmlProperty` for elements and attributes. All values are raw strings (`BetaResponseParser` / `BetaOfferMapper` parse them). `BetaResponseParser` ignores unknown properties.
- Mapper: local time + IANA zone → `Instant` via `ZonedDateTime`; legs sorted by `sequence`, which must then read 1..n; `FlightNumber` from airline code + `Number`; price from `Total/@amount` and `Total/@currency`. Null entries and missing elements are checked explicitly.
- `ProviderRestClients`: the HTTP client construction was moved from `AlphaClient` into a shared helper, so all three providers share the same timeout backstop (2× budget).
- `BetaClient`, `BetaProviderAdapter`, and a `betaProvider` bean in `ProviderConfiguration`.
- Tests: `BetaOfferMapperTest` (8, including the trap, the contract example, the dead leg, the malformed entries, a sequence gap, and a missing root element) and `BetaProviderAdapterIT` (6, with WireMock).
**Decisions:**
- **Structural problems use a mapper-private `MalformedEntry`.** The prompt says to catch only the format's own parse exceptions alongside `DomainException`. Explicit checks throw `MalformedEntry`, and the mapper catches it with `DomainException | DateTimeException`. Nothing broader is caught.
- **A missing `Itineraries` element fails the whole response** (`IllegalStateException` → `ERROR`). This is a structure problem of the whole document, not one itinerary, so it does not count as unmappable.
- **Leg sequence must read 1..n with no gap or repeat.** The contract only says sequence is 1-based and may arrive out of order. A gap or repeat makes the itinerary ambiguous, so it is unmappable. This is an infrastructure decision; the domain model does not need to change.
- **Currency comes from `Total/@currency`.** The contract also has `Meta/Currency`. The adapter uses the price's own currency, since that is what `Price` needs.
**Human review / corrections:** The first run failed in an AI-written DTO: the root record component was named `itineraries`, but the element is `Itineraries`. Jackson XML matches names case-sensitively, so the whole body mapped as "no Itineraries element" (`IllegalStateException`) and 7 mapper tests failed. The fix was an explicit `@JacksonXmlProperty(localName = "Itineraries")`. Namespace matching by local name worked without extra configuration. One test expectation was also wrong: the arrival of `BA117` (13:55 New York) is 18:55Z, not 13:55Z, and the code computed it correctly. The expectation was corrected. Verified: `./mvnw -pl infrastructure -am verify` passes (223 domain, 36 application, 28 infrastructure unit tests, 26 ITs); `/check-arch` commands pass (ArchUnit 10/10).
**Contract ambiguities (Beta), not guessed:**
1. Whether `Itineraries/@count` must equal the number of `Itinerary` elements. The adapter ignores `count`.
2. Whether `Total/@currency` must equal `Meta/Currency`. The adapter uses `Total/@currency`.
3. Whether legs may have a gap in `sequence`. The contract says 1-based; the adapter treats a gap as malformed.
4. The `Content-Type` of the 400 and 500 Fault bodies (same gap as Alpha).

Proposed wording for `provider-contracts.md`, Beta section: "`Itineraries/@count` equals the number of `Itinerary` elements. `Leg/@sequence` values are exactly 1..n for each itinerary. `Total/@currency` equals `Meta/Currency`. Error responses (`400`, `500`) are sent with `Content-Type: application/xml`. Clients must ignore unknown elements and attributes."
**Artifacts:** `flight-aggregator/infrastructure/src/main/java/io/github/jerojasol/flights/infrastructure/provider/beta/**`, `flight-aggregator/infrastructure/src/main/java/io/github/jerojasol/flights/infrastructure/provider/ProviderRestClients.java`, `flight-aggregator/infrastructure/src/main/java/io/github/jerojasol/flights/infrastructure/provider/alpha/AlphaClient.java`, `flight-aggregator/infrastructure/src/main/java/io/github/jerojasol/flights/infrastructure/config/ProviderConfiguration.java`, `flight-aggregator/infrastructure/src/test/java/io/github/jerojasol/flights/infrastructure/provider/beta/**`, `flight-aggregator/infrastructure/src/test/resources/fixtures/beta/{contract-example,error-500,dead-legs,malformed-entries}.xml`.

## 2026-11-14 — D3 (Beta) follow-up: DTO names, daylight saving, contract breaches not retried

**Intent:** Fix the three MINOR `domain-guardian` findings on D3 Beta, and set the failure rules Gamma must follow.
**Prompt (summary):** Human approved all three findings and gave the details:
1. Rename the Beta DTOs to `BetaItinerary` / `BetaAirportRef`.
2. Daylight saving, option (b): a local time in a gap is malformed, an overlap keeps the earlier offset; mapper tests; the rule and the limitation recorded in the contract and in `SOLUTION.md` §6.
3. `ProviderContractViolation` for a 200 body that breaks the contract (Alpha and Beta): not retried, but recorded by the breaker; WireMock ITs asserting exactly one request.
4. The D3 prompt for Gamma lists the three failure kinds; a stream without `#END` is truncated, so it is a transport failure and stays retryable.
**Generated:**
- `BetaSearchResponse.BetaItinerary` and `BetaAirportRef`; Jackson `localName`s unchanged. `BetaOfferMapper` no longer imports a DTO named like the domain aggregate.
- `BetaOfferMapper.instant`: `getValidOffsets(local).isEmpty()` → `MalformedEntry` (unmappable); otherwise `ZonedDateTime.of(...).withEarlierOffsetAtOverlap()`. Mapper tests: `2026-03-29T02:30 Europe/Madrid` → unmappable; `2026-10-25T02:30 Europe/Madrid` → `2026-10-25T00:30:00Z`.
- `infrastructure.provider.ProviderContractViolation`, thrown by `AlphaResponseParser` and `BetaResponseParser` on a `JacksonException`, and by `BetaOfferMapper` when `Itineraries` is missing (it was an `IllegalStateException`). In `ProviderResilience`, the retry predicate excludes an `ERROR` whose cause is a `ProviderContractViolation`; the breaker still records it.
- ITs: Alpha non-JSON body; Beta without `Itineraries`; Beta unreadable XML. Each gives `ERROR` after exactly one request, with the violation as the cause.
- `mock-providers/docs/provider-contracts.md`, Beta conventions: the daylight-saving rule and its limitation. `docs/SOLUTION.md` §6: the same limitation under exact-key deduplication.
- `prompts/infrastructure/03-provider-adapter.md`:
  - the three failure kinds (one itinerary → unmappable; whole-body breach → `ProviderContractViolation`, not retried; transport → retried);
  - `MalformedEntry` named as an allowed catch;
  - retry only on transport `ERROR`s, reusing `ProviderResilience` and `ProviderRestClients`;
  - Gamma's missing `#END` is truncation, so `ERROR` after a retry (two requests).
**Human review / corrections:** The human approved all three findings and set the exact rules, including that the breaker still records contract breaches and that truncated Gamma streams stay retryable. Verified: with the retry exclusion removed temporarily, all three new ITs failed (`Expected exactly 1 requests … but received 2`); with the gap check removed temporarily, the DST gap test failed. Both were restored. Note on the process: the first regression check removed both fixes at once, and Surefire stopped on the mapper test before Failsafe ran, so the retry change was checked again on its own. Verified: `./mvnw -pl infrastructure -am verify` passes (223 domain, 36 application, 30 infrastructure unit tests, 27 ITs); `/check-arch` commands pass (ArchUnit 10/10). Not committed.
**Contract change:** the Beta section of `mock-providers/docs/provider-contracts.md` now documents how a consumer resolves daylight saving, and its limitation. The response format is unchanged, so `mock-providers` code and the exported fixtures are unaffected.
**Artifacts:** `flight-aggregator/infrastructure/src/main/java/io/github/jerojasol/flights/infrastructure/provider/{ProviderContractViolation,ProviderResilience}.java`, `flight-aggregator/infrastructure/src/main/java/io/github/jerojasol/flights/infrastructure/provider/alpha/AlphaResponseParser.java`, `flight-aggregator/infrastructure/src/main/java/io/github/jerojasol/flights/infrastructure/provider/beta/{BetaSearchResponse,BetaResponseParser,BetaOfferMapper}.java`, `flight-aggregator/infrastructure/src/test/java/io/github/jerojasol/flights/infrastructure/provider/{alpha/AlphaProviderAdapterIT,beta/BetaOfferMapperTest,beta/BetaProviderAdapterIT}.java`, `flight-aggregator/prompts/infrastructure/03-provider-adapter.md`, `mock-providers/docs/provider-contracts.md`, `docs/SOLUTION.md`.

## 2026-11-14 — D3 (Gamma): streamed CSV provider adapter

**Intent:** Implement the last provider adapter: Gamma's streamed CSV, read line by line without buffering, with the three failure kinds agreed after Beta.
**Prompt (summary):** `flight-aggregator/prompts/infrastructure/03-provider-adapter.md` for Gamma: fixtures first; no DTO tree but a line parser plus a streaming reader that requires `#END`; `GammaOfferMapper` (cents, `yyyyMMddHHmm` UTC, `|` then `;`); client, adapter on the shared resilience; mapper tests and a WireMock IT.
**Generated:**
- Fixtures (`fixtures/gamma/`):
  - `contract-example.csv`: the contract's example plus `#END`. It also serves as the trap fixture (`BA;0117`, `UX;0091`, a two-segment packed field).
  - `error-500.txt`: the contract's text, as `.txt` because Gamma's errors are `text/plain`.
  - `dead-legs.csv`.
  - `malformed-entries.csv`: wrong field count, non-numeric cents, bad timestamp, a segment with five attributes, a blank line, and one valid line.
  - `truncated-no-end-marker.csv`.
  - The exported `two-itineraries.csv` and `empty.csv` were kept.
- Code (`provider/gamma/`):
  - `GammaLine` (raw strings) and `GammaLineParser` (shape only; private `MalformedLine`).
  - `GammaStreamReader`: header check, data lines to a consumer, stop at `#END`; `TruncatedStream` when the stream ends early.
  - `GammaOfferMapper`, `GammaClient`, `GammaProviderAdapter`, and a `gammaProvider` bean.
- Tests: `GammaOfferMapperTest` (9) and `GammaProviderAdapterIT` (8, including a chunked stream via WireMock's `withChunkedDribbleDelay`).
**Decisions:**
- **Three failure kinds**, as agreed:
  - one bad line → unmappable;
  - wrong header, or data after `#END` → `ProviderContractViolation` (`ERROR`, not retried);
  - stream ended before `#END` → `TruncatedStream` (`ERROR`, retried). An I/O error while reading becomes Spring's `ResourceAccessException` (`ERROR`, retried).
- **An empty body is truncation, not a contract breach.** A stream cut before the header is the same transport failure as one cut before `#END`. Only a header that is present but wrong breaches the contract. This was the AI's first version wrong: `header == null` was a contract violation; it was corrected before the tests were written.
- **A blank line between data lines counts as one unmappable line** (wrong field count), consistent with "one bad entry never fails the response".
- **The client takes a reader callback instead of returning an `InputStream`.** The prompt suggested returning the stream, but then the connection either closes when `exchange` returns or stays open if a caller forgets to close it. The callback runs inside `exchange`, so the body is streamed and the connection always closes.
- Error bodies are read up to 2 KB, since a 500 body should never be large.
**Human review / corrections:** None yet. Verified:
- With truncation temporarily thrown as `ProviderContractViolation`, the truncated IT failed (it expects a retry); it was then restored.
- `GammaProviderAdapterIT` passed 3 runs in a row.
- `./mvnw -pl infrastructure -am verify` passes (223 domain, 36 application, 39 infrastructure unit tests, 35 ITs); `/check-arch` commands pass (ArchUnit 10/10). Not committed.

**Known limitation:** when the TimeLimiter abandons a slow export, the reading thread is not interrupted. It keeps reading until the stream ends or the backstop read timeout (2× budget) fires, so a very large export that keeps sending data can outlive its budget in the background, on a virtual thread.
**Contract ambiguities (Gamma), not guessed:**
1. The example in the contract has no `#END` line, though the conventions say `#END` marks a complete stream. The real mock sends it. The fixture follows the conventions and the mock.
2. "Empty result: header line only" does not mention `#END`; the mock sends a header then `#END`.
3. Whether a blank line may appear in the body.

Proposed wording for `provider-contracts.md`, Gamma section: add `#END` as the last line of the example; "Empty result: the header line, then `#END`."; "There are no blank lines; every line between the header and `#END` is one itinerary."
**Artifacts:** `flight-aggregator/infrastructure/src/main/java/io/github/jerojasol/flights/infrastructure/provider/gamma/**`, `flight-aggregator/infrastructure/src/main/java/io/github/jerojasol/flights/infrastructure/config/ProviderConfiguration.java`, `flight-aggregator/infrastructure/src/test/java/io/github/jerojasol/flights/infrastructure/provider/gamma/**`, `flight-aggregator/infrastructure/src/test/resources/fixtures/gamma/{contract-example.csv,error-500.txt,dead-legs.csv,malformed-entries.csv,truncated-no-end-marker.csv}`.

## 2026-11-14 — D3 (Gamma) follow-up: empty-result wording, blank lines, rejected requests, currency minor units

**Intent:** Fix the four MINOR `domain-guardian` findings on D3 Gamma. Two of them change all three providers.
**Prompt (summary):** Human approved all four, changing two of the AI's proposals:
1. Contract: an empty result is the header plus `#END`, and `#END` is added to the example.
2. Blank lines are skipped and not counted.
3. 4xx: a new `ProviderRequestRejected` (not `ProviderContractViolation`) for 4xx other than 408 and 429, in all three clients. Not retried, and ignored by the circuit breaker, because the provider is healthy and the fault is in our request. `ProviderContractViolation` stays for unreadable 200 bodies and is still recorded. 408, 429 and 5xx remain retryable. The 400 ITs assert exactly one request and an unchanged breaker failure count. A fourth failure kind goes into the prompt.
4. Currency scale from `getDefaultFractionDigits()`; a negative value (XXX, XAU) makes the line unmappable. Tests for JPY and XXX, and the 3-decimal rounding limitation in `SOLUTION.md` §6.
**Generated:**
- `mock-providers/docs/provider-contracts.md` (Gamma): `#END` closes the example; "Empty result: the header line followed by `#END`."
- `GammaStreamReader` skips blank data lines without counting them. The `malformed-entries.csv` test now expects 4 unmappable (it was 5).
- `infrastructure.provider.ProviderRequestRejected` and `ProviderRestClients.httpError(provider, status, body)`, now used by all three clients. In `ProviderResilience`: the retry predicate excludes it, `CircuitBreakerConfig.ignoreException(...)` ignores it, and a public `failedCalls()`. Each adapter has a package-private `circuitFailedCalls()` for the ITs.
- Tests:
  - The three 400 ITs (Alpha, Beta, Gamma): a 500 first (the breaker records 2), then a 400 → exactly 1 request, cause `ProviderRequestRejected`, failure count still 2.
  - `ProviderRestClientsTest` (parameterised): 400/401/403/404/422 are rejected; 408/429/500/502/503 stay retryable.
- `GammaOfferMapper.minorUnitDigits`: a non-ISO code or negative digits → `MalformedLine`. Tests: JPY `15000` → ¥15000; an XXX line is unmappable.
- `docs/SOLUTION.md` §6: the scale-2 rounding of 3-decimal currencies (KWD) and the dropping of pseudo-currencies.
- `prompts/infrastructure/03-provider-adapter.md`: "Four failure kinds"; the new kind is rejected requests (4xx other than 408/429); `Retry` excludes `ProviderRequestRejected`.
**Human review / corrections:** The human changed two of the AI's proposals:
- **4xx gets its own exception, `ProviderRequestRejected`,** instead of reusing `ProviderContractViolation`, because the two differ for the breaker. A broken body means the provider is unhealthy (recorded); a rejected request means our request was wrong (ignored).
- **Pseudo-currencies are unmappable** (the AI had only proposed using the currency's minor units).

Regression checks, each fix removed temporarily and then restored:
- without the breaker ignore, the three 400 ITs failed (`expected: 2`, `but was: 3`);
- without the retry exclusion, they failed (`received 2` requests);
- without blank-line skipping, the malformed-entries test failed;
- with the scale hard-coded to 2, the JPY and XXX tests failed.

Verified: `./mvnw -pl infrastructure -am verify` passes (223 domain, 36 application, 51 infrastructure unit tests, 35 ITs); `/check-arch` commands pass (ArchUnit 10/10). Not committed.
**Contract change:** wording only (the example and the empty-result line); the mock already sent `#END`, so its code and the exported fixtures are unaffected.
**Artifacts:** `flight-aggregator/infrastructure/src/main/java/io/github/jerojasol/flights/infrastructure/provider/{ProviderRequestRejected,ProviderRestClients,ProviderResilience}.java`, `flight-aggregator/infrastructure/src/main/java/io/github/jerojasol/flights/infrastructure/provider/{alpha/AlphaClient,alpha/AlphaProviderAdapter,beta/BetaClient,beta/BetaProviderAdapter,gamma/GammaClient,gamma/GammaProviderAdapter,gamma/GammaStreamReader,gamma/GammaOfferMapper}.java`, `flight-aggregator/infrastructure/src/test/java/io/github/jerojasol/flights/infrastructure/provider/{ProviderRestClientsTest,alpha/AlphaProviderAdapterIT,beta/BetaProviderAdapterIT,gamma/GammaProviderAdapterIT,gamma/GammaOfferMapperTest}.java`, `flight-aggregator/prompts/infrastructure/03-provider-adapter.md`, `mock-providers/docs/provider-contracts.md`, `docs/SOLUTION.md`.

## 2026-11-14 — D3 (Gamma) follow-up: lowercase currency codes

**Intent:** Fix `domain-guardian`'s last MINOR finding on Gamma: the three adapters disagreed on a lowercase currency code.
**Prompt (summary):** Human approved the reviewer's suggestion: normalise the code before the minor-units lookup, as `Price.of` does, and add a test with `"eur"`.
**Generated:** `GammaOfferMapper.toOffer` strips and uppercases (`Locale.ROOT`) the currency once, and uses that value both for `minorUnitDigits` and for `Price.of`. New test `should_accept_lowercase_currency_code_like_alpha_and_beta_do`.
**Human review / corrections:** The defect came from the previous follow-up: `minorUnitDigits` looked up the raw field before `Price.of` had normalised it, so `eur` was unmappable for Gamma but a valid EUR Offer for Alpha and Beta. With the normalisation removed temporarily, the new test failed; it was then restored. Verified: `./mvnw -pl infrastructure -am verify` passes (223 domain, 36 application, 52 infrastructure unit tests, 35 ITs); `/check-arch` commands pass (ArchUnit 10/10). This closes D3 for all three providers. Not committed.
**Artifacts:** `flight-aggregator/infrastructure/src/main/java/io/github/jerojasol/flights/infrastructure/provider/gamma/GammaOfferMapper.java`, `flight-aggregator/infrastructure/src/test/java/io/github/jerojasol/flights/infrastructure/provider/gamma/GammaOfferMapperTest.java`.

## 2026-11-14 — D4: Redis freshness adapter

**Intent:** Implement `FreshnessPort` on Redis: freshness marker, aggregation lock with owner-checked release, and STALE detection, with degraded behaviour when Redis is down.
**Prompt (summary):** `flight-aggregator/prompts/infrastructure/04-cache-freshness.md`: `RedisFreshnessAdapter` on `StringRedisTemplate` with `fresh`, `lock` and `seen` keys; `SET NX` locks; compare-and-delete release through a Lua script; `markFresh` writes both keys; `lastResult` reads back the stored result; degraded fallback when Redis is unreachable; `CacheConfiguration` with `aggregation.*` TTLs; an IT with an owner check after expiry; a key-builder unit test.
**Generated:**
- `cache/RedisFreshnessAdapter`, `cache/FreshnessKeys` (package-private key builder), `config/CacheConfiguration`, `config/AggregationProperties` (record bound to `aggregation.*`, validates both TTLs positive), and `lock-ttl: ${LOCK_TTL:30s}` in `application.yaml`.
- Awaitility as a test dependency (version managed by Spring Boot 4.1.2, 4.3.0).
- Tests: `FreshnessKeysTest` (2) and `RedisFreshnessAdapterIT` (6): MISSING→FRESH→STALE after the freshness TTL; a lock granted once and again after release; a lock freed when its TTL expires; the owner check; `lastResult` still returning the outcomes after the freshness TTL has expired; degraded behaviour against a closed port.
**Decisions:**
- **`seen` holds the result JSON; `fresh` holds the marker `1`.** The prompt had the reverse. The phase-B prompt says a STALE response takes its `lastOutcomes` from `freshness.lastResult`, and by then `fresh` has expired. Holding the JSON in `fresh` would lose the outcomes exactly when they are needed. So `seen` (7 days) holds the JSON, and `fresh` only says "recently aggregated".
- **STALE via `seen`**, as the prompt chose: the application did not change. `status`: `fresh` → FRESH; `lock` → REFRESHING; `seen` → STALE; otherwise MISSING. A lock is checked before `seen`, so a refresh already running on stale data reports REFRESHING.
- **`markFresh` writes `seen` before `fresh`.** If the second write fails, the route stays STALE and the next search refreshes it. The other order would report FRESH with no stored outcomes.
- **`LockToken` is the UUID alone.** The prompt wrote `new LockToken(criteria, uuid)`, but the domain record is `LockToken(String value)` (domain-model.md §8). `release` takes the criteria from its own signature, so the key is still derived correctly.
- **The IT builds the connection by hand** (`LettuceConnectionFactory` on the container's mapped port), not via `@ServiceConnection`: nothing needs a Spring context for this adapter.
**Human review / corrections:** The `DEL` mutation check was run because the compare-and-delete was the requirement that mattered most. With the Lua script replaced by a plain `DEL`, the owner test failed (`expected REFRESHING but was MISSING`): the expired owner removed the lock of the caller who had re-acquired it. The script was restored. Verified: `./mvnw -pl infrastructure -am verify` passes (223 domain, 36 application, 54 infrastructure unit tests, 41 ITs); `/check-arch` commands pass (ArchUnit 10/10). Not committed.
**Known limitations:** Redis outage is degraded, not failed: every search re-aggregates and nothing is shared between instances. This is written in the adapter's class comment; it goes into `SOLUTION.md` §6 with the next documentation pass. The `seen` key keeps a route's last result for 7 days, so a result can be served as STALE long after its TTL, which is what the phase-B STALE rule intends.
**Artifacts:** `flight-aggregator/infrastructure/src/main/java/io/github/jerojasol/flights/infrastructure/cache/{RedisFreshnessAdapter,FreshnessKeys}.java`, `flight-aggregator/infrastructure/src/main/java/io/github/jerojasol/flights/infrastructure/config/{CacheConfiguration,AggregationProperties}.java`, `flight-aggregator/infrastructure/src/main/resources/application.yaml`, `flight-aggregator/infrastructure/pom.xml`, `flight-aggregator/infrastructure/src/test/java/io/github/jerojasol/flights/infrastructure/cache/{FreshnessKeysTest,RedisFreshnessAdapterIT}.java`, `flight-aggregator/prompts/infrastructure/04-cache-freshness.md`.

## 2026-11-14 — D4 follow-up: fail closed on a Redis outage, corrupt seen values, seen TTL

**Intent:** Fix the two MAJOR and one MINOR `domain-guardian` findings on D4. The MAJOR one was a real defect in the AI's design: during a Redis outage every concurrent search became the lock holder and aggregated synchronously, multiplying provider traffic by the request rate.
**Prompt (summary):** Human approved the three recommendations:
1. Fail closed: with Redis unreachable, `status` returns STALE, `tryLock` returns empty, `lastResult` is empty. An IT with 64 concurrent searches through the real `SearchItinerariesService` asserts zero aggregator calls. Document the outage in `SOLUTION.md` §6 and the adapter Javadoc.
2. A corrupt `seen` value is discarded and the route reads MISSING, with no version field.
3. The `seen` TTL is `max(7 days, 2 × freshness TTL)`, computed in `markFresh`.
**Generated:**
- `RedisFreshnessAdapter`: fail-closed branches; `lastResult` captures `JacksonException | IllegalArgumentException | DomainException`, logs `outcome=CORRUPT`, deletes the key; `seenTtl(freshnessTtl)`; the class Javadoc rewritten.
- Tests (`RedisFreshnessAdapterIT`, now 11): the outage contract; the concurrent-search IT; two corrupt-value ITs (garbage JSON, a removed provider name); two `seen` TTL ITs (4-day and 10-minute freshness TTLs).
- `docs/SOLUTION.md` §6: the outage bullet rewritten.
**Decisions:**
- **STALE when Redis is down, not MISSING.** MISSING would make the application aggregate synchronously, which is the herd we are removing. STALE with no lock serves the repository and schedules nothing, so the application needed no change. The cost: a route never aggregated before reads as STALE with an empty page, which is a claim of data that does not exist. This was accepted explicitly, and the SOLUTION bullet says it.
- **Corrupt values are deleted, not kept.** A value that cannot be decoded is worse than none, because it makes every search for that route fail until the seen TTL. Deletion is not atomic with a concurrent `markFresh`; see the review question below.
- **No version field** in the stored shape, as the human chose. A future incompatible change would be handled the same way as corruption: discard and re-aggregate.
**Human review / corrections:** The MAJOR herd finding was correct, and it was the AI's own design. The degraded-mode prompt asked for "tryLock returns a token", and the AI followed it without checking the load it creates. Mutation checks: restoring the fail-open `tryLock` made the outage IT and the concurrent IT fail (the aggregator was called); removing the corrupt handling made both corrupt-value ITs fail. Both restored. Verified: `./mvnw -pl infrastructure -am verify` passes (223 domain, 36 application, 54 infrastructure unit tests, 46 ITs); `/check-arch` commands pass (ArchUnit 10/10). Not committed.
**Artifacts:** `flight-aggregator/infrastructure/src/main/java/io/github/jerojasol/flights/infrastructure/cache/RedisFreshnessAdapter.java`, `flight-aggregator/infrastructure/src/test/java/io/github/jerojasol/flights/infrastructure/cache/RedisFreshnessAdapterIT.java`, `docs/SOLUTION.md`, `flight-aggregator/prompts/infrastructure/06-web-and-operations.md`.

## 2026-11-14 — D4 follow-up: STALE contract in §8, compare-and-delete on discard

**Intent:** Fix the two MINOR `domain-guardian` findings on D4 after the outage change.
**Prompt (summary):** Human approved both: (1) document the fail-closed STALE in `domain-model.md` §8 and link it to `SOLUTION.md` §6; (2) discard a corrupt `seen` value only if it is still the corrupt one.
**Generated:**
- `domain-model.md` §8: one paragraph after the Freshness table stating that an unavailable store reports STALE, tryLock is empty, nothing is aggregated, and a never-aggregated route returns an empty page. It is marked as a deliberate exception and linked to `SOLUTION.md` §6.
- `RedisFreshnessAdapter.discard(key, corruptJson)` runs a compare-and-delete Lua script (`DISCARD_IF_UNCHANGED`) instead of an unconditional `DEL`.
- IT `should_keep_valid_result_written_after_corrupt_value_was_read`: a template hook writes a valid result between the read of the corrupt value and the discard; the valid result must survive.
**Decisions:** the compare-and-delete reuses the pattern already used for releasing the lock, so there is one idiom for "change only what I still own" in the adapter.
**Human review / corrections:** Both findings were correct. The discard race was a window of milliseconds, but the loss was silent, and it caused an unnecessary synchronous re-aggregation. Mutation check: with the unconditional `DEL` restored, the new IT failed; the Lua version was then restored. Verified: `./mvnw -pl infrastructure -am verify` passes (223 domain, 36 application, 54 infrastructure unit tests, 47 ITs); `/check-arch` commands pass (ArchUnit 10/10). Not committed.
**Artifacts:** `flight-aggregator/docs/domain-model.md`, `flight-aggregator/infrastructure/src/main/java/io/github/jerojasol/flights/infrastructure/cache/RedisFreshnessAdapter.java`, `flight-aggregator/infrastructure/src/test/java/io/github/jerojasol/flights/infrastructure/cache/RedisFreshnessAdapterIT.java`.

## 2026-11-14 — D5: Web API (search, admin aggregation, cursor codec, Problem Details)

**Intent:** Expose the search and admin aggregation over HTTP, with request validation, an opaque keyset cursor, RFC 9457 Problem Details, and light OpenAPI docs, keeping controllers dependent only on inbound ports.
**Prompt (summary):** `flight-aggregator/prompts/infrastructure/05-web-api.md`: `GET /api/itineraries` and `POST /api/admin/aggregations`; a validated request record; response DTOs; `CursorCodec`; `ItineraryResponseMapper`; two controllers; `ApiExceptionHandler` (400/422/500); springdoc annotations; `@WebMvcTest` tests, a codec test, an aggregation controller test.
**Generated:**
- `web/`: `ItineraryController`, `AggregationController`, `ItinerariesApi`, `AggregationsApi`, `ItinerarySearchRequest`, `AggregationRequest`, `SearchRequestMapper`, `CursorCodec`, `InvalidCursorException`, `ApiExceptionHandler`; `web/dto/` (6 records); `web/mapper/ItineraryResponseMapper`.
- `config/WebConfiguration` (the `SearchRequestMapper` bean); `config/AggregationProperties.referenceCurrency` and `reference-currency: ${REFERENCE_CURRENCY:EUR}` in `application.yaml`.
- Tests: `ItineraryControllerTest` (11), `AggregationControllerTest` (2), `CursorCodecTest` (9), all with AssertJ (`MockMvcTester`), no Hamcrest.
**Decisions:**
- **Lowercase codes are accepted and normalised** (the prompt asked to decide): validation is `[A-Za-z]{3}` / `[A-Za-z0-9]{2}`, and `Airport.of` / `Carrier.of` uppercase. `origin=mad` means MAD; `origin=MADX` is 400.
- **OpenAPI annotations live on interfaces (`ItinerariesApi`, `AggregationsApi`) that the controllers implement.** ArchUnit rule 5 allows controllers to depend only on inbound ports, the domain model, `web`, Spring, `jakarta.validation` and `java`; `io.swagger` is not in that list. Verified: `@Operation` placed directly on the controller made rule 5 fail.
- **The cursor is `<amount>|<currency>|<itineraryKey>`**, not `<price>|<id>`: `PageCursor` holds a full `Price` (D2's currency check needs it), and pages are ordered by the Itinerary Key, not a database id. The cursor is opaque but not signed; a crafted one can only move its own caller within the results.
- **`maxPrice` is in a new `aggregation.reference-currency`** (default EUR). Nothing configured a currency before; `OfferValidator` takes one in its constructor, so the same property will configure it when the use cases are wired.
- **The admin endpoint answers 200 with the result**, not 202 (the prompt left it open; 200 is simpler for the demo).
- **422 for `DomainException`, 400 for request shape.** Origin equal to destination passes validation (two valid codes) and is rejected by `SearchCriteria`, so it is 422.
- **500 hides the exception:** the body has a `correlationId` and a generic detail; the exception and id are logged.
- **Type-conversion errors read "has an invalid value"** instead of Spring's conversion message, which exposes Java type names.
**Human review / corrections:** None yet. One compile error in the AI's test (`getContentAsString` declares `UnsupportedEncodingException`), fixed. Verified: `./mvnw -pl infrastructure -am verify` passes (223 domain, 36 application, 76 infrastructure unit tests, 47 ITs); `/check-arch` commands pass (ArchUnit 10/10; rules 4 and 5 included).
**Known gaps:**
- **The application does not start yet:** no configuration wires `SearchItinerariesService` / `AggregateFlightsService` (and `OfferValidator`, `ItineraryFactory`, `MarkupPolicy`). The web tests are slices with mocked use cases.
- **OpenAPI is not verified at runtime** for the same reason: springdoc is not part of the `@WebMvcTest` slice, and the full context cannot start.
- **The admin endpoint calls `AggregateFlightsUseCase` directly:** it neither takes the aggregation lock nor marks the route fresh. Sent to `domain-guardian` as a question.
**Artifacts:** `flight-aggregator/infrastructure/src/main/java/io/github/jerojasol/flights/infrastructure/web/**`, `flight-aggregator/infrastructure/src/main/java/io/github/jerojasol/flights/infrastructure/config/{WebConfiguration,AggregationProperties}.java`, `flight-aggregator/infrastructure/src/main/resources/application.yaml`, `flight-aggregator/infrastructure/src/test/java/io/github/jerojasol/flights/infrastructure/web/**`, `flight-aggregator/prompts/infrastructure/05-web-api.md`.

## 2026-11-14 — D5 follow-up: on-demand refresh use case, domain errors as 500, foreign-currency cursor

**Intent:** Fix the MAJOR and two MINOR `domain-guardian` findings on D5. The MAJOR one was that the admin endpoint aggregated without the lock and without marking the route fresh, so a manual refresh was repeated by the next search.
**Prompt (summary):** Human approved the AI's recommendations:
1. A new inbound use case `RefreshItinerariesUseCase` (`Optional<AggregationResult> refresh(SearchCriteria)`), in `domain-model.md` §8 first, implemented as tryLock → aggregate → markFresh if any provider succeeded → release in a `finally`; the admin controller answers 409 when it is empty; the Redis-down consequence goes into `SOLUTION.md` §6.
2. Wrap `DomainException` only while mapping the request (search and admin body), as a web `InvalidRequestException` → 422; any other `DomainException` → 500 with a correlation id and no domain message.
3. Reject a cursor in another currency with 400.
4. Keep the JSON field name `providers`.
**Generated:**
- `domain-model.md` §8: `RefreshItinerariesUseCase` and a paragraph on its lock and freshness rules.
- `domain/port/in/RefreshItinerariesUseCase`; `application/RefreshItinerariesService` with `RefreshItinerariesServiceTest` (5).
- `web/`: `InvalidRequestException` (422), `AggregationInProgressException` (409). `SearchRequestMapper` wraps domain failures in `criteria` and `filter`, and checks the cursor currency. `AggregationController` uses `RefreshItinerariesUseCase`. `ApiExceptionHandler` drops the blanket `DomainException` handler and adds 422 and 409 handlers. `AggregationsApi` documents 409 and 422.
- Tests:
  - `ItineraryControllerTest`: the old "use case throws DomainException → 422" test is replaced by "port throws `InvalidItineraryException` → 500 without the domain message", because the human changed the rule; a USD cursor test is added.
  - `AggregationControllerTest`: a 409 test and a 422 body test are added.
- `docs/SOLUTION.md` §6: with Redis down, a manual refresh also answers 409.
**Decisions:**
- An aggregation exception in `refresh` propagates after the lock is released; the search path swallows it to keep serving data. An operator who asked for a refresh should see that it failed, as a 500 with a correlation id.
- `Optional` is used only as a return type, within AGENTS.md §6.
**Human review / corrections:** The MAJOR finding came from the AI's D5 design: the prompt said "inject only `AggregateFlightsUseCase`", and the AI followed it without checking the lock and freshness rules in §8. The human approved adding a domain port to fix it. Mutation checks: with a blanket `DomainException → 422` handler restored temporarily, the new 500 test failed; without the cursor currency check, the USD cursor test failed. Both restored. Verified: `./mvnw -pl infrastructure -am verify` passes (223 domain, 41 application, 79 infrastructure unit tests, 47 ITs); `/check-arch` commands pass (ArchUnit 10/10). Not committed.
**Artifacts:** `flight-aggregator/docs/domain-model.md`, `flight-aggregator/domain/src/main/java/io/github/jerojasol/flights/domain/port/in/RefreshItinerariesUseCase.java`, `flight-aggregator/application/src/main/java/io/github/jerojasol/flights/application/RefreshItinerariesService.java`, `flight-aggregator/application/src/test/java/io/github/jerojasol/flights/application/RefreshItinerariesServiceTest.java`, `flight-aggregator/infrastructure/src/main/java/io/github/jerojasol/flights/infrastructure/web/{InvalidRequestException,AggregationInProgressException,SearchRequestMapper,AggregationController,AggregationsApi,ApiExceptionHandler}.java`, `flight-aggregator/infrastructure/src/test/java/io/github/jerojasol/flights/infrastructure/web/{ItineraryControllerTest,AggregationControllerTest}.java`, `docs/SOLUTION.md`.

## 2026-11-14 — D6: wiring, startup checks, scheduler, health, Dockerfile

**Intent:** Make the service start: wire domain and application explicitly, add scheduled ingestion and health, build the container, and prove the whole service with one smoke test, including a Redis outage.
**Prompt (summary):** `flight-aggregator/prompts/infrastructure/06-wiring-and-boot.md`, as written by the human, plus 12 gaps the AI found by checking the prompt against the code and the D5 review notes, appended to the prompt before execution with the human's instruction to do so.
**Gaps added to the prompt:**
1. Lettuce's 60 s default command timeout.
2. Hikari's 30 s default startup wait.
3. `providers.timeout` must be shorter than the fan-out budget.
4. The refresh executor needs a bounded queue, not only bounded threads.
5. One `Clock` and one set of TTLs.
6. Exactly one adapter per Provider, failing fast if one is missing.
7. Scheduler details: `@EnableScheduling`, per-route catch, UTC dates, route validation, disabled in tests.
8. `warmIntervalMinutes` vs a Duration.
9. The outcome logger as a decorator.
10. Health groups and details.
11. ArchUnit rule 7 for the breaker health indicators.
12. Graceful shutdown.

Also flagged: there is no root `docker-compose.yml`, which `../AGENTS.md` requires; it is out of scope for this project.
**Generated:**
- `config/UseCaseConfiguration`, `config/SchedulingConfiguration`; `config/AggregationProperties` rewritten with every startup check; `config/ProviderConfiguration` (concrete adapter types, one health indicator per provider); `config/CacheConfiguration` (Lettuce rejects commands while disconnected).
- `observability/ProviderOutcomeLogger`, `scheduling/WarmRoutesScheduler`, `provider/ProviderCircuitState`, `provider/ProviderCircuitHealthIndicator`; adapters and `ProviderResilience` expose `circuitState()`.
- `application.yaml` (aggregation properties, timeouts, actuator exposure and groups, graceful shutdown, logging) and `application-test.yaml` (no warm routes).
- `Dockerfile` and `.dockerignore`.
- Tests: `AggregationPropertiesTest` (6), `WarmRoutesSchedulerTest` (3), `FlightAggregatorApplicationIT`.
- `docs/SOLUTION.md` §6: no refresh path at all during a Redis outage; no authentication covers the admin endpoint and actuator.
**Decisions:**
- **Lettuce rejects commands while disconnected** (`DisconnectedBehavior.REJECT_COMMANDS`). Found while writing the smoke test: by default Lettuce queues commands during a disconnect and each waits for the command timeout, so a search with Redis down would have taken about 1.5 s of Redis waits even with a 500 ms timeout. It now fails at once and the adapter degrades immediately.
- **The outcome logger is a decorator** of `AggregateFlightsUseCase`, so every path (cold miss, background refresh, admin refresh, scheduler) is logged once and `application` stays free of logging.
- **Provider health is always UP**, with `circuit` and `degraded` details, so overall health never goes DOWN because a provider is down.
- **The scheduler uses `RefreshItinerariesUseCase`**: same lock as searches and admin refreshes, so it never races them.
- **Plain jar in the image**, not layered extraction, which was not needed for this delivery.
**Human review / corrections:** The human asked the AI to look for wiring gaps, add them to the prompt and execute it. AI errors during execution:
- `spring.datasource.hikari.connection-timeout: 5s` failed binding; Hikari binds a `long` in milliseconds. Fixed to `5000`.
- The Lettuce queuing behaviour was not in the AI's first list of gaps; the smoke test's timing assertion made it visible.

Verified:
- `./mvnw verify` passes (223 domain, 41 application, 88 infrastructure unit tests, 48 ITs); `/check-arch` passes (ArchUnit 10/10).
- Smoke IT story: cold search FRESH with 3 deduplicated itineraries from 3 providers; Redis stopped → liveness UP, `/actuator/health` 503, search 200 STALE in under 2 s, admin refresh 409 with no provider call.
- `docker build -t flight-aggregator .` succeeds; without Postgres the container fails in 5 s with "Connection to 127.0.0.1:1 refused", running as uid 10001.

Not committed.
**Known gaps:** no root `docker-compose.yml` (repository-level step); no authentication on admin and actuator; the aggregation-under-lock sequence still exists in three places (D5 MINOR, deferred).
**Artifacts:** `flight-aggregator/prompts/infrastructure/06-wiring-and-boot.md`, `flight-aggregator/infrastructure/src/main/java/io/github/jerojasol/flights/infrastructure/{config,observability,scheduling,provider}/**`, `flight-aggregator/infrastructure/src/main/resources/application.yaml`, `flight-aggregator/infrastructure/src/test/resources/application-test.yaml`, `flight-aggregator/infrastructure/src/test/java/io/github/jerojasol/flights/infrastructure/{FlightAggregatorApplicationIT,config/AggregationPropertiesTest,scheduling/WarmRoutesSchedulerTest}.java`, `flight-aggregator/Dockerfile`, `flight-aggregator/.dockerignore`, `docs/SOLUTION.md`.

## 2026-11-14 — D7: end-to-end test (closes Phase D)

**Intent:** One integration test that reads like documentation of the whole system: aggregation, deduplication, markup, keyset paging, filtering, a provider failure during a background refresh, cheapest-wins across runs, and a manual refresh.
**Prompt (summary):** `flight-aggregator/prompts/infrastructure/07-end-to-end.md`: `EndToEndIT` with `@SpringBootTest(RANDOM_PORT)`, test profile, Postgres and Redis containers, one WireMock for the three providers, and six steps in one `@Test`, under ~30 s.
**Generated:**
- `EndToEndIT` (one test, 6 commented steps, helpers grouped as Steps / HTTP / Providers / JSON) using Spring's `RestTestClient` and AssertJ.
- A test-only fixture `fixtures/beta/overlap-plus-beta-only.xml`: Beta's exported two itineraries plus IB6251, sold by Beta only.
**Decisions:**
- **A Beta-only itinerary was added.** The exported `two-itineraries.*` fixtures carry the same two itineraries for all three providers, so step 4's "Beta-only itineraries still present" could not be asserted with them. The new fixture keeps the overlap (7 offers → 3 itineraries) and adds one itinerary only Beta sells. The exported fixtures were not changed.
- **Freshness is expired by deleting the `fresh:*` key**, not by a 2 s TTL. D6's startup checks require `freshness-ttl > lock-ttl ≥ 2 × provider-budget` (7 s at least), so the prompt's first option is rejected at boot; the prompt's alternative was used.
- **Expected values are written out** (377.09, 544.95, 1471.07; GAMMA, BETA, GAMMA), not computed, so the test documents the outcome. The markup is still checked as `final = base × 1.05` (HALF_EVEN) for every item.
- **Step 5 forces the refresh through the stale path** (delete the marker, search, wait for FRESH), so step 6 is the only manual refresh. Step 6 asserts `persisted = 0`: nothing cheaper arrived, so the upsert rewrote nothing.
**Human review / corrections:** None yet. The test passed on the first run, so a vacuous pass was ruled out by what it asserts:
- step 4's provider statuses (BETA ERROR, ALPHA and GAMMA OK) prove the background refresh ran;
- step 5's change of source to ALPHA at 315.00 can only come from a refresh;
- step 6's `persisted = 0` fails if the upsert rewrote rows unconditionally.

Verified: `EndToEndIT` passed 3 runs in a row, about 10 s each; `./mvnw verify` passes (223 domain, 41 application, 88 infrastructure unit tests, 49 ITs). Not committed.
**Phase D status:** closed. Still open at repository level: no root `docker-compose.yml`. Two startup checks were left for the human to decide (D6 notes: retry vs fan-out budget; warm interval vs freshness TTL). The aggregation-under-lock sequence is duplicated in three places (D5 MINOR, deferred).
**Artifacts:** `flight-aggregator/infrastructure/src/test/java/io/github/jerojasol/flights/infrastructure/EndToEndIT.java`, `flight-aggregator/infrastructure/src/test/resources/fixtures/beta/overlap-plus-beta-only.xml`, `flight-aggregator/prompts/infrastructure/07-end-to-end.md`.

## 2026-11-14 — Phase D accepted: retry vs budget documented, warm-interval check, lock-sequence debt recorded

**Intent:** Close the three items left open after D7, as the human decided.
**Prompt (summary):** The human accepted Phase D, including the two D7 deviations, and decided:
1. Retry vs fan-out budget: no validation and no change to defaults. Document it in `SOLUTION.md` §6, and add "budget-aware retry" to §7.
2. Add a startup check that `warm-interval ≥ freshness-ttl`, with a test.
3. Do not refactor the aggregation-under-lock sequence; record it in §7 as technical debt.
**Generated:**
- `docs/SOLUTION.md` §6: a retry started late is abandoned by the fan-out; the provider is reported as TIMEOUT rather than ERROR; the abandoned retry still calls it; its offers arrive with the next refresh; data stays correct.
- `docs/SOLUTION.md` §7 (first entries under the template line): "Budget-aware retry", and the known technical debt that the lock sequence is written three times, with why the copies differ.
- `AggregationProperties`: `warm-interval ≥ freshness-ttl`, with a clear message.
- `AggregationPropertiesTest`: 3 new tests (shorter interval with routes fails; equal is accepted; no routes, not checked).
**Decisions:** **The interval check applies only when `warm-routes` is set.** With the scheduler disabled the interval is unused; checking it anyway would make startup fail when someone raises `FRESHNESS_TTL` without touching `WARM_INTERVAL`, with nothing actually wrong. This narrows the human's instruction slightly and is reported for confirmation.
**Human review / corrections:** All three outcomes decided by the human. Mutation check: with the new check removed temporarily, the "shorter interval with routes" test failed; it was then restored. Verified: `./mvnw verify` passes (223 domain, 41 application, 91 infrastructure unit tests, 49 ITs). Not committed, per the human's instruction.
**Artifacts:** `docs/SOLUTION.md`, `flight-aggregator/infrastructure/src/main/java/io/github/jerojasol/flights/infrastructure/config/AggregationProperties.java`, `flight-aggregator/infrastructure/src/test/java/io/github/jerojasol/flights/infrastructure/config/AggregationPropertiesTest.java`.

## 2026-11-14 — F1: root Docker Compose for the backend stack

**Intent:** Boot postgres, redis, mock-providers and flight-aggregator with one `docker compose up --build`, plus a smoke test.
**Prompt (summary):** `prompts/compose/01-backend-stack.md`: four services with fixed names, healthy-only `depends_on`, aggregator health on liveness (not aggregate), Postgres named volume, no host ports for Postgres/Redis, every tunable passed as `${NAME:-default}`, `.env.example`, `.dockerignore` checks, `scripts/smoke.sh`, root `AGENTS.md` commands.
**Generated:**
- `docker-compose.yml`: env names and defaults taken from both `application.yaml` files (includes `WARM_ROUTES`, `WARM_INTERVAL`). Mock healthcheck inherited from its Dockerfile. The aggregator's Dockerfile already probed `/actuator/health/liveness`, so it was not changed; compose restates the same probe only to raise `retries` to 6 (start period 40 s).
- `.env.example` grouped by service; the stack boots with no `.env` (none existed, so every acceptance run was without one).
- Both `.dockerignore` files lacked `.git`; added.
- `scripts/smoke.sh` (liveness wait, first page with 3 retries printing provider statuses, cursor page with no repeated ids, markup check, 400 problem+json).
**Human review / corrections:** Compose file accepted. Four corrections from the human:
1. **Provider timeout too short (AI error).** I copied `PROVIDER_TIMEOUT=3s` / `PROVIDER_BUDGET=3500ms` from `application.yaml` without checking them against the brief's latency (100 ms – 5 s, uniform in the mock). A 3 s timeout cuts off about 40% of calls that would have succeeded; with the 10% error rate, the breakers (50% over 10 calls) opened within minutes. The human's logs showed 15 OK against 13 TIMEOUT, and a new route returned zero items with all three providers CIRCUIT_OPEN. My first acceptance run (one smoke search plus one Redis check) was too short to show it. Defaults changed to `5500ms` / `6s` in `application.yaml`, `docker-compose.yml` and `.env.example`. Startup checks still hold: timeout is below budget, and `LOCK_TTL` 30 s is at least twice the budget. No test depends on the yaml defaults. The trade-off is recorded in `SOLUTION.md` §6: the first search of a new route can take up to 6 s, and routes already aggregated are served from Postgres. The "defaults" numbers in the existing retry-vs-budget paragraph were updated.
2. **`.env.example` comment wrong (AI error).** `REFERENCE_CURRENCY` was described as "currency all prices are converted to". There is no FX: offers quoted in any other currency are rejected. Fixed.
3. **Smoke markup check too strict (AI error).** The domain rounds HALF_EVEN and the script rounds half up, so exact ties differ by one cent. The check now accepts a difference of up to one cent.
4. **Branch.** A feature branch was required before any commit. The work was already on `feat/create-compose`, not on `develop`, so no new branch was created.

Verification after the corrections: `./mvnw verify` passed (406 tests including 49 ITs, 0 failures). Then `docker compose down -v && docker compose up --build -d` and a 5-minute wait. Aggregator log outcomes: **OK 20, TIMEOUT 1, ERROR 0, CIRCUIT_OPEN 0**. This covered the single warm-routes run at startup (3 routes × 2 dates; the interval is 10 min) plus the smoke search. The one TIMEOUT was BETA on MAD-LHR 2026-11-15, logged with `elapsed_ms=600`. Smoke test: all PASS. A route never requested before (LIS-FCO, 2026-12-03) returned HTTP 200 in 5.9 s, with 5 items, `FRESH` and all three providers OK. Not committed.
**Artifacts:** `docker-compose.yml`, `.env.example`, `scripts/smoke.sh`, `AGENTS.md`, `flight-aggregator/.dockerignore`, `mock-providers/.dockerignore`, `flight-aggregator/infrastructure/src/main/resources/application.yaml`, `docs/SOLUTION.md`, `docs/ai-log.md`.

## 2026-11-14 — G1: frontend scaffold, AGENTS.md and typed API client

**Intent:** Scaffold `frontend/` (Vite + React + TS strict) with a typed, same-origin API client and formatting helpers.
**Prompt (summary):** `frontend/prompts/01-scaffold-and-api-client.md`: fixed dependency allow-list at current npm versions, `engines.node >=24 <25`, Vite proxy for `/api` (no CORS, no runtime base URL), types mirroring the backend DTOs, `ApiError` with Problem Details, `formatDuration`/`formatPrice`/`formatUtc` (UTC only), QueryClient with `retry: false`, project `AGENTS.md`, tests with stubbed `fetch`.
**Generated:**
- Scaffold: the template was generated in a temp dir with `npm create vite` (`.gitignore`, `index.html`, `tsconfig.json`, favicon copied over); everything else was written by hand. DTO types were checked against the Java records and against a live `/api/itineraries` response. `POST /api/admin/aggregations` was taken from `AggregationController`.
- `src/api/{types,client}.ts`: query built with `URLSearchParams`, skipping undefined and empty values. Network failures and non-problem error bodies become `ApiError` with `problem: null`. `AbortError` is rethrown so React Query cancellation still works. No client timeout.
- `src/format.ts`, `src/main.tsx`, `src/App.tsx` (placeholder), `src/styles.css`, `frontend/AGENTS.md`.
- Tests: 5 for the client (including a non-JSON 502) and 6 for formatting.
**Human review / corrections:** Two conflicts with the prompt came up, and the human chose how to resolve them:
1. The current Vite react-ts template ships **oxlint**, not ESLint. The human kept ESLint with the classic Vite `eslint.config.js` (`@eslint/js`, `typescript-eslint`, react-hooks, react-refresh, `globals`).
2. The latest **TypeScript is 7.0.2**, but `typescript-eslint` 8.72.2 only supports `<6.1`. The human chose `typescript ~6.0.3`; the pin and the reason are recorded in `frontend/AGENTS.md`.

Other notes: `@testing-library/dom` (a peer of `@testing-library/react`) and `@types/node` `^24` were added as implied dependencies. `npm audit` reports 7 moderate advisories, all in the dev-only `typescript-eslint` chain, and they were left as is. Verified: `node -v` = v24.22.3; `npm ci`, lint, 11 tests and build all passed. Through the Vite proxy, `/api/itineraries?...&limit=1` returned `"FRESH"`, and an invalid origin returned `application/problem+json`. Not committed.
**Artifacts:** `frontend/` (package.json, package-lock.json, vite.config.ts, eslint.config.js, tsconfig*.json, index.html, src/**, AGENTS.md), `README.md`, `docs/ai-log.md`.

## 2026-11-14 — G2: search form, results list and keyset "load more"

**Intent:** Build the one-page search UI: URL-driven search state, form, results with cursor pagination, and every loading and error state.
**Prompt (summary):** `frontend/prompts/02-search-and-results.md`: `searchState.ts` (URLSearchParams, `pushState`, `popstate`, shareable links); `SearchForm` with client validation that mirrors the backend; `useItinerarySearch` built on `useInfiniteQuery` (key `["itineraries", params]`, `limit: 20`, metadata from the first page); `ResultsList`/`ItineraryCard`; idle, first-load, empty, 400, other-error and load-more-error states; no stale overwrite; 8 required tests.
**Generated:**
- `src/searchState.ts`, `src/useItinerarySearch.ts`, `src/components/{SearchForm,ResultsList,ItineraryCard}.tsx`, the rewritten `src/App.tsx`, and styles. Client validation regexes were copied from `ItinerarySearchRequest.java` (`[A-Za-z]{3}`, `[A-Za-z0-9]{2}`, `@Positive`), plus a client-only check that origin ≠ destination.
- On `popstate`, the form is remounted with a new `key` so it refills from the URL. Problem `errors` keys that match a form field go to that field; other keys, or a 400 with no `errors`, go to a summary above the form. The "Load more" button becomes "Retry" after a load-more error, and the items already shown stay.
- `src/App.test.tsx` has 11 tests: the 8 required ones, plus the idle hint, load-more error with retry, and a correlation id. The race test holds the first (MAD-JFK) response open, switches to BCN-LHR with back/forward navigation, then releases the old response.
**Human review / corrections:** Accepted. Notes:
1. The first test run failed 8 tests because Testing Library's automatic cleanup only registers with Vitest globals, which are off. I added `cleanup()` in `src/test/setup.ts`.
2. Mutation check: with `params` temporarily removed from the query key, the race test failed. Restored.
3. The race test switches searches with `popstate` instead of a second form submit, because the Search button is disabled while the first page is loading (as the prompt requires).
4. The live acceptance check used `curl` through the Vite proxy with the same requests the UI sends, because no browser was available in the session. It was not a click-through in a browser and not React Query devtools. MAD→JFK 2026-11-20: page 1 had 20 items (`FRESH`); the cursor page had 20 more; 40 unique ids, non-decreasing prices. `maxPrice=1`: 0 items, `nextCursor: null`, which the UI shows as the empty state with the filters ("No itineraries found", "MAD → JFK, 2026-11-20, max €1").

Verified: lint clean, 22 tests passed, build OK. Not committed.
**Artifacts:** `frontend/src/App.tsx`, `frontend/src/App.test.tsx`, `frontend/src/searchState.ts`, `frontend/src/useItinerarySearch.ts`, `frontend/src/components/`, `frontend/src/styles.css`, `frontend/src/test/setup.ts`, `docs/ai-log.md`.

## 2026-11-14 — G3: freshness badge, provider panel and live refresh

**Intent:** Make the dashboard show how fresh the data is, report each provider's outcome, and follow background refreshes live with capped polling and a manual refresh.
**Prompt (summary):** `frontend/prompts/03-freshness-and-providers.md`: `FreshnessBadge` (text per state, relative age every 30 s, UTC `title`); `ProviderPanel` (always ALPHA/BETA/GAMMA, fixed wording for statuses and for the partial and none-answered sentences); `useFreshnessPolling` (every 2 s, `limit: 1`, key `["freshness", params]`, stops on FRESH, on a search change, while the tab is hidden, or after 15 attempts); on FRESH, refetch silently with one page loaded or show a "Reload" bar with more; "Refresh now" with 409 treated as busy plus polling; two-column layout that stacks on narrow screens; 8 required tests with fake timers.
**Generated:**
- `src/useFreshnessPolling.ts`: polling driven by `setTimeout` and `queryClient.fetchQuery` on `["freshness", params]`, not React Query's `refetchInterval`, so the first poll happens 2 s after the first page and not immediately. A failed poll still counts toward the cap. Tab visibility comes from `useSyncExternalStore` on `visibilitychange`. The fresh callback uses `useEffectEvent`, so loading more pages does not restart the 2 s timer.
- `src/components/SearchResults.tsx` is mounted with `key` = the serialised search. A new search therefore drops all polling and refresh state, and the cleanup clears the timer and cancels the in-flight poll. "Refresh now" uses `useMutation`. "Reload" trims the infinite query to page 1 with `setQueryData` and refetches, so there is no loading flash. Results now stay rendered whenever data exists (`pageCount > 0`). This matters because in TanStack v5 a failed load-more sets `status: 'error'`.
- `src/components/{FreshnessBadge,ProviderPanel}.tsx`, the badge slot in `ResultsList`, and the layout and status styles. Every status has a text label plus a marker character, so colour is never the only signal.
- `src/freshness.test.tsx` has 11 tests: the 8 required ones, plus "none answered", a 409 → busy message → polling, and another failure shown inline with the list kept.
**Human review / corrections:** Accepted. Notes:
1. ESLint's react-refresh rule rejected an exported helper from a component file. The helper was made private.
2. Mutation checks: with `MAX_POLLS` at 16, the 15-poll test failed; with the "only one page loaded" rule removed, the Reload test failed. Both were restored.
3. Live acceptance used `curl` through the Vite proxy with the UI's own requests, because there was no browser in the session. Observations:
   - **Step 1:** LIS→BOS arrived `FRESH` with all three providers OK, but the timing line was lost to `jq`. A repeat on OPO→YUL took **4.2 s** cold and arrived `FRESH`.
   - **Step 2:** with errorRate 0.7, four "Refresh now" calls returned all OK; TIMEOUT/ERROR/TIMEOUT (none answered); OK/ERROR/TIMEOUT (partial, 1 of 3); TIMEOUT/CIRCUIT_OPEN/CIRCUIT_OPEN. The search still returned 20 stored items each time. Which provider failed was random, because chaos is global in the mock.
   - **Step 3:** with Redis stopped, every first page was `STALE` with `providers: {}` (HTTP 200, 20 items), and `POST` returned **409**. The UI shows this as three "no data" rows, no summary sentence, and "May be out of date" after 15 polls (30 s).
   - **Step 4:** Redis restarted and chaos reset to 0.1.
   - The circuit breakers opened during step 2 were still open on the next new route (OPO→YUL: BETA and GAMMA `CIRCUIT_OPEN`).
   - "No console errors" was **not** checked, because there was no browser.

Verified: lint clean, 33 tests passed, build OK. Not committed.
**Artifacts:** `frontend/src/useFreshnessPolling.ts`, `frontend/src/components/{FreshnessBadge,ProviderPanel,SearchResults,ResultsList}.tsx`, `frontend/src/App.tsx`, `frontend/src/useItinerarySearch.ts`, `frontend/src/styles.css`, `frontend/src/freshness.test.tsx`, `docs/ai-log.md`.

## 2026-11-14 — G4: frontend container (multi-stage build and nginx)

**Intent:** Package the frontend as a container that serves the built bundle and proxies `/api` to the aggregator inside the compose network.
**Prompt (summary):** `frontend/prompts/04-docker-nginx.md`: a `node:24-alpine` build stage with lockfile-first `npm ci` (no tests in the image) and a `nginx:stable-alpine` run stage; `nginx.conf` with `/healthz`, `/api/` proxied to `flight-aggregator:8080` with a 60 s read timeout, hashed `/assets/` cached for a year, `index.html` with `no-cache`, and gzip; `.dockerignore`.
**Generated:**
- `frontend/Dockerfile`, `frontend/nginx.conf` (with a comment on why there is no resolver: compose starts this service after the aggregator is healthy), `frontend/.dockerignore` (the list from the prompt, as written).
- Verification: `docker build` runs; `nginx -t` fails outside compose with `host not found in upstream "flight-aggregator"`, as the prompt predicted. With `--add-host flight-aggregator:127.0.0.1`, `nginx -t` passes, `/healthz` returns `ok`, `/` returns `Cache-Control: no-cache`, gzip is applied, and a deep route serves the SPA shell.
**Human review / corrections:** Accepted with two changes from the human:
1. **Healthcheck host.** The URL changed from `http://localhost/healthz` to `http://127.0.0.1/healthz`. On Alpine, `localhost` can resolve to `::1` first, and nginx listens on IPv4 only. My first verification only saw the status `starting` at 2 s and never waited for a final status, so it would not have caught this. After the change, a container with the stub upstream reached **`healthy`**, and every health log entry had exit code 0. For the record, in this image `localhost` still worked: `/etc/hosts` lists `127.0.0.1` before `::1` and `wget localhost` exited 0. The IP form removes the dependency on resolution order.
2. **One `Cache-Control` header on `/assets/`.** `expires 1y` was dropped. The location now sends only `Cache-Control: public, max-age=31536000, immutable`, which was verified on a hashed JS asset: one header and no `Expires`. I had noticed the duplicate headers in the first version but left them because the prompt asked for both.

Other notes: the image is **62.3 MB**. The first build took 66 s (cold cache), and a second build with no changes took **1.7 s**, all layers cached. Not checked here: the proxy path (`/api/` → aggregator), which needs compose and is the F2 check.

Not committed.
**Artifacts:** `frontend/Dockerfile`, `frontend/nginx.conf`, `frontend/.dockerignore`, `docs/ai-log.md`.

## 2026-11-14 — Re-submitting an identical search does not refetch

**Intent:** Make the Search button fetch again when the user submits the same filters, which previously did nothing.
**Prompt (summary):** Why does clicking "Search" again with unchanged filters not call the API? Then: apply the proposed fix, with no commit.
**Generated:**
- Diagnosis: the query key `["itineraries", params]` compares by value. Re-submitting the same values creates an equal key, so React Query sees no change and does not refetch. The URL is also unchanged, so there is no `pushState`, and `SearchResults` keeps its key, so the freshness polling does not restart. `staleTime` is 0, but nothing triggers a refetch: `refetchOnWindowFocus` is off and no observer mounts.
- `frontend/src/App.tsx`: when the submitted URL equals the current one, `handleSubmit` calls `queryClient.invalidateQueries({ queryKey: ['itineraries', next] })` and returns. Otherwise it behaves as before.
- `frontend/src/App.test.tsx`: new test "searches again when the same search is submitted twice". It checks that a second `fetch` happens and that the URL is unchanged.
**Human review / corrections:** Human approved the proposed fix. Verified: lint clean; 34 tests passed (1 new); build OK. Mutation check: with the invalidation removed, the new test fails. Restored.
Not verified in a real browser by the agent. Not committed.
**Follow-up:** the user reported that it still did not work in the browser. Cause: the running container (`ota-frontend` on port 3000) was built before the fix and still served the old bundle (`index-DKEyiYq0.js`). The source change does not reach nginx until the image is rebuilt. The image was rebuilt (`index-BtkZWRBY.js`), the old container was removed, and a new one was started on `ota-flights_default` at port 3000. The container reports `starting` for a few seconds after start, then becomes healthy, and `/api` through nginx returns 200. Lesson for the workflow: after any frontend source change, rebuild the image before testing the container (`npm run dev` avoids this).
**Artifacts:** `frontend/src/App.tsx`, `frontend/src/App.test.tsx`, `docs/ai-log.md`.

## 2026-11-14 — F2: frontend in Compose, quick start, clean-clone verification

**Intent:** Make `docker compose up --build` work for someone who has never seen this machine, including the dashboard.
**Prompt (summary):** `prompts/compose/02-add-frontend-and-clean-run.md`. Steps: add a `frontend` service (healthcheck from its Dockerfile, depends on a healthy aggregator) and make the published ports configurable; extend `smoke.sh` with checks through the frontend; rewrite the README quick start; verify from a clean clone of the branch without committing.
**Generated:**
- `docker-compose.yml`: `frontend` service. Ports set by `AGGREGATOR_PORT`, `MOCK_PROVIDERS_PORT` and `FRONTEND_PORT`, also added to `.env.example`.
- `scripts/smoke.sh`: optional third argument for the frontend URL. Checks `/healthz`, that `/` contains `id="root"`, and that a search through the nginx `/api` proxy returns items.
- `README.md`: quick start (prerequisites, measured build time, URLs, first search, resilience demo, quiet run, reset, ports, tests). Removed the "(pending)" markers.
- Clean clone: checked before the run. `mvnw` and the scripts are mode 100755 in git, both `maven-wrapper.properties` are tracked, `frontend/package-lock.json` is tracked and `npm ci` succeeded, and nothing the build needs is git-ignored (the root `.gitignore` does not ignore `prompts/`, contrary to the prompt). The only files missing from the clone were this step's own uncommitted ones: `docker-compose.yml`, `scripts/smoke.sh`, `.env.example`.
- Results: `docker compose build --no-cache` took 120 s, with base images already pulled locally, so not a fully cold machine. `up --wait` until all five services were healthy took 33 s. Smoke test: all 8 checks PASS. Dashboard-equivalent requests through the proxy (20 per page, MAD-JFK 2026-12-10): page 1 had 20 items, FRESH, all providers OK, in 4.0 s; page 2 had 20 items with no repeated ids and prices continuing in order.
**Human review / corrections:**
- **Missing files in the clone.** As the prompt required, the AI stopped and asked. The human chose to copy the three uncommitted files into the clone, without committing.
- **Port 3000 busy (found by the run).** It was held by a container `ota-frontend-local`, outside compose and apparently left from G4. The AI did not remove it; the clone and the working-tree stack ran with `FRONTEND_PORT=3001`.
- **Shared volume (found by the run).** `down -v` in the clone also deleted the working tree's Postgres volume, because the compose file pins `name: ota-flights`, so every checkout shares one project and one volume.
- **Resilience demo (AI error, caught before reporting).** The AI's first README draft said an already-searched route would show STALE data while providers fail. Checked against the live stack, that was wrong: within the 10-minute freshness TTL providers are not called at all. A failed refresh saves nothing, and the search response still shows the previous run's statuses (all OK) and FRESH. The README now describes what was observed. Whether a failed refresh should update the reported outcomes is flagged to the human; it was not changed.
- The AI had no browser available, so the dashboard was checked with the same HTTP requests the UI makes, not visually.
- Not committed.
**Artifacts:** `docker-compose.yml`, `.env.example`, `scripts/smoke.sh`, `README.md`, `docs/ai-log.md`.

## 2026-11-14 — F2 accepted: meaning of provider statuses after a failed refresh

**Intent:** Settle the questions the F2 clean-clone run raised.
**Prompt (summary):** F2 accepted. The human decided:
1. Keep `name: ota-flights` in the compose file.
2. Keep the current behaviour after a refresh in which every provider fails, but make it explicit: `providers` and `aggregatedAt` describe the last aggregation that produced the data shown, not the last attempt. This goes in the `@Schema` descriptions, in the dashboard (panel title, a message instead of silently reverting, and a test) and in `SOLUTION.md` §6.
3. The README resilience block uses `errorRate` 0.5, so the usual outcome is a partial failure.
**Generated:**
- `ItineraryPageResponse`: new `@Schema` descriptions for `providers` and `aggregatedAt`, confirmed in `/v3/api-docs` on the running stack.
- Frontend: the provider panel is titled "Last successful aggregation". When "Refresh now" returns with no provider OK, `SearchResults` shows "No provider answered; showing stored results." and leaves the panel, freshness badge and list unchanged: no refetch, no polling. New test in `freshness.test.tsx`. Mutation check: with the guard disabled, the new test failed; it was then restored.
- `docs/SOLUTION.md` §6: a refresh in which every provider fails stores nothing and is not reflected in the search response. The failure is visible only in the refresh response and in the logs.
- `README.md`: the resilience block uses `errorRate` 0.5. It describes partial failures as the usual outcome, and says a refresh in which all three fail leaves the stored results and statuses unchanged.
**Human review / corrections:** All decisions are the human's. The human's note said to replace 0.7, but the README draft actually used 1.0; it was changed to 0.5 as decided. Verified: frontend has 35 tests passing, lint is clean and the build succeeds. `./mvnw verify` in `flight-aggregator` passed (406 tests including 49 ITs, 0 failures). Stack rebuilt (frontend on 3001, because 3000 is still held by `ota-frontend-local`); smoke test all PASS, and the served bundle contains both new strings. The dashboard was not checked visually (no browser available). Not committed.
**Artifacts:** `flight-aggregator/infrastructure/src/main/java/io/github/jerojasol/flights/infrastructure/web/dto/ItineraryPageResponse.java`, `frontend/src/components/ProviderPanel.tsx`, `frontend/src/components/SearchResults.tsx`, `frontend/src/freshness.test.tsx`, `docs/SOLUTION.md`, `README.md`, `docs/ai-log.md`.
