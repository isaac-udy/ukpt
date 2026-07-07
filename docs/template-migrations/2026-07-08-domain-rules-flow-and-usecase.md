# Domain rules: `Flow<T>` returns and cross-module use cases

Two `domain` architecture rules were rejecting patterns the catalog itself prescribes. Both are
fixed in the rule sources under `platform/common/architecture`; the fixes are behavioural — no rule
ID changed.

- **`DomainInterface.primaryReturnType` / `primaryParameterTypes` now accept a reactive wrapper.**
  A `FlowOf…` interface whose primary function returns `Flow<T>` (also `StateFlow`/`SharedFlow`) of
  a domain-compatible `T` is now domain-compatible. Previously the type check treated `Flow` as an
  unknown non-domain type and reported a violation, contradicting the construct's own requirement
  that "a Domain Interface declares all functions as `suspend` or returning a `Flow<T>`". The
  wrapper's type argument is still validated recursively, so `Flow<Session?>` and
  `Flow<List<Report>>` pass while `Flow<android.view.View>` still fails.
- **The `UseCase` construct now matches a real `[DomainInterface]Impl`.** A use case is matched by
  name — a `<X>Impl` class with exactly one parent named `<X>` — instead of by resolving the parent
  reference to its interface declaration. In the normal layout the interface lives in the sibling
  `:api` module and the `…Impl` in `:client`/`:server`, where the parent could not be resolved from
  the class alone; the old check always returned no match, so every `…Impl` matched no Construct and
  tripped `DomainLayer.exhaustive` and `everyDeclarationBelongsToALayer`. The
  `DomainInterface.implementedByRepositoryOrUseCase` rule still carries the "`<X>` is really a domain
  interface" guarantee.

## Detection

The project is affected if it has either shape and adopted `2026-07-03.1` (or an earlier version
carrying these domain rules):

- `FlowOf…` domain interfaces returning `Flow<T>` (e.g. `fun interface FlowOfSession { operator fun invoke(): Flow<Session?> }`)
  were failing `DomainLayer.DomainInterface.primaryReturnType`.
- `…Impl` use-case classes implementing a domain `fun interface` from the sibling `:api` module
  (e.g. `internal class SignOutImpl(...) : SignOut`) were failing
  `everyDeclarationBelongsToALayer` / `DomainLayer.exhaustive` (matched no Construct).

## Migration

None for downstream code — this is a rule **fix**. Re-syncing the corrected rule catalog under
`platform/common/architecture` (carried by the file sync) is sufficient. Rule IDs are unchanged, so
no `@ArchitectureException` annotations need updating.

The sync also adds a domain worked example to `:feature:core` — a `Greeting` domain object, plain
and `FlowOf…` domain interfaces in `:api` (`GetGreeting`, `FlowOfGreetings`, `FlowOfLatestGreeting`,
`FlowOfGreetingHistory`), and their `…Impl` use cases in `:client` — so both rule paths are exercised
by the template's own `verifyArchitecture`. Downstream projects with their own domain code can keep
or drop this example.

## Verification

```
./gradlew :platform:common:architecture:verifyArchitecture
```

Must be green, including the project's own `FlowOf…` interfaces and `…Impl` use cases.
