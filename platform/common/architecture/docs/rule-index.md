> [!NOTE]
> **This file is generated — do not edit it by hand.**
> Generated entirely from the rule catalog.
> Regenerate with `UPDATE_ARCHITECTURE_DOCS=true ./gradlew :platform:common:architecture:test`.

# Rule index

The complete catalog, one row per construct or rule. Ids are object/property paths (see the [README](../README.md)). `tested` = executable check · `construct` = classification requirements · `guidance` = documented convention · `codegen` = delegated to code generation.

| Rule | Enforcement | Statement |
| --- | --- | --- |
| `ModuleRules.featureNotApp` | tested | A `:feature` module must never depend on an `:app` module |
| `ModuleRules.featureMayUsePlatform` | guidance | A `:feature` module may depend on `:platform` modules |
| `ModuleRules.clientApiOnly` | tested | A `:feature:[name]:client` module must never depend on another `:client`/`:server` module |
| `ModuleRules.clientMayUseApi` | tested | A `:feature:[name]:client` module may depend on any `:feature:[name]:api` module |
| `ModuleRules.serverApiOnly` | tested | A `:feature:[name]:server` module must never depend on another `:client`/`:server` module |
| `ModuleRules.serverMayUseApi` | tested | A `:feature:[name]:server` module may depend on any `:feature:[name]:api` module |
| `ModuleRules.apiMayUseApi` | guidance | A `:feature:[name]:api` module may depend on another feature's `:api` module to share models |
| `ModuleRules.featuresMayBeGrouped` | guidance | A `:feature` module may be grouped (`:feature:[group]:[name]:…`) |
| `ModuleRules.platformNotApp` | tested | A `:platform` module must never depend on an `:app` module |
| `ModuleRules.platformNotFeature` | tested | A `:platform` module must never depend on a `:feature` module |
| `ModuleRules.platformMayUsePlatform` | guidance | A `:platform` module may depend on other `:platform` modules |
| `DomainLayer.DomainInterface` | construct | resides in `feature..domain..` · is a `fun interface` · has a primary function that is an `operator fun invoke` · declares all functions as `suspend` or returning a `Flow<T>` · is prefixed with `FlowOf` when its primary function returns a `Flow` |
| `DomainLayer.DomainInterface.interfaceDefaults` | guidance | A Domain Interface may define additional default functions that call the primary function |
| `DomainLayer.DomainInterface.primaryParameterTypes` | guidance | A Domain Interface's primary-function parameters must be domain objects, nested types, primitives, or collections of those |
| `DomainLayer.DomainInterface.primaryReturnType` | guidance | A Domain Interface's primary-function return type must be domain objects, nested types, primitives, collections of those, or no value |
| `DomainLayer.DomainInterface.implementedByRepositoryOrUseCase` | guidance | A Domain Interface must be implemented by a Repository (as a property) or by a UseCase |
| `DomainLayer.DomainInterface.errorsViaExceptions` | tested | A Domain Interface's functions propagate errors via thrown exceptions, never via the return type |
| `DomainLayer.DomainObject` | construct | resides in `feature..domain..` · is a class or interface · satisfies one of: {is `sealed`, is a `data class`, is an `enum class`, is a `value class`} · is annotated with `@Serializable` |
| `DomainLayer.DomainObject.immutable` | tested | A Domain Object must be immutable (val properties only) |
| `DomainLayer.DomainObject.nestedValueClassIds` | guidance | A Domain Object should use nested value classes for identifiers where appropriate |
| `DomainLayer.DomainObject.sealedHierarchies` | guidance | A Domain Object should use sealed interface hierarchies to model polymorphic data where appropriate |
| `DomainLayer.DomainObject.invariantInitBlocks` | guidance | A Domain Object should include `init` blocks that enforce invariants |
| `DomainLayer.DomainObject.nestedTypes` | guidance | A Domain Object should use nested types when conceptually inseparable from the parent |
| `DomainLayer.UseCase` | construct | resides in `feature..domain..` · is a non-sealed/data/enum/value class named `[DomainInterface]Impl` · implements exactly one domain interface |
| `DomainLayer.UseCase.noMutableState` | tested | A UseCase must not contain mutable state — all properties are `val` |
| `DomainLayer.UseCase.noOverridingDefaults` | tested | A UseCase must not override any default function of its domain interface |
| `DomainLayer.UseCase.mayInjectDomainInterfaces` | guidance | A UseCase may inject domain interfaces to perform its logic |
| `DomainLayer.UseCase.breakDownComplexUseCases` | guidance | A UseCase that becomes too complex should be broken into private/file-private/nested parts |
| `DomainLayer.DomainException` | construct | resides in `feature..domain..` · is a class extending RuntimeException/Exception/PresentableException |
| `DomainLayer.DomainConstants` | construct | resides in `feature..domain..` · is an `object` with only `val` properties and no functions |
| `DomainLayer.DomainExtensionFunction` | construct | resides in `feature..domain..` · has receiver/return/parameter types that are domain objects, primitives, or collections of those |
| `DomainLayer.DomainExtensionFunction.noPlatformDeps` | tested | A Domain Extension Function must not introduce platform-specific dependencies |
| `DomainLayer.DomainExtensionProperty` | construct | resides in `feature..domain..` · has a receiver/type that is a domain object, primitive, or collection of those |
| `DomainLayer.noPlatformDeps` | tested | The `domain` layer must not contain platform-specific dependencies (Android, Ktor, SQL, …) |
| `DomainLayer.noUiDataServicesDeps` | tested | The `domain` layer must not depend on `ui`, `data`, or `services` packages within the feature |
| `DomainLayer.crossFeatureViaApi` | tested | The `domain` layer may depend on another feature's `domain` only via that feature's `:api` module |
| `DomainLayer.exhaustive` | tested | Every top-level declaration in `feature..domain..` matches exactly one construct |
| `UiLayer.Screen` | construct | resides in `feature..ui..` · is bound to its Destination via the `@NavigationDestination` annotation · is named `[Name]Screen` (property-based screens may end in `Screen` or `Destination`) · has a single parameter — the associated `[Name]ViewModel` (property form exempt) |
| `UiLayer.Screen.composableFunction` | tested | A Screen function must be annotated with `@Composable` |
| `UiLayer.Screen.viewModelStateRelationship` | guidance | A Screen function has a 1:1 relationship with a ViewModel and ViewModel State |
| `UiLayer.Screen.observesState` | guidance | A Screen function must observe the ViewModel's `state` property and use it to drive the UI |
| `UiLayer.Screen.delegatesInteraction` | guidance | A Screen function should delegate all user interaction handling to the ViewModel |
| `UiLayer.Screen.overlayViaDsl` | guidance | A dialog/overlay Screen must use the `navigationDestination` DSL with `metadata = { directOverlay() }` |
| `UiLayer.Screen.overlayViewModel` | guidance | A dialog/overlay Screen that needs a ViewModel should call `viewModel()` inside the `navigationDestination` block |
| `UiLayer.Screen.screenContentCompanion` | tested | A Screen function must be paired with an `internal [Name]ScreenContent` composable in the same file |
| `UiLayer.Screen.viewModelInjection` | tested | A ViewModel must be injected into its Screen using `viewModel()`, not `koinViewModel()` |
| `UiLayer.Composable` | construct | resides in `feature..ui..` · is not a Screen · is annotated `@Composable` |
| `UiLayer.Composable.screenContentSnapshotTest` | tested | A `[Name]ScreenContent` composable must be exercised by at least one snapshot test |
| `UiLayer.Destination` | construct | resides in `feature..ui..` · is a class or object · implements `dev.enro.NavigationKey` or `NavigationKey.WithResult<T>` · is named `[Name]Destination` · is annotated `@Serializable` · is declared in a file matching its name |
| `UiLayer.Destination.minimalData` | guidance | A Destination should accept the minimal data required to initialise the associated Screen |
| `UiLayer.Destination.definedInApiOrClient` | tested | A Destination may live in `:api` (shared entry point / server-driven) or `:client` (internal only) |
| `UiLayer.ViewModel` | construct | resides in `feature..ui..` · extends `androidx.lifecycle.ViewModel` · is named `[Name]ViewModel` · declares its `state` property as a `ViewModelState<[Name]State>` (1:1 with the ViewModel's State type) · has a `private val navigation` obtained via `navigationHandle<[Name]Destination>()` · is declared in a file matching its name |
| `UiLayer.ViewModel.singlePublicStateProperty` | tested | A ViewModel exposes a single public `state` property, or no public properties at all |
| `UiLayer.ViewModel.publicFunctionsReturnUnit` | tested | A ViewModel's `public`/`internal` functions must only return `Unit` (or omit a return type) |
| `UiLayer.ViewModel.injectsDomainInterfaces` | guidance | A ViewModel should inject domain interfaces to load and manipulate domain objects |
| `UiLayer.ViewModel.usesJobManager` | tested | A ViewModel must use `JobManager` to manage coroutines — never hold `var job: Job?` references |
| `UiLayer.ViewModelState` | construct | resides in `feature..ui..` · is a class · is a `data class` · is named `[Name]State` · is declared in a file matching its name |
| `UiLayer.ViewModelState.immutable` | tested | A ViewModel State object must be immutable (val properties only) |
| `UiLayer.ViewModelState.viewModelRelationship` | guidance | A ViewModel State object has a 1:1 relationship with a ViewModel type |
| `UiLayer.ViewModelState.usesAsyncState` | guidance | A ViewModel State object must use `AsyncState<T>` / `UpdatableState<T>` for asynchronously loaded data and action progress |
| `UiLayer.ViewModelState.noCustomAsyncSealedTypes` | tested | A ViewModel State object must not define custom sealed types for loading/success/error — use `AsyncState<T>` |
| `UiLayer.ViewModelState.transparentContainer` | guidance | A ViewModel State object should be a transparent container for domain objects, not a lossy UI-level mapping |
| `UiLayer.ViewModelState.invariantInitBlocks` | guidance | A ViewModel State object should include `init` blocks that enforce invariants |
| `UiLayer.ViewModelState.formattingInScreen` | guidance | A ViewModel State object's formatting and visual representation must be handled by the Screen or specialized `@Composable` properties/functions |
| `UiLayer.UiValueType` | construct | resides in `feature..ui..` · satisfies one of: {is an `enum class`, is `sealed`} · has no member functions |
| `UiLayer.mayDependOnDomain` | guidance | The `ui` layer may depend on `domain` |
| `UiLayer.noImplementingDomainInterfaces` | tested | The `ui` layer is forbidden from implementing `domain` interfaces |
| `UiLayer.noDataServicesDeps` | tested | The `ui` layer is forbidden from depending on `data` or `services` |
| `UiLayer.noKoinInject` | tested | The `ui` layer must not use `koinInject` — all dependencies are injected through ViewModels |
| `UiLayer.exhaustive` | tested | Every top-level declaration in `feature..ui..` matches exactly one construct |
| `DataLayer.Repository` | construct | resides in `feature..data..` · is a class · is named `[Name]Repository` · is declared in a file matching its name |
| `DataLayer.Repository.internalVisibility` | tested | A Repository must be marked as `internal` |
| `DataLayer.Repository.doesNotImplementDomainInterfaces` | tested | A Repository must not implement domain interfaces directly |
| `DataLayer.Repository.exposesDomainInterfacesAsProperties` | tested | A Repository must expose domain interfaces as `public val` properties |
| `DataLayer.Repository.doesNotInjectDomainInterfaces` | tested | A Repository must not inject domain interfaces |
| `DataLayer.Repository.doesNotInjectRepositories` | tested | A Repository must not inject other Repositories |
| `DataLayer.Repository.propertiesEagerlyInitialized` | tested | A Repository's domain-interface properties must be initialized immediately — no `by lazy`, no custom getter |
| `DataLayer.Repository.mayInjectServicesStorageOrClients` | guidance | A Repository may inject Services, client-side `data.storage` Storage objects, or database clients to fulfill its domain properties |
| `DataLayer.ClientDataInterface` | construct | resides in `feature..data..` · is an interface · resides in `feature.[name].data` (not `data.storage`) |
| `DataLayer.ClientDataImplementation` | construct | resides in `feature..data..` · is a class · is not named `[Name]Repository` · resides in `feature.[name].data` (not `data.storage`) |
| `DataLayer.ClientStorage` | construct | resides in `feature..data..` · is a class · is named `[Name]Storage` · is not abstract · is not a `data class` · resides in the `data.storage` package on `:client` |
| `DataLayer.ClientStorage.internalVisibility` | tested | A Storage class must be marked as `internal` (the `expect` declaration may be public, but `actual` implementations should be `internal` where the language allows) |
| `DataLayer.ClientStorage.doesNotInjectDomainRepositoriesOrServices` | tested | A Storage class must not inject domain interfaces, Repositories, or Services |
| `DataLayer.providesDomainImplementations` | tested | The `data` layer provides implementations of `domain` interfaces — by exposing them as properties, not by inheriting them |
| `DataLayer.noInjectingDomainInterfaces` | tested | A `data` class must not inject `domain` interfaces — logic requiring multiple domain interfaces must be moved to a UseCase |
| `DataLayer.storageInternalVisibility` | tested | A `data.storage` class uses `internal` visibility where the language allows (see `DataLayer.ClientStorage.internalVisibility` for the canonical statement, incl. the `expect`/`actual` nuance) |
| `DataLayer.noUiDeps` | tested | The `data` layer must not depend on the `ui` package |
| `DataLayer.exhaustive` | tested | Every top-level declaration in `feature..data..` matches exactly one construct |
| `ServicesLayer.ServiceInterface` | construct | resides in `feature..services..` · is an `interface` annotated `@Urpc` · is named `[Name]Service` · resides in the top-level `feature.[name].services` package |
| `ServicesLayer.ServiceInterface.noClientOnlyServices` | guidance | A Service must always be implemented as urpc service functions in the appropriate server module — never as a client-only local service |
| `ServicesLayer.ServiceInterface.plainFunctionShapes` | tested | A Service function is a plain `suspend fun f(req): Res`, `fun f(req): Flow<Res>`, or `fun f(reqs: Flow<Req>): Flow<Res>`, taking 0 or 1 parameter |
| `ServicesLayer.ServiceInterface.nestedRequestResponseTypes` | guidance | A Service function's `Request`/`Response` types are nested `@Serializable` types grouped under a per-function `object` namespace |
| `ServicesLayer.ServiceInterface.contractLivesInApi` | tested | A Service interface lives in `feature.[name].services` of the `:api` module |
| `ServicesLayer.ServiceInterface.errorsViaExceptions` | tested | A Service function propagates errors via thrown exceptions; the return type only ever represents a successful result |
| `ServicesLayer.ServiceImpl` | construct | resides in `feature..services..` · is named `[Name]ServiceImpl`, matching its `[Name]Service` contract · resides in `feature.[name].services` of the `:server` module (dual-life with the contract) |
| `ServicesLayer.ServiceImpl.internalVisibility` | tested | A Service implementation must be `internal` |
| `ServicesLayer.ServiceImpl.noInjectingDomainInterfaces` | tested | A Service implementation must not inject domain interfaces |
| `ServicesLayer.ServiceImpl.mayInjectStorageAndInternal` | guidance | A Service implementation may inject `services.storage` Storage classes and `services.internal` orchestrators of the same feature, plus other features' Service contracts via `:api` |
| `ServicesLayer.ServiceImpl.noUiDependency` | tested | A Service implementation must not depend on the `ui` package |
| `ServicesLayer.InternalCoordinator` | construct | resides in `feature..services..` · is a concrete (non-`abstract`, non-`data`) class that is not a `Job` or `Exception` · resides in `feature.[name].services.internal` |
| `ServicesLayer.InternalDataCarrier` | construct | resides in `feature..services..` · is a `data class` payload that flows between subsystems through the orchestrator · resides in `feature.[name].services.internal` |
| `ServicesLayer.InternalInterface` | construct | resides in `feature..services..` · is an interface · resides in `feature.[name].services.internal` |
| `ServicesLayer.InternalException` | construct | resides in `feature..services..` · is a class named `[Name]Exception`, thrown only by internal helpers · resides in `feature.[name].services.internal` |
| `ServicesLayer.InternalObjectHelper` | construct | resides in `feature..services..` · is an object · resides in `feature.[name].services.internal` |
| `ServicesLayer.StorageClass` | construct | resides in `feature..services..` · is named `[Name]Storage` (or `[Name]Store` where the broader name fits) · is not abstract and not a `data class` · resides in `feature.[name].services.storage` |
| `ServicesLayer.StorageClass.internalVisibility` | tested | A Storage class must be `internal` |
| `ServicesLayer.StorageClass.returnsRowTypesOnly` | tested | A Storage class must take and return `XxxRow` types only — never domain types |
| `ServicesLayer.StorageClass.partialUpdatesByHand` | guidance | A Storage operation that touches only a subset of columns keeps the hand-written `update { … it[col] = value … }` block — `setFromRow` writes every column and is wrong here |
| `ServicesLayer.StorageRecord` | construct | resides in `feature..services..` · is a `data class` · satisfies one of: {is named `[Name]Row`, is named `[Name]Record`, is named `[Name]Insert`} · resides in `feature.[name].services.storage` |
| `ServicesLayer.MappingFunction` | construct | resides in `feature..services..` · is a function · resides in `feature.[name].services.storage` |
| `ServicesLayer.MappingFunction.mappersInStorage` | guidance | A Mapping Function between a generated `XxxRow` and a domain type lives in `services.storage` as a plain `internal fun` declaration, conventionally collected in `[Name]Mappers.kt` |
| `ServicesLayer.MappingFunction.multiTableLoadHelpers` | guidance | A storage operation that spans multiple tables to assemble a richer record is defined as a higher-level `suspend fun [Name]Storage.loadXxx(…)` extension in `services.storage` |
| `ServicesLayer.CodecObject` | construct | resides in `feature..services..` · is an object · lives in `services.storage` alongside the Row + mapping functions for the table that uses it |
| `ServicesLayer.CodecObject.keyedToColumn` | guidance | A Codec encapsulates the read/write asymmetry `setFromRow` can't express — it stays small and keyed to the column it serves |
| `ServicesLayer.mustNotDependOnData` | tested | The `services` layer may depend on `domain` and on other features' `:api` `services` contracts; it must not depend on `data` |
| `ServicesLayer.crossFeatureViaApi` | tested | The `services` layer may depend on another feature's `services` only via that feature's `:api` module |
| `ServicesLayer.internalHierarchicalVisibility` | tested | A class in `services.internal.<subsystem>.**` may not import from a different subsystem under `services.internal` (ancestor data-shape imports are allowed) |
| `ServicesLayer.storageMustNotDependOnInternal` | tested | A `services.storage` file must not import from `services.internal` — the dependency direction inside `services` is `internal → storage` |
| `ServicesLayer.toolsApiContractOnly` | tested | A declaration placed in `services.tools` may depend on the Service contract via `:api`-defined types only — never on `services.storage` or `services.internal` |
| `ServicesLayer.generatedTableRowSources` | codegen | A `Table`/`Row` source is generated by the `dev.isaacudy.udytils.postgres` plugin from the Flyway-migrated schema, into the shared package `platform.server.postgres.tables` |
| `ServicesLayer.generatedTableObjects` | codegen | A persisted entity has a generated `object XxxTable : Table("xxx")` (plural); custom columns use the udytils column types (`JsonbColumnType`, `TextArrayColumnType`, …) |
| `ServicesLayer.everyColumnOnTable` | codegen | A generated `Table` object declares every column on the SQL table, with no omissions; the UUID primary key is `uuid("id").autoGenerate()` but the write path always supplies the id explicitly |
| `ServicesLayer.rowDataClassPrimitives` | codegen | The in-memory persistence shape is a top-level `data class XxxRow` (singular) whose fields use only primitive types — no domain wrappers, enums, or sealed hierarchies |
| `ServicesLayer.rowFakeConstructorAndSetFromRow` | codegen | A generated file exposes a fake-constructor `fun XxxRow(row: ResultRow): XxxRow` for reads, and a `fun UpdateBuilder<*>.setFromRow(row: XxxRow)` extension for writes |
| `ServicesLayer.exhaustive` | tested | Every top-level declaration in `feature..services..` matches exactly one construct |
| `FeatureRules.DependencyModule` | construct | resides in the top-level `feature.[name]` package of a `:client` or `:server` module · is a property · is named `[Name]Dependencies` |
| `FeatureRules.DependencyModule.ownFeatureBindingsOnly` | tested | A Dependency Module must only bind/provide dependencies that are both defined and implemented in its own feature |
| `FeatureRules.DependencyModule.urpcServiceBinding` | tested | A Dependency Module registers a service's generated `[Name]ServiceUrpcBinding` by chaining `.bindService(::[Name]ServiceUrpcBinding)` off the implementation's binding, inside the per-call `scope<UrpcCall> { }` block |
| `FeatureRules.DependencyModuleHelper` | construct | is a function · is `internal` · has a Koin `Module` receiver |
| `FeatureRules.constructorReferenceBindings` | tested | A DI binding must use the constructor reference style `singleOf(::Constructor).bind(BindingType::class)`, not the lambda style `single<BindingType> { Constructor(get()) }` |
| `ProjectRules.noCatchException` | tested | A `try/catch` block must never catch `Exception` — use `catch (t: Throwable)` or a specific exception type |
| `ProjectRules.serviceExceptionsSerializable` | tested | An exception type defined in `services` (the cross-the-wire contract) must be annotated with `@Serializable` |
| `ProjectRules.noWildcardImports` | tested | An import must not use a wildcard — always list the explicit symbols |
| `ProjectRules.noDirectAsyncStateConstruction` | tested | An `AsyncState` must never be constructed directly via `Loading`/`Success`/`Error` — use `AsyncState.fromSuspending`/`fromFlow` |
| `ProjectRules.sealedActionVariants` | guidance | An action/request type must model its variants as a `sealed interface`/`sealed class` (each variant a `data class`), not as a single type with an `enum` discriminator and nullable fields |
| `ProjectRules.exceptionsNeedHumanSignOff` | guidance | An architecture exception may only be added after discussing the exception with a human author |
| `ProjectRules.exceptionNotForFailingTests` | guidance | An architecture exception is not a valid way to resolve an immediate architecture-test failure without user feedback — fix the code or the rule first |
| `ProjectRules.exceptionNeedsKdoc` | guidance | An architecture exception must include a KDoc-style (`/** ... */`) comment explaining why it exists and the intended resolution |
| `ProjectRules.exceptionsAreTemporary` | guidance | An architecture exception is temporary — revisit it periodically and remove it once the underlying issue is resolved |
| `architecture.everyDeclarationBelongsToALayer` | tested | Every feature-module declaration matches exactly one construct across all layers |
