# ViewModel rules, `CompositionLocal`, and exception-reason relaxations

A batch of `architecture` catalog changes ported from a downstream project. The rule sources under
`platform/common/architecture` carry them (via the file sync); this entry covers the ones that can
flag existing downstream code.

- **`UiLayer.ViewModel.publicFunctionsReturnUnit` now catches default-visibility functions.** The
  filter used `hasPublicModifier`, which silently skipped functions with no explicit visibility
  modifier — i.e. most functions. It now uses `hasPublicOrDefaultModifier`, so a public-by-default
  ViewModel function returning a non-`Unit` value is flagged where it previously slipped through.
  No rule ID change; the rule simply enforces what its statement always said.
- **New rule `UiLayer.ViewModel.publicFunctionsNotSuspend`.** A ViewModel's `public`/`internal`
  functions must not be `suspend`: a suspending public method makes the caller await work the
  ViewModel should own, and on Android the awaiter is lost on process death. Launch into
  `viewModelScope` and reflect the outcome in `state`.
- **New rule `UiLayer.ViewModel.noPrivateVarProperties`.** A ViewModel must not declare
  `private var` properties — a mutable private field is a side channel around `state` and is lost on
  process death. Carry per-open context on the navigation itself (key fields, or `instance.metadata`
  via a `NavigationKey.MetadataKey`); put genuine UI state in `state`.
- **New construct `UiLayer.CompositionLocal`.** A top-level `Local…` val built via
  `compositionLocalOf` / `staticCompositionLocalOf` in a `..ui..` package is now a recognised
  construct (previously such a declaration matched no Construct and tripped `UiLayer` exhaustiveness).
  Its single rule — must be a dependency-access channel with an inert default, never a back door for
  mutable state — is review-enforced (unverifiable).
- **`ProjectRules.exceptionNeedsKdoc` is renamed to `ProjectRules.exceptionNeedsReason` and now
  requires a `reason` argument (a KDoc no longer suffices).** The prior version accepted a
  KDoc-style comment as the explanation; the explanation must now be the annotation's own non-blank
  `reason = "…"` argument — machine-readable, travelling with the annotation, and the natural form
  for a file-level `@file:ArchitectureException(reason = …)`. A declaration whose only explanation
  is a KDoc will newly fail. **The rule ID changed**, so any downstream
  `@ArchitectureException(ruleIds = ["ProjectRules.exceptionNeedsKdoc"])` referencing the old ID
  must be updated (in practice there are none — a declaration is never exempted from the rule that
  requires exceptions to explain themselves).
- **`ProjectRules.noDirectAsyncStateConstruction` now exempts `@Preview` functions.** A `@Preview`
  composable in production `commonMain` legitimately constructs sample `AsyncState.Loading/Success/Error`
  values for each previewed state. Constructions inside a `@Preview` function no longer count; the
  rule still flags direct construction in real code. Projects that carried file-level
  `@file:ArchitectureException` opt-outs purely for preview sample state can delete them.
- **`DomainInterface` primary-function types accept standard date/time value types.** `Instant`,
  `LocalDate`, `LocalDateTime`, `LocalTime`, `Duration`, `DatePeriod`, and `DateTimePeriod` (and
  their fully-qualified forms) are now domain-compatible, so a domain interface may name them
  directly. Behavioural relaxation — no downstream action.
- **`ServicesLayer.StorageClass` storage-return allowlist adds `Uuid`.** The postgres codegen emits
  `kotlin.uuid.Uuid` columns, so generated `XxxRow` types carry `Uuid`. The allowlist entry was
  `UUID`; it is now `Uuid`. No effect until a project has a `services.storage` module with a uuid
  column.
- **`invariantInitBlocks` is now plain guidance — its audit is dropped.** Both
  `DomainLayer.DomainObject.invariantInitBlocks` and `UiLayer.ViewModelState.invariantInitBlocks`
  carried an audit that flagged every domain object / state class without an `init` block. Most have
  no invariants to enforce, so the audit produced a large volume of findings that were noise rather
  than signal. The rules remain as guidance (the advice still stands where a type *does* have
  invariants); they simply no longer report. Both rule IDs are unchanged. No downstream action —
  projects will just see these audit findings disappear from `verifyArchitecture`.

## Detection

Re-sync the rule catalog under `platform/common/architecture`, then run `verifyArchitecture`. It
names each affected declaration:

- ViewModel functions failing `publicFunctionsReturnUnit` (now including default-visibility ones),
  `publicFunctionsNotSuspend`, or a `private var` failing `noPrivateVarProperties`.
- A `Local…` composition local failing `UiLayer.exhaustive` / `everyDeclarationBelongsToALayer`
  means the `CompositionLocal.kt` construct source was not synced.
- Files failing `noDirectAsyncStateConstruction` where the construction is inside a `@Preview`
  indicate the rule source predates this change.
- `@ArchitectureException` declarations failing `exceptionNeedsReason` — those whose only
  explanation is a KDoc, now that a non-blank `reason` argument is required.

## Migration

- Fix flagged ViewModels: convert a value-returning or `suspend` public function to launch into
  `viewModelScope` and reflect the outcome in `state`; move `private var` context onto the
  navigation (key fields or `instance.metadata`).
- Delete any file-level `@file:ArchitectureException` that opted out only for `@Preview` AsyncState
  sample state.
- Add a non-blank `reason = "…"` argument to every `@ArchitectureException` whose explanation was
  previously carried only in a KDoc comment; the annotation-level reason is now required. The KDoc
  can stay or go — it is no longer what the rule checks.
- No action for the date/time or `Uuid` relaxations. Rule IDs are unchanged, so no existing
  `@ArchitectureException` annotations need updating for those.

## Verification

```
./gradlew :platform:common:architecture:verifyArchitecture
```

Must be green, including the project's own ViewModels, composition locals, and preview composables.
