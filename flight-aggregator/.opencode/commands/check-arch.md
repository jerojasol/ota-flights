---
description: Run ArchUnit and the domain-guardian review on flight-aggregator
---

Architecture check for `flight-aggregator`.

ArchUnit result:
!`./mvnw -q -pl infrastructure test -Dtest=ArchitectureTest -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | tail -40`

Domain and application unit tests:
!`./mvnw -q -pl domain,application test 2>&1 | tail -20`

Now launch the `domain-guardian` subagent to review `domain/` and `application/` against `docs/domain-model.md` and `AGENTS.md`. When it reports back, present its findings to me unchanged, then add a short list of which findings you recommend fixing first and why.

Extra focus, if any: $ARGUMENTS
