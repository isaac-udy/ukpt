# Async UI state discipline

Architecture rules for ViewModel State async lifecycle, a new advisory audit task, a reworked
`:feature:core` worked example, and updated `ukpt-feature-slice` templates.

## What changed

**udytils submodule bump.** `ViewModelState.update` now applies the lambda atomically under a lock.
Previous behavior dropped concurrent updates (a `state.update` from a second collector could read
stale state). Downstreams receive the fix through the submodule pin.

**New Gradle task: `auditArchitecture`.** Runs advisory audit checks and writes the full report to
`build/reports/architecture/audit.md`. `verifyArchitecture` now prints a one-line advisory audit
summary after enforced-rule results.

**New enforced rule: `ClientUi.ViewModelState.noManualAsyncLifecycleFields`.** A State data class
that declares both a progress-verb Boolean property (`loading`, `saving`, `sending`, `submitting`,
`refreshing`, `deleting`, `updating`, optionally `is`-prefixed, declared type `Boolean`) and an
error-synonym sibling (`error`, `failure`, `exception`, `throwable`) violates. Use `AsyncState<T>` /
`AsyncState<Unit>` or `UpdatableState<T>`. False positives require
`@ArchitectureException(ruleIds = ["ClientUi.ViewModelState.noManualAsyncLifecycleFields"], reason = "...")`.

**Strengthened unverifiable rules (now audited):**

- `ClientUi.ViewModelState.usesAsyncState` — audit flags lone progress-verb Boolean properties
  (same name+type test, no error-synonym sibling). Finding message is a review prompt.
- `ClientUi.ViewModelState.noFlattenedAsyncProxies` — audit flags single-expression getters
  whose body is `<asyncProp>.getOrNull()` (optionally chained with `?.<field>`, `.orEmpty()`,
  `?: <fallback>`). Finding message is a review prompt.

**New unverifiable rules and guidance (no audit):**

- `ClientUi.Screen.asyncStateExhaustiveRendering` — a Screen whose required data is an
  `AsyncState` renders `Idle`/`Loading`, `Success`, and `Error` explicitly.
- `ClientUi.ViewModel.aggregateReadProjection` — when several async inputs must be coherent,
  inject one domain interface exposing an immutable read projection.
- `ClientUi.Destination.presentationHints` — a NavigationKey may carry optional presentation
  hints; they are non-authoritative and replaced by loaded data.

**Reworked `:feature:core` worked example.** The template's `:feature:core` now demonstrates
`AsyncState<T>` for data, `AsyncState<Unit>` for actions, a domain read projection
(`GreetingSummary`/`FlowOfGreetingSummary`), exhaustive `when`-rendering, and preview coverage
per meaningful state.

**Updated `ukpt-feature-slice` templates.** New feature scaffolding emits the async-state pattern.

## Detection

```bash
./gradlew verifyArchitecture
./gradlew auditArchitecture
```

`verifyArchitecture` fails on `noManualAsyncLifecycleFields` violations. `auditArchitecture`
reports `usesAsyncState` and `noFlattenedAsyncProxies` findings.

## Migration

Run `auditArchitecture` and review the findings. Act on findings touching code being changed.
Burn down or exempt the standing backlog once — findings are advisory and require semantic review.
Refactor by coherent screen/feature slice rather than per flagged field.

For `noManualAsyncLifecycleFields` violations: replace the progress Boolean + error property pair
with `AsyncState<T>` or `AsyncState<Unit>`. If the pair is intentional, add
`@ArchitectureException(ruleIds = ["ClientUi.ViewModelState.noManualAsyncLifecycleFields"], reason = "...")`.

For `usesAsyncState` audit findings: review lone progress Booleans and consider `AsyncState<Unit>`.

For `noFlattenedAsyncProxies` audit findings: remove proxy getters that flatten an `AsyncState`
back into nullable/default values; read the `AsyncState` at the rendering boundary instead.

## Verification

```bash
./gradlew verifyArchitecture
./gradlew auditArchitecture
```
