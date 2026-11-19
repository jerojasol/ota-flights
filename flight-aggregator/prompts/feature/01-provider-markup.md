# F1 — Markup Policy per Provider

Load the `hexagonal-java` and `testing-conventions` skills. Read `docs/domain-model.md` §3 (I10, I11), §5 (MarkupPolicy, DeduplicationPolicy), §7 (domain services, package layout), §9 (pipeline) and `AGENTS.md` §4–§6.

## Problem

The Markup Policy is global: `PercentageMarkupPolicy(5%)` applies the same 5% to every Provider. The commercial agreements differ per Provider, so the markup must be a function of **(Provider, Base Price)**, configurable per Provider, with a default for the ones not configured.

## The decision this change turns on

With one global percentage the cheapest Base Price is always the cheapest Final Price. With per-provider percentages that stops being true:

| Provider | Base Price | Markup | Final Price |
|---|---|---|---|
| ALPHA | 100.00 | 10% | 110.00 |
| GAMMA | 105.00 | 2% | 107.10 |

`ItineraryDeduplicator` currently keeps the lowest **Base Price**, so it would keep ALPHA and the customer would be shown 110.00 when 107.10 was available — and searches sort and paginate by `final_price`, so the wrong row would also sort wrongly.

**Deduplication must therefore select the lowest Final Price.** Markup is applied to each Offer *before* the per-key winner is chosen. If you believe the business wants the opposite ("cheapest base price for us, whatever the customer sees"), stop and ask before writing code; everything below assumes Final Price.

## Step 0 — Preconditions

- The working tree already contains the unrelated price-increase fix (`ItineraryRepositoryPort`, `ItineraryJdbcUpserter`, `EndToEndIT`, `ItineraryRepositoryAdapterIT`, `InMemoryItineraryRepository`, `AggregateFlightsServiceTest`, `docs/domain-model.md`, `../SOLUTION.md`, `../docs/ai-log.md`, `prompts/infrastructure/02-persistence.md`). **Keep it.** Build your edits on top of what is in the tree; never revert or overwrite those changes.
- Record the test counts per module before starting (`./mvnw -q verify -DskipITs`). They must not drop.

## Step 1 — Documents first (`AGENTS.md` §9)

No new domain type before the document says it exists.

1. `docs/domain-model.md`
   - §5 **MarkupPolicy**: new signature `Price apply(Provider provider, Price basePrice)`; `PercentageMarkupPolicy` is the uniform case; new `ProviderMarkupPolicy` = default percentage + per-provider overrides. State that the domain still never reads configuration.
   - §5 **DeduplicationPolicy** step 2: "lowest `basePrice`" becomes "lowest Final Price (markup applied per Provider)". Keep the tie-break as it is (Provider enum order, then earlier final arrival) and keep the O(n) grouping requirement. Add the two-line worked example above as the reason.
   - §3 **I10**: still "derived via the Markup Policy, never set directly", now derived from `(source, basePrice)`.
   - §3 **Rehydration**: the existing paragraph already explains why stored prices are trusted. Extend it in one sentence: a percentage change repriced per Provider is equally not retroactive.
   - §7 service table: `ItineraryDeduplicator` now holds the `MarkupPolicy`; update its inputs/outputs cell and `ItineraryFactory`'s.
   - §7 package layout: add `ProviderMarkupPolicy` to the `model` row and to the paragraph explaining why the policies stay in `model`.
   - §9 pipeline diagram: the markup is applied before the winner is selected. Keep the diagram readable, e.g. `OfferValidator → MarkupPolicy per Offer → ItineraryDeduplicator (cheapest Final Price) → ItineraryFactory`.
2. `AGENTS.md` §5 Ubiquitous Language
   - **Markup Policy**: "Rule that derives Final Price from Base Price **for a given Provider**. Default: +5% for every Provider, overridable per Provider."
   - **Aggregation**: the step order becomes "… validate → apply markup → deduplicate (cheapest Final Price) → persist".
   - Do not invent a new term unless you need one; if you do, add it to the table in the same edit.

Show me the document diff and stop there. I confirm it before any Java changes.

## Step 2 — Domain (`domain/`)

- `MarkupPolicy` (`model`): `Price apply(Provider provider, Price basePrice)`.
- `PercentageMarkupPolicy` (`model`): keep as it is, ignoring the Provider — it is the uniform policy and most tests use it. The arithmetic (`× (1 + pct/100)`, HALF_EVEN, scale 2 through `Price.multiply`) stays in this one class.
- `ProviderMarkupPolicy` (`model`, new): `defaultPercentage` plus per-Provider overrides.
  - Immutable, defensive `EnumMap` copy, static factory; no setters.
  - Rejects: null or negative default, null map, null key, null or negative value — `InvalidPriceException`, the same exception `PercentageMarkupPolicy` already throws for a negative percentage.
  - `percentageFor(Provider)` is public (the startup log and tests need it); `apply` delegates to `PercentageMarkupPolicy` so rounding has exactly one implementation.
  - No configuration reading, no `Optional` field, no framework import.
- `Itinerary.from(Offer, MarkupPolicy, Instant)`: pass `winning.provider()` into `apply`. `rehydrate` is unchanged.
- `ItineraryDeduplicator` (`service`): takes the `MarkupPolicy` in its constructor and compares `markup.apply(offer.provider(), offer.basePrice())`.
  - Reject a null policy the way `ItineraryFactory` does.
  - Grouping stays a single hash pass, O(n); do not build `Itinerary` instances to compare them, and do not sort the offers of a group.
  - Tie-break order after equal Final Price: Provider enum order, then earlier final arrival — unchanged and still deterministic.
  - It returns `Offer`s, as now; `ItineraryFactory` re-applies the markup when it builds the winner. Keep it that way: the two call sites must use the same policy instance.
- No new exception type. No `Optional` as a field or parameter.

## Step 3 — Guard step (do not skip, do not postpone)

Run `/check-arch` **now**, before touching `infrastructure/`:

- ArchUnit green (no new rule should be needed: `service → model` is allowed, and `MarkupPolicy` must stay in `model`).
- `./mvnw -q -pl domain,application test` green.
- `domain-guardian`: no CRITICAL and no MAJOR finding; I10 and I11 reported as covered.

Present the guardian's findings unchanged, then fix them. Do not start Step 5 with an open CRITICAL or MAJOR.

## Step 4 — Application (`application/`)

`AggregateFlightsService` should need **no logic change**: it already delegates to `ItineraryDeduplicator` and `ItineraryFactory`. Only its test's construction changes. If you find yourself writing markup or price-comparison code in this layer, stop — it belongs in the domain.

## Step 5 — Infrastructure configuration

- `AggregationProperties`: drop `markupPercentage`, add a nested record bound at `aggregation.markup`:
  `MarkupProperties(BigDecimal defaultPercentage, Map<Provider, BigDecimal> providers)`.
  - The YAML key must be `default-percentage`: `default` is a Java keyword, so no record component can carry that name and relaxed binding will not map it.
  - A missing or empty `providers` map is valid: every Provider then uses the default.
  - Validate in the compact constructor, in the existing fail-fast style, naming the exact property in the message: `aggregation.markup.default-percentage must be zero or more`, `aggregation.markup.providers.beta must be zero or more`. Keep the other cross-checks (`lock-ttl`, `freshness-ttl`, `warm-*`) untouched.
- `UseCaseConfiguration`:
  - the `MarkupPolicy` bean becomes a `ProviderMarkupPolicy` built from the properties;
  - inject that **same bean** into `itineraryDeduplicator(...)` and `itineraryFactory(...)`;
  - log the effective percentage per Provider once at startup, SLF4J, structured `key=value`. Logging stays in infrastructure.
- `application.yaml`:
  ```yaml
  aggregation:
    markup:
      default-percentage: ${MARKUP_PERCENTAGE:5}
      providers:
        alpha: ${MARKUP_ALPHA:${MARKUP_PERCENTAGE:5}}
        beta: ${MARKUP_BETA:${MARKUP_PERCENTAGE:5}}
        gamma: ${MARKUP_GAMMA:${MARKUP_PERCENTAGE:5}}
  ```
  `MARKUP_PERCENTAGE` keeps working as the fallback for all three, so existing `.env` files and the compose file stay valid.
- `infrastructure/src/test/resources/application-test.yaml`: leave the markup at its defaults; tests that need other values set them themselves.
- Repository root — allowed, these are this service's own container variables (`../AGENTS.md`): add `MARKUP_ALPHA`, `MARKUP_BETA`, `MARKUP_GAMMA` to the `flight-aggregator` service's `environment` in `../docker-compose.yml` as `${MARKUP_ALPHA:-5}` …, and to `../.env.example` with a one-line comment each, next to `MARKUP_PERCENTAGE` (whose comment becomes "default markup for providers without an override").

## Step 6 — Tests

Follow `testing-conventions`: `should_<expected>_when_<condition>`, AssertJ only, pure unit tests in `domain/`. **Never weaken a test to make it pass.**

New:
- `domain/model/ProviderMarkupPolicyTest`: a percentage per Provider; a Provider without an override falls back to the default; currency preserved (I11); negative or null default, negative or null override rejected; the map is copied defensively (mutating the caller's map afterwards changes nothing).
- `domain/service/ItineraryDeduplicatorTest`: the Offer with the lowest Base Price **loses** because another Provider's markup makes its Final Price lower (use the 100/10% vs 105/2% case, assert the winner and its provider); equal Final Prices from different Base Prices fall back to Provider order; the existing cases keep passing.

Update (the signature change touches all of these):
- `PercentageMarkupPolicyTest`, `ItineraryTest` (I10 asserted per Provider; the `wrongCurrency` lambda now takes two arguments and must keep covering I11), `ItineraryFactoryTest`, `domain/search/ItineraryPageTest`.
- `application`: `AggregateFlightsServiceTest` — fix construction, and add one test where two Providers overlap with different percentages and the persisted Itinerary is the cheapest Final Price with that Provider's markup. `SearchItinerariesServiceTest` construction.
- `infrastructure`: `AggregationPropertiesTest` (new record shape; negative per-provider value; absent provider falls back to the default), `ItineraryControllerTest`, `ItineraryRepositoryAdapterIT`.
- `EndToEndIT`: this file is **modified by the price-increase fix** — edit on top of it. Replace the single `MARKUP = 1.05` constant with distinct percentages per Provider (e.g. ALPHA 10, BETA 2, GAMMA 7) set through the test's property source, and recompute by hand the expected Final Prices and the expected `source` of each of the three itineraries (today `377.09 / 544.95 / 1471.07` and `GAMMA / BETA / GAMMA`). Expected values stay written out, not computed, so the test documents the outcome. Keep step "cheaper wins across runs" meaningful under the new percentages: pick numbers where at least one winner changes Provider because of the markup.
- `../scripts/smoke.sh`: the markup check must read `.items[0].source` and use that Provider's percentage (`MARKUP_ALPHA`/`MARKUP_BETA`/`MARKUP_GAMMA`, falling back to `MARKUP_PERCENTAGE`). Keep the one-cent HALF_EVEN tolerance.

## Step 7 — Remaining documents

Grep before editing; change only what is now wrong:
- `../SOLUTION.md`: the summary line "applies a 5% markup", the `model` type list, the package-rationale paragraph that mentions `MarkupPolicy`, the data-model paragraph, and the smoke-test paragraph. Add to the limitations section: a percentage change is not retroactive, and cursors issued before a change can skip or repeat a row once (see call-outs).
- `../README.md`: the "applies a 5% OTA markup" sentence.
- `../docs/domain.mmd`: `PercentageMarkupPolicy`, the new `ProviderMarkupPolicy`, and `Itinerary`'s relation to them.
- `.opencode/agents/domain-guardian.md`: the I10 check and the "`PercentageMarkupPolicy` takes the percentage as a constructor argument" line.
- `.opencode/skills/hexagonal-java/SKILL.md`: the `Itinerary.from` example.
- Do **not** edit anything else under `prompts/` and do not rewrite earlier `../docs/ai-log.md` entries.

## Things to take into account

- **No database migration.** `base_price`, `final_price` and `source` are already stored, and `rehydrate` deliberately trusts them.
- **Repricing is not retroactive.** After a configuration change, existing rows keep their old Final Price until their route is aggregated again (freshness TTL 10 min; a key no Provider returns any more is never rewritten). Immediate repricing of the whole table is out of scope — flag it, do not implement it.
- **Keyset pagination orders by `final_price`.** Changing percentages reorders pages, so a cursor issued before a re-aggregation may skip or repeat one row. Same class of effect as a price change; document it, do not try to fix it.
- **I11 must hold for every Provider's policy**: a markup never changes the currency.
- **One reference currency still applies** (§10): O1 is checked before deduplication, so all Final Prices compared are in the same currency. Do not add a second currency property.
- **A Provider outage can rewrite a key at a higher Final Price.** Already accepted in §8; per-provider markups make it more visible. Mention it, change nothing.
- **The API does not expose the markup.** Leave `ItineraryResponse` alone unless I ask for the applied percentage.
- **`ProviderOutcomeLogger`** is untouched.

## Acceptance

Report the real output of each:

```bash
./mvnw -q -pl domain,application test
./mvnw -q verify                                  # needs Docker; counts not lower than the baseline
git diff --stat HEAD | tail -1
```

Then:
- `/check-arch`: ArchUnit green, `domain-guardian` with no CRITICAL or MAJOR finding, invariants I1–I12 covered.
- `docker compose --project-directory .. up --build -d flight-aggregator`, then
  `MARKUP_ALPHA=10 MARKUP_BETA=2 MARKUP_GAMMA=7 ../scripts/smoke.sh` — show the output.
- Show one concrete worked example from the new tests where the lowest Base Price loses to a lower Final Price, with the numbers.

## Closing

`/check-arch`, `/test-layer all`, then `/log-ai-decision` noting every decision you made that this prompt did not dictate and anything I corrected. Show `git status --short` and the diff summary. **Do not commit**: this goes in its own branch and its own commit, separate from the price-increase fix already in the tree.
