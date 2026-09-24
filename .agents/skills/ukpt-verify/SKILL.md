---
name: ukpt-verify
description: >-
  Compile and test the server and run every check — the compile and test
  sweep, HTML golden snapshot recording/verification, architecture rule
  verification, validateTemplate and template integrity, the dependency graph
  test, and the fat jar smoke test. Use after making changes to verify
  correctness.
---

# ukpt-verify

Identifiers here use the template's UKPT identity (`ukptLayout`, `feature.ukpt`); projects rename these — the map is in `.ukpt/template.json`.

## The sweep

```
./gradlew test verifyArchitecture validateTemplate
```

`test` compiles every module and runs every module's tests: the feature modules (domain, routes,
event streams, HTML snapshots), `:platform:server:web`, and `:app:server`
(`ServerDependenciesTest`). A change scoped to one feature can run that feature's tests alone
(`./gradlew :feature:core:server:test`); run the whole sweep before handing the change off.

## HTML snapshots

Pages and Components are snapshotted as HTML: a test renders them with `createHTML()` and calls
`HtmlSnapshots().assertMatches("<Page>/<case>", html)` from `dev.isaacudy.udytils:html-snapshot`.
Goldens are normalised (one element per line, attributes sorted) and live in the module's
`src/test/snapshots/html/`. A module opts in with the `ukpt.html-snapshot` plugin, which makes the
goldens a test input.

Record, review the diff, then verify:
```
./gradlew :feature:core:server:test -PrecordHtmlSnapshots
git diff -- '*/src/test/snapshots/html/*'
./gradlew :feature:core:server:test
```
A missing golden fails verification. A golden changes only when the markup does, so read its diff
as a review of the rendered page.

## Route tests

Route tests boot the feature's routes with the platform: `testApplication { application {
installWebPlatform(listOf(routes)) } }`, with the Routes class constructed from fakes or the
feature's in-memory Repository. Parse responses with Jsoup and assert with CSS selectors. Send
`HX-Request: true` to exercise the htmx branch of a handler, and create the client with
`followRedirects = false` to assert a post/redirect/get `303`. An event stream is tested either as
a `Flow<ServerSentEvent>` (`greetingEvents(...)`) or through the client `SSE` plugin.

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

`ServerDependenciesTest` (in `:app:server:test`) runs koin-test's `verify()` over one module
including the list the server installs (`serverDependencies(...)` in `ServerDependencies.kt`):
every registered constructor parameter must have a definition, checked without instantiating
anything or opening a database. A Routes class is part of that graph, so a domain interface it
takes without a binding fails here.

## Packaging

`./gradlew :app:server:smokeTestFatJar` builds the fat jar and boots it against an embedded
Postgres; run it after a dependency, resource or packaging change. The `ukpt-server-packaging`
skill covers it.

## In a browser

The tests do not run htmx, the `sse` extension, or Alpine. After a change to markup attributes,
scripts or the event stream, run the server (`ukpt-run`), open the page, and check the browser
console: a content security policy violation there means a script or style is inline or comes from
another origin.
