---
description: Run the tests of one flight-aggregator layer (domain | application | infrastructure | it | all) and summarise failures
---

Run the tests for the `$1` layer of `flight-aggregator` and summarise the result.

Mapping:
- `domain` → `./mvnw -q -pl domain test`
- `application` → `./mvnw -q -pl application test`
- `infrastructure` → `./mvnw -q -pl infrastructure test` (unit + slices, no Docker)
- `it` → `./mvnw -q -pl infrastructure verify` (integration tests, needs Docker)
- `all` → `./mvnw -q verify`

Use the shell tool to run the matching command. Then:
1. State pass/fail and the counts (tests run, failures, errors, skipped).
2. For each failure: test class and method, the assertion message, and the most likely cause in one line.
3. Do not fix anything unless I ask. Do not weaken tests.

If `$1` is empty or not in the list, ask me which layer I meant.
