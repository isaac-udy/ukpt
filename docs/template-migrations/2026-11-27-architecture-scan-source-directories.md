# Parse only module `src` directories for the architecture scan

`ProjectScope.kt` in `:platform:common:architecture` no longer calls `Konsist.scopeFromProject()`.
`ProjectScope.kt` collects the `src` directory of every directory under the build root that
contains a `build.gradle.kts` or `build.gradle` file, parses those directories with
`Konsist.scopeFromDirectories`, and passes the scope to `useKonsistTypeIndex`. The walk skips
directories named `build`, `src` or `node_modules` and directories whose name starts with `.`.

`useKonsistTypeIndex` is declared in the `architecture-core` artifact of `embedded-udytils`. Sync
the `embedded-udytils` pin and `ProjectScope.kt` in the same step; either one without the other
does not compile or does not change the scan.

## Detection

- Compare the project's `ProjectScope.kt` with the template base. A project that edited
  `governedScope` or replaced `Konsist.scopeFromProject()` ports the change by hand.
- List the modules whose Kotlin sources are outside `<module>/src`: a module with a custom
  `srcDir`, a module with no build file in the module directory, or a module below a directory
  named `build`, `src` or `node_modules` or below a directory whose name starts with `.`. The scan
  no longer classifies declarations in those modules, and `verifyArchitecture` reports no failure
  for the omission.

## Migration

1. Before syncing, run `./gradlew classifyArchitecture` and copy
   `platform/common/architecture/build/reports/architecture/classification.json` to a location
   outside the build directory.
2. Sync the `embedded-udytils` pin and `ProjectScope.kt`. In a customised `ProjectScope.kt`, keep
   the project's `slice` predicates and replace only the source of `governedScope` with
   `sourceScope`.
3. For each module found in Detection, add the module's source directory to `sourceDirectories`,
   or move the sources under `<module>/src`.

## Verification

- Run `./gradlew classifyArchitecture` and compare the new `classification.json` with the copy from
  step 1. The two files are identical when the scan covers the same declarations.
- Run `./gradlew classifyArchitecture` a second time without a source change. Gradle reports
  `classifyArchitecture` as `UP-TO-DATE`.
