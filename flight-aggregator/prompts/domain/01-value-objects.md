# A1 — Value objects: Airport, Carrier, FlightNumber, Price

Load the `hexagonal-java` and `testing-conventions` skills. Read `AGENTS.md` §4–§6 and `docs/domain-model.md` §2–§3 before writing anything.

## Task

Implement the following value objects in `domain/src/main/java/io/github/jerojasol/flights/domain/model/`, with their unit tests in `domain/src/test/java/...`:

1. `Airport` — invariant A1.
2. `Carrier` — invariant C1.
3. `FlightNumber` — invariant F1. Must normalise leading zeros so that `IB117` and `IB0117` are equal (providers disagree on this). Expose `carrier()` and `number()`.
4. `Price` — invariants P1–P4. `BigDecimal` amount with scale 2, `RoundingMode.HALF_EVEN`, `java.util.Currency`. Provide `plus`, `isLowerThan`, `multiply(BigDecimal factor)` returning a new `Price`; reject cross-currency operations.

Also create:
- `DomainException` (unchecked, abstract) and one concrete exception per VO (`InvalidAirportException`, etc.) in `domain/model` or `domain/exception` — pick one and be consistent.
- A `Fixtures` class in `domain/src/test` with constants `MAD`, `JFK`, `LHR`, `IB`, `BA`, `EUR`, and helpers `price("512.30")`, `instant("2026-11-20T07:35:00Z")`.

## Constraints

- `record`s with compact-constructor validation; static factory `of(...)` for normalisation (trim, uppercase).
- No imports outside `java.*`.
- Test method names `should_<expected>_when_<condition>` with the invariant ID in a trailing comment (`// A1`).
- Cover at least: valid input, null, wrong length, lowercase normalised by factory but rejected by constructor, HALF_EVEN rounding case for `Price` (e.g. `100.005` → `100.00`, `100.015` → `100.02`).

## Acceptance

- `./mvnw -q -pl domain test` green.
- Every invariant A1, C1, F1, P1, P2, P3, P4 has at least one test referencing it.
- `domain/pom.xml` untouched.

## Closing

Run `/check-arch`. Fix CRITICAL and MAJOR findings. Then `/log-ai-decision` noting anything I corrected, and show me the diff summary before committing.
