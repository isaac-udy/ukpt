# Shared UI tests, client adapters, Routes and robot steps

The Testing rules assumed every Component Test renders a feature screen and every adapter is on
the server. They now also accept these tests:

- `Testing.ComponentTest.placement` accepts the `jsTest`, `jvmTest` or `desktopTest` source set of
  a `:platform:client:*` or `:app:client:common` module. `rendersScreenContentOnly` and
  `drivesThroughRobots` skip tests in those modules.
- `Testing.AdapterTest.placement` accepts a platform test source set (any `*Test` except
  `commonTest` and `androidHostTest`) of a `:platform:client:*` or `:feature:[name]:client` module.
- `Testing.Robot.oneScreen` also counts an `object [Name]Tags` of `const val`s declared in a
  shared UI module.
- `Testing.UnitTest.oneProductionClass` treats a `data class`, a `value class`, or a class of
  primary-constructor `val`s only as test data.

`FeatureRules.DependencyModule.registeredInEveryClientShell` checks nothing when the project has
one `ClientDependencies.kt`, and accepts a module that another `:client` module installs through
`includes(…)`.

New Construct: `ServerServices.Route`, a top-level `Route.[name]` extension in
`feature.[name].server.services` of `:server`, for a plain-HTTP caller such as a webhook. New rule:
`ServerServices.Route.takesDomainInterfaces`.

Template-owned files that arrive with the file sync: `installServerHost` passes a
`ServiceErrorMapper` bound in the server graph to `urpcWithKoin`; `ScreenRobot` has step helpers
for absence, enabled state, text entry, scrolling, semantics, and text or descriptions inside a
tagged ancestor; `:platform:common:subcutaneous` has `awaitAsserted`. `ukpt-testing` says client
file storage is an edge.

No rule ID is renamed or removed.

## Detection

```bash
# Exceptions or workarounds for the widened rules
grep -rn 'Testing.ComponentTest\|Testing.AdapterTest\|Testing.Robot.oneScreen\|Testing.UnitTest.oneProductionClass\|registeredInEveryClientShell' \
  --include='*.kt' feature platform app
# Plain-HTTP routes outside a feature
grep -rln 'fun Route\.' --include='*.kt' app/server
# Local copies of the new helpers
grep -rn 'fun <T> awaitAsserted\|ServiceErrorMapper' --include='*.kt' feature platform app
```

## Migration

1. Apply the catalog changes through the rule sync. A project that deleted
   `registeredInEveryClientShell` may keep it deleted.
2. Remove `@ArchitectureException` entries and moved or re-annotated tests that only worked around
   the four Testing rules above. Ask the owner before removing an exception.
3. Move a webhook or other plain-HTTP route from `:app:server` into its feature's `:server` as a
   `Route.[name]` extension in `feature.[name].server.services`. It takes domain interfaces and
   configuration, never a Repository, Storage or ServiceImpl. `:app:server` installs it after
   `installServerHost`.
4. If the project edited `installServerHost` to pass an error mapper, take the template's version
   and bind the `ServiceErrorMapper` in the server graph.
5. Replace local copies of `awaitAsserted` with `platform.testing.subcutaneous.awaitAsserted`, and
   local robot helpers that duplicate the new `ScreenRobot` steps with those steps.

## Verification

`./gradlew verifyArchitecture` passes, and the project's `subcutaneousTest`, `componentTest` and
`adapterTest` tasks pass.
