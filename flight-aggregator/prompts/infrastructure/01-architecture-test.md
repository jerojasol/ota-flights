# D1 — Maven test plugins and ArchitectureTest (ArchUnit) before any infrastructure code

Load the `hexagonal-java` skill (§9 lists the rules). Read `AGENTS.md` §3–§4.

## Part 0 — Activate Failsafe (integration tests do not run otherwise)

`spring-boot-starter-parent` only *manages* `maven-failsafe-plugin`; it must be declared to run. In `infrastructure/pom.xml` `<build><plugins>` add:

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-failsafe-plugin</artifactId>
  <configuration>
    <excludedGroups>perf</excludedGroups>
  </configuration>
</plugin>
```
(version and `integration-test`/`verify` executions are inherited). Add a `perf` profile that sets `<excludedGroups/>` empty and `<groups>perf</groups>` so `./mvnw verify -Pperf` runs only the tagged tests.

Update `AGENTS.md` §8 commands to:
```
./mvnw verify              # unit + integration (needs Docker)
./mvnw verify -DskipITs    # unit only
./mvnw verify -Pperf       # perf-tagged ITs only
```
and remove the non-existent `-Pintegration` profile reference.

Verify: create a trivial `SmokeIT` with one passing assertion, run `./mvnw -q -pl infrastructure verify` and confirm Failsafe reports `Tests run: 1`. Delete `SmokeIT` afterwards; `ArchitectureTest` stays under Surefire (it needs no Docker).

## Part 1 — ArchitectureTest

## Task

Create `infrastructure/src/test/java/io/github/jerojasol/flights/infrastructure/ArchitectureTest.java` using `@AnalyzeClasses(packages = "io.github.jerojasol.flights", importOptions = ImportOption.DoNotIncludeTests.class)`.

Rules (one `@ArchTest` each, named so failures read as sentences):

1. `domain_depends_only_on_jdk_and_itself` — classes in `..domain..` may only depend on `java..`, `..domain..`.
2. `application_depends_only_on_jdk_and_domain` — `..application..` → `java..`, `..domain..`, `..application..`.
3. `domain_has_no_framework_annotations` — no class in `..domain..` annotated with anything from `org.springframework..`, `jakarta..`, `tools.jackson..`.
4. `web_does_not_touch_outbound_adapters` — `..infrastructure.web..` must not depend on `..infrastructure.persistence..`, `..infrastructure.provider..`, `..infrastructure.cache..`.
5. `controllers_depend_only_on_inbound_ports_and_web` — classes with simple name ending `Controller` depend only on `..domain.port.in..`, `..domain.model..` (and the query/result package if you created one), `..infrastructure.web..`, `org.springframework..`, `jakarta.validation..`, `java..`.
6. `jpa_entities_stay_in_persistence` — classes annotated `@jakarta.persistence.Entity` reside in `..infrastructure.persistence..` and are only accessed from that package.
7. `provider_dtos_stay_in_their_adapter` — for each of `alpha`, `beta`, `gamma`: classes in `..provider.<p>..` are only accessed from `..provider.<p>..` or `..infrastructure.config..`.
8. `ports_are_interfaces` — classes in `..domain.port..` are interfaces.
9. `no_field_injection` — no field annotated `@Autowired` anywhere.
10. Layered check via `layeredArchitecture()`: `Domain` ← `Application` ← `Infrastructure`, with `Infrastructure` not accessed by anyone.

Use `com.tngtech.archunit.library.Architectures.layeredArchitecture()` for rule 10 and plain `ArchRuleDefinition` for the rest. Allow the `..infrastructure.config..` package to access everything (it is the composition root).

## Acceptance

- `./mvnw -q -pl infrastructure test -Dtest=ArchitectureTest` passes against the current (empty infrastructure) code base.
- Deliberately add a temporary `import org.springframework.stereotype.Service;` to a domain class, run again, confirm rule 1/3 fails with a readable message, then revert. Report the message you saw.

## Closing

`/check-arch` now runs the full pipeline. `/log-ai-decision`, show diff summary.
