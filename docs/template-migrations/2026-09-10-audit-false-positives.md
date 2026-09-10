# Three audits stop reporting correct code

Three checks matched code that already follows the rule they state. Each fix narrows what the check
matches; none changes what the rule means. Downstream projects have nothing to migrate — the effect
is that findings disappear from `auditArchitecture`, and one enforced rule stops failing on a shape
it should never have failed on.

## Rules

- **`ClientUi.ViewModelState.usesAsyncState`** (audit, narrowed): reports stored progress-verb
  Booleans only. A property with a getter derives its value from another property —
  `val refreshing: Boolean get() = refreshProgress is AsyncState.Loading` reads the `AsyncState`
  the rule prescribes — and was previously reported alongside the `AsyncState` it reads. A getter
  that flattens an `AsyncState` into a nullable or a default remains
  `ClientUi.ViewModelState.noFlattenedAsyncProxies`' subject.
- **`ClientUi.ViewModelState.noManualAsyncLifecycleFields`** (enforced, narrowed): the same
  correction on the enforced twin. The pair the rule prohibits is two stored properties, so a
  computed Boolean beside an error property is no longer half of one. A project holding an
  `@ArchitectureException` for this shape can delete it.
- **`DesignSystemRules.noLiteralsInFeatureUi`** (audit, narrowed): a `@Preview` function is exempt.
  A preview states the viewport it renders (`UkptPreviewFrame(width = 360.dp)`), which describes
  the device rather than the design language, and previews live in the same package as the screens
  they preview. A non-preview helper a preview calls is not exempt.
- **`ClientDomain.DomainInterface.consumedInProduction` / `ServerDomain.…`** (audit, widened
  detection): counts a file resolving the interface out of the Koin container — `get<T>()`
  (including `koin.get<T>()` and `getKoin().get<T>()`), `inject<T>()`, and Compose's
  `koinInject<T>()` — as a consumer, alongside a class taking it as a primary-constructor
  parameter. An interface resolved by an `:app` shell's startup wiring or a Compose entry point is
  no longer reported as having no consumer. A `bind T::class` in a Koin module is the binding, not
  a consumer, and is still not counted.

The grouped audits — `readProjections`, `namesACapability`, `collapsedUpdateFamilies` — continue to
count constructor injection alone, because what they report is interfaces injected together into
one class. The `inventory` line counts both forms, so its `none`/`one`/`several` split matches
`consumedInProduction`.

## Detection

```bash
./gradlew auditArchitecture
```

Compare the report against the previous run. `usesAsyncState` findings on a State whose flagged
Boolean has a getter, `noLiteralsInFeatureUi` findings inside `@Preview` functions, and
`consumedInProduction` findings on an interface an `:app` module resolves are the ones that
disappear. Nothing else changes.

## Migration

Nothing to apply. Re-triage the `consumedInProduction` list: what remains is the set of interfaces
neither injected nor resolved, which is the list to check before deleting.

## Verification

```bash
./gradlew verifyArchitecture
```
