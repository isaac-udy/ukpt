---
name: ukpt-verify
description: >-
  Compile and test the server and run every check before a pull request — the
  sweep, every test category including the browser End To End Tests, HTML
  golden snapshot recording/verification, architecture rule verification,
  validateTemplate and template integrity, the dependency graph test, the fat
  jar smoke test, and the CI workflow that runs the same sweep.
---

# ukpt-verify

Identifiers here use the template's UKPT identity (`ukptLayout`, `feature.ukpt`); projects rename these — the map is in `.ukpt/template.json`.

This is the sweep to run once, before a pull request. While iterating, run only the tests the
change affects: the `ukpt-testing` skill, "Development loop".

## The sweep

```
./gradlew validateTemplate verifyArchitecture test
./gradlew :app:server:smokeTestFatJar
```

`test` compiles every module and runs every module's tests, every category: the feature modules'
Unit, Component and Snapshot Tests, each `:feature:<name>:tests` module's Subcutaneous Tests,
`:platform:server:web`'s and `:platform:server:postgres`'s Adapter Tests, and `:app:server`'s
Wiring and End To End Tests. `:app:server:test` installs the Chromium headless shell first
(`installEndToEndBrowser`); the first run downloads it.

## CI

`.github/workflows/ci.yml` runs the sweep on every pull request and every push to `htmx`, in one
job: `validateTemplate` and `verifyArchitecture`, `-p build-logic test`, every test category by
task, and `:app:server:smokeTestFatJar`. On CI, `installEndToEndBrowser` also installs Chromium's
system libraries. A failed run uploads the test reports (`test-reports`) as an artifact. A project
adds its own jobs and deploy steps to the same file.

## By category

Every test class carries one category annotation (the `ukpt-testing` skill). A category task
given without a module path runs in every module that has it:
```
./gradlew unitTest componentTest snapshotTest wiringTest checkTest adapterTest
./gradlew subcutaneousTest endToEndTest
```

## HTML snapshots

Pages are snapshotted as HTML: a `@SnapshotTest` renders them with `createHTML()` and calls
`HtmlSnapshots().assertMatches("<Page>/<case>", html)` from `dev.isaacudy.udytils:html-snapshot`.
Goldens are normalised (one element per line, attributes sorted) and live in the module's
`src/test/snapshots/html/`. A module opts in with the `ukpt.html-snapshot` plugin, which makes the
goldens a test input.

Record, review the diff, then verify:
```
./gradlew :feature:core:server:snapshotTest -PrecordHtmlSnapshots
git diff -- '*/src/test/snapshots/html/*'
./gradlew :feature:core:server:snapshotTest
```
A missing golden fails verification. A golden changes only when the markup does, so read its diff
as a review of the rendered page.

## Architecture rules

`./gradlew :platform:common:architecture:verifyArchitecture` always re-executes; its failures name
the rule ID, the statement, and each violating declaration. It also prints a one-line advisory
audit summary; `./gradlew auditArchitecture` writes the full advisory report. Semantic review — web
flows, domain read projections — is the `ukpt-architecture-review` skill.

After changing a rule or an examples file, regenerate the documentation (README + `docs/`):
```
./gradlew :platform:common:architecture:updateArchitectureDocumentation
```

## Template integrity

`./gradlew validateTemplate` checks the marker, `.ukpt/flavour.json`, migration ordering and
sections, shared agent guidance, skill metadata, Claude compatibility links, and that every file
path, markdown link and architecture rule ID a skill cites still resolves.
`./gradlew -p build-logic test` runs the validator's and the rename planner's unit tests.

## Dependency graph

`ServerDependenciesTest` (`@WiringTest`, in `:app:server`) runs koin-test's `verify()` over one
module including the list the server installs (`serverDependencies(...)` in
`ServerDependencies.kt`): every registered constructor parameter must have a definition, checked
without instantiating anything or opening a database. A Routes class is part of that graph, so a
domain interface it takes without a binding fails here.

## Packaging

`./gradlew :app:server:smokeTestFatJar` builds the fat jar and boots it against an embedded
Postgres; run it after a dependency, resource or packaging change. The `ukpt-server-packaging`
skill covers it.
