# ViewModel unit testing

The `enro-test` dependency, a ViewModel test pattern using `putNavigationHandleForViewModel` and
result-simulation APIs, and a test skeleton in the `ukpt-feature-slice` scaffold.

## What changed

**enro submodule repin.** The embedded enro submodule is repinned to a main that includes the
result-simulation API: `sendResultForTest`, `sendCompletedForTest`, and `sendClosedForTest`
extensions on `NavigationKey.Instance`. These let tests simulate a child destination completing,
closing, or returning a typed result to the ViewModel's `registerForNavigationResult` channel.

**`enro-test` catalog entry and dependency.** `libs.enro.test` is added to `gradle/libs.versions.toml`
(no version -- resolves through the composite `includeBuild` substitution like the other enro
artifacts). `:feature:core:client` and the `ukpt-feature-slice` `:client` template add it as a
`commonTest` dependency alongside `kotlinx-coroutinesTest`.

**ViewModel test pattern.** `UkptViewModelTest` in `:feature:core:client` demonstrates the full
pattern:

- `putNavigationHandleForViewModel<VM, K>(key)` pre-registers a `TestNavigationHandle` -- call it
  BEFORE constructing the ViewModel, because `by navigationHandle()` resolves at init time.
- `Dispatchers.setMain(UnconfinedTestDispatcher())` replaces Main so `viewModelScope` coroutines
  and result-channel observers execute eagerly.
- Each test wraps in `runEnroTest { }`, constructs the VM with hand-written fakes for domain
  interfaces, and asserts against `vm.state.value`.
- Teardown ordering is load-bearing: cancel each constructed ViewModel's `viewModelScope` BEFORE
  `Dispatchers.resetMain()`. A leaked collector subscribed to `pendingResults` would be woken by a
  later test's `registerResult` and dispatch to a Main dispatcher that no longer exists on targets
  without a default one. Clear `pendingResults` between tests.

**Test file reorganization.** The domain and infrastructure tests previously in `UkptViewModelTest.kt`
(`FlowOfGreetingSummaryImplTest`, `GreetingRepositoryTest`, `AsyncStateLoadTest`) are moved to files
named after the class under test.

**Updated `ukpt-feature-slice` templates.** The `:client` build template includes `libs.enro.test` in
`commonTest`. A new templates.md section (SS7) provides a minimal ViewModel test skeleton with the
`putNavigationHandleForViewModel` + `setMain` + tracked-teardown pattern.

## Detection

```bash
./gradlew :feature:core:client:jvmTest
```

## Migration

Add `enro-test` to the version catalog and to each feature client module's `commonTest` dependencies.
Copy the test pattern from `:feature:core:client`'s `UkptViewModelTest` or the `ukpt-feature-slice`
template skeleton. The teardown ordering (scope cancel before `resetMain`) is non-negotiable -- test
isolation fails without it.

## Verification

```bash
./gradlew :feature:core:client:jvmTest
./gradlew verifyArchitecture
./gradlew validateTemplate
```
