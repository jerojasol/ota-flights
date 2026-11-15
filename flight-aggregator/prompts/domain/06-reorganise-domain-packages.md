# E0 — Reorganise the domain packages

Read `AGENTS.md` §3–§4 and `docs/domain-model.md` first.

This is a **move-only refactor**. `domain.model` holds 40 types in one flat package; split it into five. No class is renamed, no signature changes, no logic changes, no test is added or weakened. If a step seems to need a code change beyond `package` and `import` lines, stop and tell me.

Precondition: `git status --short` is clean and `./mvnw verify` is green. Record the test counts per module before starting; they must be identical at the end.

## Target layout

Under `io.github.jerojasol.flights.domain`:

| Package | Purpose | Types |
|---|---|---|
| `model` | What is sold, and the rules it carries | `Itinerary`, `Segment`, `Offer`, `ItineraryKey`, `Airport`, `Carrier`, `FlightNumber`, `Price`, `Provider`, `RejectionReason`, `MarkupPolicy`, `PercentageMarkupPolicy` |
| `service` | The three steps of the pipeline and their results | `OfferValidator`, `OfferValidation`, `ValidationSummary`, `ItineraryDeduplicator`, `ItineraryFactory` |
| `search` | What goes into and comes out of a search | `SearchCriteria`, `ItineraryFilter`, `PageCursor`, `ItineraryPage`, `SearchResult`, `Freshness`, `LockToken` |
| `aggregation` | The outcome of calling the providers | `AggregationResult`, `ProviderOutcome`, `ProviderResponse`, `ProviderStatus` |
| `exception` | `DomainException` and every subclass | `DomainException`, `InvalidAggregationException`, `InvalidAirportException`, `InvalidCarrierException`, `InvalidFlightNumberException`, `InvalidItineraryException`, `InvalidItineraryKeyException`, `InvalidOfferException`, `InvalidPriceException`, `InvalidSearchException`, `InvalidSegmentException`, `ProviderUnavailableException` |
| `port.in`, `port.out` | unchanged | unchanged |

That is 12 + 5 + 7 + 4 + 12 = 40. If the package holds a type not listed here, stop and ask where it goes.

Two placements are deliberate; do not "fix" them:
- `MarkupPolicy` and `PercentageMarkupPolicy` stay in `model`. `Itinerary.from(...)` takes the policy, so moving it to `service` would make `model` depend on `service`.
- `RejectionReason` stays in `model`. `Itinerary` itself uses it.

## Dependency direction between the new packages

```
service      ──> model
search       ──> model, aggregation
aggregation  ──> model
model        ──> (none of the three above)
exception    <─> model, aggregation     (accepted: exceptions carry the types they report)
```

Before moving anything, check the current imports against this. If a type in `model` would end up depending on `service`, `search` or `aggregation`, or `aggregation` on `search`, report the exact reference and stop: the table above is then wrong and I will decide.

Nested types move with their owner (for example `Itinerary.Violation`).

## Steps

1. **Main sources.** Move the files with `git mv` so history is kept. Update each moved file's `package` line and the imports in every file that refers to it, across `domain`, `application` and `infrastructure`, main and test. Types that used to share a package now need explicit imports of each other.
2. **Tests.** Move every test in `domain/src/test/.../domain/model/` to the package of the class it tests (`PriceTest` → `model`, `OfferValidatorTest` → `service`, and so on). `Fixtures` stays where it is.
3. **Everything was `public` already**, so no visibility needs widening. If the compiler says otherwise, stop and report it rather than adding `public`.
4. **`ArchitectureTest`:**
   - The rule on controllers allows `..domain.model..`. Extend it to the packages a controller may legitimately see: `model`, `search`, `aggregation`. Do not add `service`; if a controller needs `service`, report it.
   - Add one rule, named so a failure reads as a sentence: `model_does_not_depend_on_service_search_or_aggregation`.
   - Add a second: `aggregation_does_not_depend_on_search_or_service`.
   - Prove the first new rule bites: add a temporary import of a `service` type to a `model` class, run the test, quote the failure message, revert.
5. **Documents** — every place that names the old package or lists the domain layout:
   - `docs/domain-model.md`: add a short "Package layout" subsection with the table above and the dependency direction. Update any path it mentions.
   - `AGENTS.md` §3 (module layout) and anywhere it says `domain/model`.
   - `.opencode/agents/domain-guardian.md` and `.opencode/skills/hexagonal-java/SKILL.md`: package names and example `package` lines. The guardian's check "ports are in `domain/port`" is unchanged; where it says value objects live in `domain/model`, list the new packages.
   - `../docs/SOLUTION.md`, `../README.md` and `../frontend/` only if they name the old package: grep before editing.
   - Do **not** edit earlier entries of `../docs/ai-log.md` or the files under any `prompts/` directory; they are a record of what was asked at the time.

## Acceptance

Report the real output of each:

```bash
./mvnw verify                                   # green; same counts per module as before
git diff --stat HEAD | tail -1
git status --short | grep -v '^R' | head -40    # everything in domain should show as a rename
git diff HEAD -M --diff-filter=R --stat | tail -3
grep -rn "domain\.model\.\(OfferValidator\|ItineraryDeduplicator\|ItineraryFactory\|OfferValidation\|ValidationSummary\|SearchCriteria\|ItineraryFilter\|PageCursor\|ItineraryPage\|SearchResult\|Freshness\|LockToken\|AggregationResult\|ProviderOutcome\|ProviderResponse\|ProviderStatus\|[A-Za-z]*Exception\)" --include=*.java --include=*.md . | grep -v prompts/   # must print nothing
```

Then confirm, with evidence:
- The diff of moved Java files contains only `package` and `import` lines. Show how you checked (for example, `git diff HEAD -M` filtered to lines that are neither).
- No test was deleted, skipped or changed in its assertions.
- `/check-arch` passes, and the guardian reports no new finding caused by the move.

Finally, rebuild the image the stack uses, since class names in stack traces and logs change: `docker compose --project-directory .. up --build -d flight-aggregator` and `../scripts/smoke.sh`.

## Closing

Do not commit. This goes in its own branch and its own commit, containing nothing but the move, the two ArchUnit rules and the document updates. Show `git status --short`, then `/log-ai-decision`.
