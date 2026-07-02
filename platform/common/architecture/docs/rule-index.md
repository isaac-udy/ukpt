> [!NOTE]
> **This file is generated — do not edit it by hand.**
> Generated entirely from the rule catalog.
> Regenerate with `UPDATE_ARCHITECTURE_DOCS=true ./gradlew :platform:common:architecture:test`.

# Rule index

The complete catalog, one row per construct or rule. Ids are object/property paths (see the [README](../README.md)). `tested` = executable check · `construct` = classification requirements · `guidance` = documented convention · `codegen` = delegated to code generation.

| Rule | Enforcement | Statement |
| --- | --- | --- |
| `ModuleRules.featureNotApp` | tested | `:feature` modules must never depend on `:app` modules |
| `ModuleRules.featureMayUsePlatform` | guidance | `:feature` modules may depend on `:platform` modules |
| `ModuleRules.clientApiOnly` | tested | `:feature:[name]:client` must never depend on another `:client`/`:server` module |
| `ModuleRules.clientMayUseApi` | tested | `:feature:[name]:client` may depend on any `:feature:[name]:api` module |
| `ModuleRules.serverApiOnly` | tested | `:feature:[name]:server` must never depend on another `:client`/`:server` module |
| `ModuleRules.serverMayUseApi` | tested | `:feature:[name]:server` may depend on any `:feature:[name]:api` module |
| `ModuleRules.apiMayUseApi` | guidance | `:feature:[name]:api` may depend on another feature's `:api` module to share models |
| `ModuleRules.featuresMayBeGrouped` | guidance | `:feature` modules may be grouped (`:feature:[group]:[name]:…`) |
| `ModuleRules.platformNotApp` | tested | `:platform` modules must never depend on `:app` modules |
| `ModuleRules.platformNotFeature` | tested | `:platform` modules must never depend on `:feature` modules |
| `ModuleRules.platformMayUsePlatform` | guidance | `:platform` modules may depend on other `:platform` modules |
| `DomainLayer.DomainInterface` | construct | resides in `feature..domain..` · Domain interfaces must be a `fun interface` · The primary function of a domain interface must be an `operator fun invoke` · All functions in a domain interface must be `suspend` or return a `Flow<T>` · Flow-returning domain interfaces are prefixed with `FlowOf` |
| `DomainLayer.DomainInterface.interfaceDefaults` | guidance | May define additional default functions that call the primary function |
| `DomainLayer.DomainInterface.primaryParameterTypes` | guidance | Primary-function parameters must be domain objects, nested types, primitives, or collections of those |
| `DomainLayer.DomainInterface.primaryReturnType` | guidance | Primary-function return type must be domain objects, nested types, primitives, collections of those, or no value |
| `DomainLayer.DomainInterface.implementedByRepositoryOrUseCase` | guidance | Must be implemented by a Repository (as a property) or by a UseCase |
| `DomainLayer.DomainInterface.errorsViaExceptions` | tested | Functions propagate errors via thrown exceptions, never via the return type |
| `DomainLayer.DomainObject` | construct | resides in `feature..domain..` · is a class or interface · one of {is `sealed`, is a `data class`, is an `enum class`, is a `value class`} · Domain objects must be annotated with `@Serializable` |
| `DomainLayer.DomainObject.immutable` | tested | Domain objects must be immutable (val properties only) |
| `DomainLayer.DomainObject.nestedValueClassIds` | guidance | Should use nested value classes for identifiers where appropriate |
| `DomainLayer.DomainObject.sealedHierarchies` | guidance | Should use sealed interface hierarchies to model polymorphic data where appropriate |
| `DomainLayer.DomainObject.invariantInitBlocks` | guidance | Should include `init` blocks that enforce invariants |
| `DomainLayer.DomainObject.nestedTypes` | guidance | Should use nested types when conceptually inseparable from the parent |
| `DomainLayer.UseCase` | construct | resides in `feature..domain..` · A UseCase is a non-sealed/data/enum/value class named `[DomainInterface]Impl` · A UseCase must implement exactly one domain interface |
| `DomainLayer.UseCase.noMutableState` | tested | A UseCase must not contain mutable state — all properties are `val` |
| `DomainLayer.UseCase.noOverridingDefaults` | tested | Must not override any default function of its domain interface |
| `DomainLayer.UseCase.mayInjectDomainInterfaces` | guidance | May inject domain interfaces to perform its logic |
| `DomainLayer.UseCase.breakDownComplexUseCases` | guidance | If it becomes too complex, break it into private/file-private/nested parts |
| `DomainLayer.DomainException` | construct | resides in `feature..domain..` · A domain exception is a class extending RuntimeException/Exception/PresentableException |
| `DomainLayer.DomainConstants` | construct | resides in `feature..domain..` · Domain constants are an `object` with only `val` properties and no functions |
| `DomainLayer.DomainExtensionFunction` | construct | resides in `feature..domain..` · Receiver/return/parameter types are domain objects, primitives, or collections of those |
| `DomainLayer.DomainExtensionFunction.noPlatformDeps` | tested | Domain extension functions must not introduce platform-specific dependencies |
| `DomainLayer.DomainExtensionProperty` | construct | resides in `feature..domain..` · Receiver/type is a domain object, primitive, or collection of those |
| `DomainLayer.noPlatformDeps` | tested | Domain must not contain platform-specific dependencies (Android, Ktor, SQL, …) |
| `DomainLayer.noUiDataServicesDeps` | tested | Domain must not depend on `ui`, `data`, or `services` packages within the feature |
| `DomainLayer.crossFeatureViaApi` | tested | May depend on another feature's `domain` only via that feature's `:api` module |
| `DomainLayer.exhaustive` | tested | Every top-level declaration in `feature..domain..` matches exactly one construct |
| `UiLayer.Screen` | construct | resides in `feature..ui..` · Screen functions/properties must be bound to their Destination via the `@NavigationDestination` annotation · Screen functions are named `[Name]Screen`; property-based screens end in `Screen` or `Destination` · Screen functions must have a single parameter — the associated `[Name]ViewModel` |
| `UiLayer.Screen.composableFunction` | tested | Screen functions must be annotated with `@Composable` |
| `UiLayer.Screen.viewModelStateRelationship` | guidance | Screen functions have a 1:1 relationship with a ViewModel and ViewModel State |
| `UiLayer.Screen.observesState` | guidance | Screen functions must observe the ViewModel's `state` property and use it to drive the UI |
| `UiLayer.Screen.delegatesInteraction` | guidance | Screen functions should delegate all user interaction handling to the ViewModel |
| `UiLayer.Screen.overlayViaDsl` | guidance | Dialog/overlay screens must use the `navigationDestination` DSL with `metadata = { directOverlay() }` |
| `UiLayer.Screen.overlayViewModel` | guidance | Dialog/overlay screens that need a ViewModel should call `viewModel()` inside the `navigationDestination` block |
| `UiLayer.Screen.screenContentCompanion` | tested | Screen functions must be paired with an `internal [Name]ScreenContent` composable in the same file |
| `UiLayer.Screen.viewModelInjection` | tested | ViewModels must be injected into screens using `viewModel()`, not `koinViewModel()` |
| `UiLayer.Composable` | construct | resides in `feature..ui..` · Is not a Screen · annotated `@Composable` |
| `UiLayer.Composable.screenContentSnapshotTest` | tested | Every `[Name]ScreenContent` composable must be exercised by at least one snapshot test |
| `UiLayer.Destination` | construct | resides in `feature..ui..` · is a class or object · Destinations must implement `dev.enro.NavigationKey` or `NavigationKey.WithResult<T>` · name ends with `Destination` · annotated `@Serializable` · is declared in a file matching its name |
| `UiLayer.Destination.minimalData` | guidance | Destinations should accept the minimal data required to initialise the associated Screen |
| `UiLayer.Destination.definedInApiOrClient` | tested | Destinations may live in `:api` (shared entry point / server-driven) or `:client` (internal only) |
| `UiLayer.ViewModel` | construct | resides in `feature..ui..` · ViewModels extend `androidx.lifecycle.ViewModel` · ViewModels must be named `[Name]ViewModel` · The `state` property is a `ViewModelState<[Name]State>` (1:1 with the ViewModel's State type) · ViewModels have a `private val navigation` obtained via `navigationHandle<[Name]Destination>()` · is declared in a file matching its name |
| `UiLayer.ViewModel.singlePublicStateProperty` | tested | ViewModels expose a single public `state` property, or no public properties at all |
| `UiLayer.ViewModel.publicFunctionsReturnUnit` | tested | `public`/`internal` functions on a ViewModel must only return `Unit` (or omit a return type) |
| `UiLayer.ViewModel.injectsDomainInterfaces` | guidance | ViewModels should inject domain interfaces to load and manipulate domain objects |
| `UiLayer.ViewModel.usesJobManager` | tested | ViewModels must use `JobManager` to manage coroutines — never hold `var job: Job?` references |
| `UiLayer.ViewModelState` | construct | resides in `feature..ui..` · is a class · is a `data class` · name ends with `State` · is declared in a file matching its name |
| `UiLayer.ViewModelState.immutable` | tested | ViewModel State objects must be immutable (val properties only) |
| `UiLayer.ViewModelState.viewModelRelationship` | guidance | ViewModel State objects have a 1:1 relationship with a ViewModel type |
| `UiLayer.ViewModelState.usesAsyncState` | guidance | ViewModel State objects must use `AsyncState<T>` / `UpdatableState<T>` for asynchronously loaded data and action progress |
| `UiLayer.ViewModelState.noCustomAsyncSealedTypes` | tested | ViewModel State objects must not define custom sealed types for loading/success/error — use `AsyncState<T>` |
| `UiLayer.ViewModelState.transparentContainer` | guidance | ViewModel State objects should be a transparent container for domain objects, not lossy UI-level mappings |
| `UiLayer.ViewModelState.invariantInitBlocks` | guidance | ViewModel State objects should include `init` blocks that enforce invariants |
| `UiLayer.ViewModelState.formattingInScreen` | guidance | Formatting and visual representation must be handled by the Screen or specialized `@Composable` properties/functions |
| `UiLayer.UiValueType` | construct | resides in `feature..ui..` · one of {is an `enum class`, is `sealed`} · Has no member functions |
| `UiLayer.mayDependOnDomain` | guidance | May depend on `domain` |
| `UiLayer.noImplementingDomainInterfaces` | tested | Forbidden from implementing `domain` interfaces |
| `UiLayer.noDataServicesDeps` | tested | Forbidden from depending on `data` or `services` |
| `UiLayer.noKoinInject` | tested | Must not use `koinInject` — all dependencies are injected through ViewModels |
| `UiLayer.exhaustive` | tested | Every top-level declaration in `feature..ui..` matches exactly one construct |
| `DataLayer.Repository` | construct | resides in `feature..data..` · is a class · name ends with `Repository` · is declared in a file matching its name |
| `DataLayer.Repository.internalVisibility` | tested | Repositories must be marked as `internal` |
| `DataLayer.Repository.doesNotImplementDomainInterfaces` | tested | Repositories must not implement domain interfaces directly |
| `DataLayer.Repository.exposesDomainInterfacesAsProperties` | tested | Repositories must expose domain interfaces as `public val` properties |
| `DataLayer.Repository.doesNotInjectDomainInterfaces` | tested | Repositories are forbidden from injecting domain interfaces |
| `DataLayer.Repository.doesNotInjectRepositories` | tested | Repositories are forbidden from injecting other Repositories |
| `DataLayer.Repository.propertiesEagerlyInitialized` | tested | Repository domain-interface properties must be initialized immediately — no `by lazy`, no custom getter |
| `DataLayer.Repository.mayInjectServicesStorageOrClients` | guidance | May inject Services, client-side `data.storage` Storage objects, or database clients to fulfill their domain properties |
| `DataLayer.ClientDataInterface` | construct | resides in `feature..data..` · is an interface · Must live in `feature.[name].data` (not `data.storage`) |
| `DataLayer.ClientDataImplementation` | construct | resides in `feature..data..` · is a class · Must not be named `Repository` · Must live in `feature.[name].data` (not `data.storage`) |
| `DataLayer.ClientStorage` | construct | resides in `feature..data..` · is a class · name ends with `Storage` · Storage classes must not be abstract · Storage classes must not be `data class` · Storage classes must reside in the `data.storage` package on `:client` |
| `DataLayer.ClientStorage.internalVisibility` | tested | Storage classes must be marked as `internal` (the `expect` declaration may be public, but `actual` implementations should be `internal` where the language allows) |
| `DataLayer.ClientStorage.doesNotInjectDomainRepositoriesOrServices` | tested | Storage classes are forbidden from injecting domain interfaces, Repositories, or Services |
| `DataLayer.providesDomainImplementations` | tested | Provides implementations of `domain` interfaces — by exposing them as properties, not by inheriting them |
| `DataLayer.noInjectingDomainInterfaces` | tested | Forbidden from injecting `domain` interfaces — logic requiring multiple domain interfaces must be moved to a UseCase |
| `DataLayer.storageInternalVisibility` | tested | `data.storage` classes use `internal` visibility where the language allows (see `DataLayer.ClientStorage.internalVisibility` for the canonical statement, incl. the `expect`/`actual` nuance) |
| `DataLayer.noUiDeps` | tested | Must not depend on the `ui` package |
| `DataLayer.exhaustive` | tested | Every top-level declaration in `feature..data..` matches exactly one construct |
| `ServicesLayer.ServiceInterface` | construct | resides in `feature..services..` · A service is an `interface` annotated `@Urpc` · name ends with `Service` · Resides in the top-level `feature.[name].services` package |
| `ServicesLayer.ServiceInterface.noClientOnlyServices` | guidance | Always implement services as urpc service functions in the appropriate server module — do not build client-only local services |
| `ServicesLayer.ServiceInterface.plainFunctionShapes` | tested | Functions are plain `suspend fun f(req): Res`, `fun f(req): Flow<Res>`, or `fun f(reqs: Flow<Req>): Flow<Res>`, each taking 0 or 1 parameter |
| `ServicesLayer.ServiceInterface.nestedRequestResponseTypes` | guidance | Each function's `Request`/`Response` types are nested `@Serializable` types grouped under a per-function `object` namespace |
| `ServicesLayer.ServiceInterface.contractLivesInApi` | tested | Service interfaces live in `feature.[name].services` of the `:api` module |
| `ServicesLayer.ServiceInterface.errorsViaExceptions` | tested | Service functions propagate errors via thrown exceptions; the return type only ever represents a successful result |
| `ServicesLayer.ServiceImpl` | construct | resides in `feature..services..` · For a service named `[Name]Service` the implementation is a class named `[Name]ServiceImpl` · Resides in `feature.[name].services` of the `:server` module (dual-life with the contract) |
| `ServicesLayer.ServiceImpl.internalVisibility` | tested | Service implementations must be `internal` |
| `ServicesLayer.ServiceImpl.noInjectingDomainInterfaces` | tested | Service implementations are forbidden from injecting domain interfaces |
| `ServicesLayer.ServiceImpl.mayInjectStorageAndInternal` | guidance | May inject `services.storage` Storage classes and `services.internal` orchestrators of the same feature, plus other features' Service contracts via `:api` |
| `ServicesLayer.ServiceImpl.noUiDependency` | tested | Service implementations must not depend on the `ui` package |
| `ServicesLayer.InternalCoordinator` | construct | resides in `feature..services..` · A coordinator is a concrete (non-`abstract`, non-`data`) class that is not a `Job` or `Exception` · Resides in `feature.[name].services.internal` |
| `ServicesLayer.InternalDataCarrier` | construct | resides in `feature..services..` · A data carrier is a `data class` payload that flows between subsystems through the orchestrator · Resides in `feature.[name].services.internal` |
| `ServicesLayer.InternalInterface` | construct | resides in `feature..services..` · is an interface · Resides in `feature.[name].services.internal` |
| `ServicesLayer.InternalException` | construct | resides in `feature..services..` · An internal exception is a class named `[Name]Exception`, thrown only by internal helpers · Resides in `feature.[name].services.internal` |
| `ServicesLayer.InternalObjectHelper` | construct | resides in `feature..services..` · is an object · Resides in `feature.[name].services.internal` |
| `ServicesLayer.StorageClass` | construct | resides in `feature..services..` · Named `[Name]Storage` (or `[Name]Store` where the broader name fits) · Not abstract, not a `data class` · Resides in `feature.[name].services.storage` |
| `ServicesLayer.StorageClass.internalVisibility` | tested | Storage classes must be `internal` |
| `ServicesLayer.StorageClass.returnsRowTypesOnly` | tested | Storage classes must take/return `XxxRow` types only — never domain types |
| `ServicesLayer.StorageClass.partialUpdatesByHand` | guidance | When an operation touches only a subset of columns, keep the hand-written `update { … it[col] = value … }` block — `setFromRow` writes every column and is wrong here |
| `ServicesLayer.StorageRecord` | construct | resides in `feature..services..` · Is a `data class` · one of {name ends with `Row`, name ends with `Record`, name ends with `Insert`} · Resides in `feature.[name].services.storage` |
| `ServicesLayer.MappingFunction` | construct | resides in `feature..services..` · is a function · Resides in `feature.[name].services.storage` |
| `ServicesLayer.MappingFunction.mappersInStorage` | guidance | Conversions between a generated `XxxRow` and a domain type live in `services.storage` as plain `internal fun` declarations, conventionally collected in `[Name]Mappers.kt` |
| `ServicesLayer.MappingFunction.multiTableLoadHelpers` | guidance | Where storage operations span multiple tables to assemble a richer record, define those higher-level helpers as `suspend fun [Name]Storage.loadXxx(…)` extensions in `services.storage` |
| `ServicesLayer.CodecObject` | construct | resides in `feature..services..` · is an object · Lives in `services.storage` alongside the Row + mapping functions for the table that uses it |
| `ServicesLayer.CodecObject.keyedToColumn` | guidance | Codecs encapsulate the read/write asymmetry `setFromRow` can't express — keep them small and keyed to the column they serve |
| `ServicesLayer.mustNotDependOnData` | tested | `services` may depend on `domain` and on other features' `:api` `services` contracts; it must not depend on `data` |
| `ServicesLayer.crossFeatureViaApi` | tested | May depend on another feature's `services` only via that feature's `:api` module |
| `ServicesLayer.internalHierarchicalVisibility` | tested | A class in `services.internal.<subsystem>.**` may not import from a different subsystem under `services.internal` (ancestor data-shape imports are allowed) |
| `ServicesLayer.storageMustNotDependOnInternal` | tested | Files in `services.storage` must not import from `services.internal` — the dependency direction inside `services` is `internal → storage` |
| `ServicesLayer.toolsApiContractOnly` | tested | Anything placed in `services.tools` may depend on the Service contract via `:api`-defined types only — never on `services.storage` or `services.internal` |
| `ServicesLayer.generatedTableRowSources` | codegen | `Table`/`Row` sources are generated by the `dev.isaacudy.udytils.postgres` plugin from the Flyway-migrated schema, into the shared package `platform.server.postgres.tables` |
| `ServicesLayer.generatedTableObjects` | codegen | Each persisted entity has a generated `object XxxTable : Table("xxx")` (plural); custom columns use the udytils column types (`JsonbColumnType`, `TextArrayColumnType`, …) |
| `ServicesLayer.everyColumnOnTable` | codegen | Every column on the SQL table is declared on the `Table` object, with no omissions; the UUID primary key is `uuid("id").autoGenerate()` but the write path always supplies the id explicitly |
| `ServicesLayer.rowDataClassPrimitives` | codegen | The in-memory persistence shape is a top-level `data class XxxRow` (singular) whose fields use only primitive types — no domain wrappers, enums, or sealed hierarchies |
| `ServicesLayer.rowFakeConstructorAndSetFromRow` | codegen | Each generated file exposes a fake-constructor `fun XxxRow(row: ResultRow): XxxRow` for reads, and a `fun UpdateBuilder<*>.setFromRow(row: XxxRow)` extension for writes |
| `ServicesLayer.exhaustive` | tested | Every top-level declaration in `feature..services..` matches exactly one construct |
| `FeatureRules.DependencyModule` | construct | DI modules must be defined in the top-level `feature.[name]` package of the `:client` and `:server` modules · is a property · name ends with `Dependencies` |
| `FeatureRules.DependencyModule.ownFeatureBindingsOnly` | tested | The DI module for a feature must only bind/provide dependencies that are both defined and implemented in that feature |
| `FeatureRules.DependencyModule.urpcServiceBinding` | tested | Register a service's generated `[Name]ServiceUrpcBinding` by chaining `.bindService(::[Name]ServiceUrpcBinding)` off the implementation's binding, inside the per-call `scope<UrpcCall> { }` block |
| `FeatureRules.DependencyModuleHelper` | construct | is a function · is `internal` · A DI registration helper has a Koin `Module` receiver |
| `FeatureRules.constructorReferenceBindings` | tested | DI bindings must use the constructor reference style `singleOf(::Constructor).bind(BindingType::class)`, not the lambda style `single<BindingType> { Constructor(get()) }` |
| `ProjectRules.noCatchException` | tested | `try/catch` blocks must never catch `Exception` — use `catch (t: Throwable)` or a specific exception type |
| `ProjectRules.serviceExceptionsSerializable` | tested | Exception types defined in `services` (the cross-the-wire contract) must be annotated with `@Serializable` |
| `ProjectRules.noWildcardImports` | tested | Imports must not use wildcards — always list the explicit symbols |
| `ProjectRules.noDirectAsyncStateConstruction` | tested | `AsyncState.Loading`/`Success`/`Error` must not be constructed directly — use `AsyncState.fromSuspending`/`fromFlow` |
| `ProjectRules.sealedActionVariants` | guidance | Model action/request variants as a `sealed interface`/`sealed class` (each variant a `data class`), not a single type with an `enum` discriminator and nullable fields |
| `ProjectRules.exceptionsNeedHumanSignOff` | guidance | Architecture exceptions may only be added after discussing the exception with a human author |
| `ProjectRules.exceptionNotForFailingTests` | guidance | Adding an architecture exception is not a valid way to resolve an immediate architecture-test failure without user feedback — fix the code or the rule first |
| `ProjectRules.exceptionNeedsKdoc` | guidance | Every architecture exception must include a KDoc-style (`/** ... */`) comment explaining why it exists and the intended resolution |
| `ProjectRules.exceptionsAreTemporary` | guidance | Architecture exceptions are temporary — revisit them periodically and remove them once the underlying issue is resolved |
| `architecture.everyDeclarationBelongsToALayer` | tested | Every feature-module declaration matches exactly one construct across all layers |
