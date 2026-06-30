# UKPT Architecture
This document describes the architecture that should be used for the UKPT project.

These rules are **not maintained by hand**. They are a projection of the machine-readable catalog in [`src/test/kotlin/architecture/rules/`](src/test/kotlin/architecture/rules), which is the single source of truth. Each rule is a `by`-delegated property in that catalog; `RegistryArchitectureTest` both enforces the rules and keeps the [rule index](#rule-index) below in lock-step with them.

## Rule IDs

Every rule has a stable ID that is the **dotted path of the property names that declare it** in the catalog — `<group>[.<construct>].<rule>`:

| ID | Reads as |
| --- | --- |
| `domainLayer.domainInterface.funInterface` | the `funInterface` rule of the `domainInterface` construct in the `domainLayer` group |
| `domainLayer.useCase.noOverridingDefaults` | the `noOverridingDefaults` rule of the `useCase` construct |
| `moduleRules.platformNotFeature` | a group-level rule (no construct) |
| `projectRules.noWildcardImports` | a project-wide rule |

The id is unique by Kotlin scoping, stable under reordering, and self-describing — there are no axis prefixes or numbers to maintain. Test failures, the [rule index](#rule-index), and [architecture exceptions](#6-architecture-exceptions) all reference rules by this path. Search the [catalog sources](src/test/kotlin/architecture/rules) for an id to find its canonical definition.

The groups are:

| Group | Covers | Sections |
| --- | --- | --- |
| `moduleRules` | Gradle module / dependency structure | §1, §2 |
| `domainLayer` | `domain` package | §3.1, §4.1 |
| `uiLayer` | `ui` package | §3.2, §4.2 |
| `dataLayer` | `data` package (client) | §3.3, §4.3 |
| `servicesLayer` | `services` package (contract + server) | §3.4, §4.4 |
| `featureRules` | feature top-level / DI wiring | §3.5, §4.5 |
| `projectRules` | project-wide code rules + exceptions | §5, §6 |

### Enforcement status

Each rule's enforcement tag is **derived from how it is declared in the catalog**, so it can never disagree with reality:

| Tag | Declared as | Meaning |
| --- | --- | --- |
| `✅ tested` | `scope { }` / `constrain { }` / `moduleGraph { }`, or `enforcedBy(...)` | A Konsist check enforces the rule directly and fails citing its id. `enforcedBy(...)` rules are enforced *transitively* by the rules they name. |
| `🔶 construct` | `requirement(...)` | A classification predicate — part of what it means to *be* a construct. Enforced indirectly: a declaration matching no construct (or more than one) fails the layer exhaustiveness / membership check (`<layer>.exhaustive`, `architecture.everyDeclarationBelongsToALayer`) rather than a named-rule failure. |
| `📋 guidance` | `guidance()` | A convention static analysis can't reliably check (the "should…" / permissive "may…" rules). Enforced by review. |
| `⚙️ codegen` | `codegen()` | Guaranteed by the `dev.isaacudy.udytils.postgres` code generator — the shape is generated from the migrated schema, so there is nothing in `src/` for Konsist to scan. |

So `✅ tested` rules have a check citing their id; `🔶 construct` rules are enforced through their construct's membership in the exhaustiveness check; `📋 guidance` and `⚙️ codegen` rules are not machine-checked in `src/`.

### Rule index

Every enforced rule, generated from the catalog by `RegistryArchitectureTest.ruleIndexMatchesReadme`. After changing rules, regenerate with `UPDATE_RULE_INDEX=true ./gradlew :platform:common:architecture:test` — the test fails if this block drifts from the catalog.

<!-- RULE-INDEX:START -->

| Rule | Enforcement | Statement |
| --- | --- | --- |
| `domainLayer.domainInterface.funInterface` | 🔶 construct | Domain interfaces must be a `fun interface` |
| `domainLayer.domainInterface.operatorInvoke` | 🔶 construct | The primary function of a domain interface must be an `operator fun invoke` |
| `domainLayer.domainInterface.suspendingOrFlow` | 🔶 construct | All functions in a domain interface must be `suspend` or return a `Flow<T>` |
| `domainLayer.domainInterface.flowOfPrefix` | 🔶 construct | Flow-returning domain interfaces are prefixed with `FlowOf` |
| `domainLayer.domainInterface.interfaceDefaults` | 📋 guidance | May define additional default functions that call the primary function |
| `domainLayer.domainInterface.primaryParameterTypes` | 📋 guidance | Primary-function parameters must be domain objects, nested types, primitives, or collections of those |
| `domainLayer.domainInterface.primaryReturnType` | 📋 guidance | Primary-function return type must be domain objects, nested types, primitives, collections of those, or no value |
| `domainLayer.domainInterface.implementedByRepositoryOrUseCase` | 📋 guidance | Must be implemented by a Repository (as a property) or by a UseCase |
| `domainLayer.domainInterface.errorsViaExceptions` | ✅ tested | Functions propagate errors via thrown exceptions, never via the return type |
| `domainLayer.domainObject.valueType` | 🔶 construct | A domain object is a sealed/data/enum/value class or interface |
| `domainLayer.domainObject.immutable` | 🔶 construct | Domain objects must be immutable (val properties only) |
| `domainLayer.domainObject.serializable` | 🔶 construct | Domain objects must be annotated with `@Serializable` |
| `domainLayer.domainObject.nestedValueClassIds` | 📋 guidance | Should use nested value classes for identifiers where appropriate |
| `domainLayer.domainObject.sealedHierarchies` | 📋 guidance | Should use sealed interface hierarchies to model polymorphic data where appropriate |
| `domainLayer.domainObject.invariantInitBlocks` | 📋 guidance | Should include `init` blocks that enforce invariants |
| `domainLayer.domainObject.nestedTypes` | 📋 guidance | Should use nested types when conceptually inseparable from the parent |
| `domainLayer.useCase.implNaming` | 🔶 construct | A UseCase is a non-sealed/data/enum/value class named `[DomainInterface]Impl` |
| `domainLayer.useCase.implementsOneInterface` | 🔶 construct | A UseCase must implement exactly one domain interface |
| `domainLayer.useCase.noMutableState` | 🔶 construct | A UseCase must not contain mutable state — all properties are `val` |
| `domainLayer.useCase.noOverridingDefaults` | ✅ tested | Must not override any default function of its domain interface |
| `domainLayer.useCase.mayInjectDomainInterfaces` | 📋 guidance | May inject domain interfaces to perform its logic |
| `domainLayer.useCase.breakDownComplexUseCases` | 📋 guidance | If it becomes too complex, break it into private/file-private/nested parts |
| `domainLayer.domainException.extendsThrowable` | 🔶 construct | A domain exception is a class extending RuntimeException/Exception/PresentableException |
| `domainLayer.domainConstants.objectOfVals` | 🔶 construct | Domain constants are an `object` with only `val` properties and no functions |
| `domainLayer.domainExtensionFunction.compatibleTypes` | 🔶 construct | Receiver/return/parameter types are domain objects, primitives, or collections of those |
| `domainLayer.domainExtensionFunction.noPlatformDeps` | 📋 guidance | Domain extension functions must not introduce platform-specific dependencies |
| `domainLayer.domainExtensionProperty.compatibleTypes` | 🔶 construct | Receiver/type is a domain object, primitive, or collection of those |
| `domainLayer.noPlatformDeps` | ✅ tested | Domain must not contain platform-specific dependencies (Android, Ktor, SQL, …) |
| `domainLayer.noUiDataServicesDeps` | ✅ tested | Domain must not depend on `ui`, `data`, or `services` packages within the feature |
| `domainLayer.crossFeatureViaApi` | ✅ tested | May depend on another feature's `domain` only via that feature's `:api` module |
| `domainLayer.exhaustive` | ✅ tested | Every top-level declaration in `feature..domain..` matches exactly one construct |
| `uiLayer.screen.boundToDestination` | 🔶 construct | Screen functions/properties must be bound to their Destination via the `@NavigationDestination` annotation |
| `uiLayer.screen.screenNaming` | 🔶 construct | Screen functions are named `[Name]Screen`; property-based screens end in `Screen` or `Destination` |
| `uiLayer.screen.singleViewModelParameter` | 🔶 construct | Screen functions must have a single parameter — the associated `[Name]ViewModel` |
| `uiLayer.screen.composableFunction` | 📋 guidance | Screen functions must be annotated with `@Composable` |
| `uiLayer.screen.viewModelStateRelationship` | 📋 guidance | Screen functions have a 1:1 relationship with a ViewModel and ViewModel State |
| `uiLayer.screen.observesState` | 📋 guidance | Screen functions must observe the ViewModel's `state` property and use it to drive the UI |
| `uiLayer.screen.delegatesInteraction` | 📋 guidance | Screen functions should delegate all user interaction handling to the ViewModel |
| `uiLayer.screen.overlayViaDsl` | 📋 guidance | Dialog/overlay screens must use the `navigationDestination` DSL with `metadata = { directOverlay() }` |
| `uiLayer.screen.overlayViewModel` | 📋 guidance | Dialog/overlay screens that need a ViewModel should call `viewModel()` inside the `navigationDestination` block |
| `uiLayer.screen.screenContentCompanion` | ✅ tested | Screen functions must be paired with an `internal [Name]ScreenContent` composable in the same file |
| `uiLayer.screen.viewModelInjection` | ✅ tested | ViewModels must be injected into screens using `viewModel()`, not `koinViewModel()` |
| `uiLayer.composable.notAScreen` | 🔶 construct | Is not a Screen |
| `uiLayer.composable.composableFunction` | 🔶 construct | Is a `@Composable` function |
| `uiLayer.composable.screenContentSnapshotTest` | ✅ tested | Every `[Name]ScreenContent` composable must be exercised by at least one snapshot test |
| `uiLayer.destination.classOrObjectShape` | 🔶 construct | A Destination is a class or object |
| `uiLayer.destination.navigationKeyParent` | 🔶 construct | Destinations must implement `dev.enro.NavigationKey` or `NavigationKey.WithResult<T>` |
| `uiLayer.destination.destinationNaming` | 🔶 construct | Destinations must be named `[Name]Destination` |
| `uiLayer.destination.serializable` | 🔶 construct | Destinations must be serializable and annotated with `@Serializable` |
| `uiLayer.destination.fileNameMatchesDeclaration` | 🔶 construct | Destinations are declared in a file matching their name |
| `uiLayer.destination.minimalData` | 📋 guidance | Destinations should accept the minimal data required to initialise the associated Screen |
| `uiLayer.destination.definedInApiOrClient` | 📋 guidance | Destinations may live in `:api` (shared entry point / server-driven) or `:client` (internal only) |
| `uiLayer.viewModel.extendsViewModel` | 🔶 construct | ViewModels extend `androidx.lifecycle.ViewModel` |
| `uiLayer.viewModel.viewModelNaming` | 🔶 construct | ViewModels must be named `[Name]ViewModel` |
| `uiLayer.viewModel.singlePublicStateProperty` | 🔶 construct | ViewModels expose a single public `state` property, or no public properties at all |
| `uiLayer.viewModel.stateIsViewModelStateType` | 🔶 construct | The `state` property is a `ViewModelState<[Name]State>` (1:1 with the ViewModel's State type) |
| `uiLayer.viewModel.navigationHandleProperty` | 🔶 construct | ViewModels have a `private val navigation` obtained via `navigationHandle<[Name]Destination>()` |
| `uiLayer.viewModel.publicFunctionsReturnUnit` | 🔶 construct | `public`/`internal` functions on a ViewModel must only return `Unit` (or omit a return type) |
| `uiLayer.viewModel.fileNameMatchesDeclaration` | 🔶 construct | ViewModels must be defined in their own file (`[Name]ViewModel.kt`) |
| `uiLayer.viewModel.injectsDomainInterfaces` | 📋 guidance | ViewModels should inject domain interfaces to load and manipulate domain objects |
| `uiLayer.viewModel.usesJobManager` | ✅ tested | ViewModels must use `JobManager` to manage coroutines — never hold `var job: Job?` references |
| `uiLayer.viewModelState.dataClass` | 🔶 construct | ViewModel State objects must be a `data class` |
| `uiLayer.viewModelState.stateNaming` | 🔶 construct | ViewModel State objects are named `[Name]State` |
| `uiLayer.viewModelState.immutable` | 🔶 construct | ViewModel State objects must be immutable (val properties only) |
| `uiLayer.viewModelState.fileNameMatchesDeclaration` | 🔶 construct | ViewModel State objects must be defined in their own file (`[Name]State.kt`) |
| `uiLayer.viewModelState.viewModelRelationship` | 📋 guidance | ViewModel State objects have a 1:1 relationship with a ViewModel type |
| `uiLayer.viewModelState.usesAsyncState` | 📋 guidance | ViewModel State objects must use `AsyncState<T>` / `UpdatableState<T>` for asynchronously loaded data and action progress |
| `uiLayer.viewModelState.noCustomAsyncSealedTypes` | 📋 guidance | ViewModel State objects must not define custom sealed types for loading/success/error — use `AsyncState<T>` |
| `uiLayer.viewModelState.transparentContainer` | 📋 guidance | ViewModel State objects should be a transparent container for domain objects, not lossy UI-level mappings |
| `uiLayer.viewModelState.invariantInitBlocks` | 📋 guidance | ViewModel State objects should include `init` blocks that enforce invariants |
| `uiLayer.viewModelState.formattingInScreen` | 📋 guidance | Formatting and visual representation must be handled by the Screen or specialized `@Composable` properties/functions |
| `uiLayer.uiValueType.enumOrSealed` | 🔶 construct | Is an enum, sealed class, or sealed interface |
| `uiLayer.uiValueType.noMemberFunctions` | 🔶 construct | Has no member functions |
| `uiLayer.mayDependOnDomain` | 📋 guidance | May depend on `domain` |
| `uiLayer.noImplementingDomainInterfaces` | ✅ tested | Forbidden from implementing `domain` interfaces |
| `uiLayer.noDataServicesDeps` | ✅ tested | Forbidden from depending on `data` or `services` |
| `uiLayer.noKoinInject` | ✅ tested | Must not use `koinInject` — all dependencies are injected through ViewModels |
| `uiLayer.exhaustive` | ✅ tested | Every top-level declaration in `feature..ui..` matches exactly one construct |
| `dataLayer.repository.isClass` | 🔶 construct | A Repository is a class |
| `dataLayer.repository.nameEndsWithRepository` | 🔶 construct | Repositories must be named `[Name]Repository` |
| `dataLayer.repository.internalVisibility` | 🔶 construct | Repositories must be marked as `internal` |
| `dataLayer.repository.fileNameMatchesDeclaration` | 🔶 construct | A Repository is declared in a file whose name matches the class |
| `dataLayer.repository.doesNotImplementDomainInterfaces` | 🔶 construct | Repositories must not implement domain interfaces directly |
| `dataLayer.repository.exposesDomainInterfacesAsProperties` | 🔶 construct | Repositories must expose domain interfaces as `public val` properties |
| `dataLayer.repository.doesNotInjectDomainInterfaces` | 🔶 construct | Repositories are forbidden from injecting domain interfaces |
| `dataLayer.repository.doesNotInjectRepositories` | 🔶 construct | Repositories are forbidden from injecting other Repositories |
| `dataLayer.repository.propertiesEagerlyInitialized` | ✅ tested | Repository domain-interface properties must be initialized immediately — no `by lazy`, no custom getter |
| `dataLayer.repository.mayInjectServicesStorageOrClients` | 📋 guidance | May inject Services, client-side `data.storage` Storage objects, or database clients to fulfill their domain properties |
| `dataLayer.clientDataInterface.isInterface` | 🔶 construct | A non-Repository client data interface is an `interface` |
| `dataLayer.clientDataInterface.residesInData` | 🔶 construct | Must live in `feature.[name].data` (not `data.storage`) |
| `dataLayer.clientDataImplementation.isClass` | 🔶 construct | A non-Repository client data class is a class |
| `dataLayer.clientDataImplementation.notNamedRepository` | 🔶 construct | Must not be named `Repository` |
| `dataLayer.clientDataImplementation.residesInData` | 🔶 construct | Must live in `feature.[name].data` (not `data.storage`) |
| `dataLayer.clientStorage.isClass` | 🔶 construct | A client Storage class is a class |
| `dataLayer.clientStorage.nameEndsWithStorage` | 🔶 construct | Storage classes must be named `[Name]Storage` |
| `dataLayer.clientStorage.notAbstract` | 🔶 construct | Storage classes must not be abstract |
| `dataLayer.clientStorage.notDataClass` | 🔶 construct | Storage classes must not be `data class` |
| `dataLayer.clientStorage.residesInDataStorage` | 🔶 construct | Storage classes must reside in the `data.storage` package on `:client` |
| `dataLayer.clientStorage.internalVisibility` | 📋 guidance | Storage classes must be marked as `internal` (the `expect` declaration may be public, but `actual` implementations should be `internal` where the language allows) |
| `dataLayer.clientStorage.doesNotInjectDomainRepositoriesOrServices` | ✅ tested | Storage classes are forbidden from injecting domain interfaces, Repositories, or Services |
| `dataLayer.providesDomainImplementations` | ✅ tested | Provides implementations of `domain` interfaces — by exposing them as properties, not by inheriting them |
| `dataLayer.noInjectingDomainInterfaces` | ✅ tested | Forbidden from injecting `domain` interfaces — logic requiring multiple domain interfaces must be moved to a UseCase |
| `dataLayer.storageInternalVisibility` | 📋 guidance | `data.storage` classes use `internal` visibility where the language allows (see `dataLayer.clientStorage.internalVisibility` for the canonical statement, incl. the `expect`/`actual` nuance) |
| `dataLayer.noUiDeps` | ✅ tested | Must not depend on the `ui` package |
| `dataLayer.exhaustive` | ✅ tested | Every top-level declaration in `feature..data..` matches exactly one construct |
| `servicesLayer.serviceInterface.urpcInterface` | 🔶 construct | A service is an `interface` annotated `@Urpc` |
| `servicesLayer.serviceInterface.nameEndsWithService` | 🔶 construct | Is named `[Name]Service` |
| `servicesLayer.serviceInterface.inServicesContractPackage` | 🔶 construct | Resides in the top-level `feature.[name].services` package |
| `servicesLayer.serviceInterface.noClientOnlyServices` | 📋 guidance | Always implement services as urpc service functions in the appropriate server module — do not build client-only local services |
| `servicesLayer.serviceInterface.plainFunctionShapes` | 📋 guidance | Functions are plain `suspend fun f(req): Res`, `fun f(req): Flow<Res>`, or `fun f(reqs: Flow<Req>): Flow<Res>`, each taking 0 or 1 parameter |
| `servicesLayer.serviceInterface.nestedRequestResponseTypes` | 📋 guidance | Each function's `Request`/`Response` types are nested `@Serializable` types grouped under a per-function `object` namespace |
| `servicesLayer.serviceInterface.contractLivesInApi` | 📋 guidance | Service interfaces live in `feature.[name].services` of the `:api` module |
| `servicesLayer.serviceInterface.errorsViaExceptions` | ✅ tested | Service functions propagate errors via thrown exceptions; the return type only ever represents a successful result |
| `servicesLayer.serviceImpl.serviceImplClass` | 🔶 construct | For a service named `[Name]Service` the implementation is a class named `[Name]ServiceImpl` |
| `servicesLayer.serviceImpl.internalVisibility` | 🔶 construct | Service implementations must be `internal` |
| `servicesLayer.serviceImpl.inServerServicesPackage` | 🔶 construct | Resides in `feature.[name].services` of the `:server` module (dual-life with the contract) |
| `servicesLayer.serviceImpl.noInjectingDomainInterfaces` | 📋 guidance | Service implementations are forbidden from injecting domain interfaces |
| `servicesLayer.serviceImpl.mayInjectStorageAndInternal` | 📋 guidance | May inject `services.storage` Storage classes and `services.internal` orchestrators of the same feature, plus other features' Service contracts via `:api` |
| `servicesLayer.serviceImpl.noUiDependency` | ✅ tested | Service implementations must not depend on the `ui` package |
| `servicesLayer.internalCoordinator.concreteCoordinatorClass` | 🔶 construct | A coordinator is a concrete (non-`abstract`, non-`data`) class that is not a `Job` or `Exception` |
| `servicesLayer.internalCoordinator.inInternalPackage` | 🔶 construct | Resides in `feature.[name].services.internal` |
| `servicesLayer.internalDataCarrier.dataCarrierClass` | 🔶 construct | A data carrier is a `data class` payload that flows between subsystems through the orchestrator |
| `servicesLayer.internalDataCarrier.inInternalPackage` | 🔶 construct | Resides in `feature.[name].services.internal` |
| `servicesLayer.internalInterface.internalInterfaceShape` | 🔶 construct | An internal interface is an abstraction used inside a subsystem |
| `servicesLayer.internalInterface.inInternalPackage` | 🔶 construct | Resides in `feature.[name].services.internal` |
| `servicesLayer.internalException.internalExceptionClass` | 🔶 construct | An internal exception is a class named `[Name]Exception`, thrown only by internal helpers |
| `servicesLayer.internalException.inInternalPackage` | 🔶 construct | Resides in `feature.[name].services.internal` |
| `servicesLayer.internalObjectHelper.objectHelperShape` | 🔶 construct | An object helper is an `object` holding pure helpers |
| `servicesLayer.internalObjectHelper.inInternalPackage` | 🔶 construct | Resides in `feature.[name].services.internal` |
| `servicesLayer.storageClass.storageNameSuffix` | 🔶 construct | Named `[Name]Storage` (or `[Name]Store` where the broader name fits) |
| `servicesLayer.storageClass.notAbstractNotData` | 🔶 construct | Not abstract, not a `data class` |
| `servicesLayer.storageClass.internalVisibility` | 🔶 construct | `internal` visibility |
| `servicesLayer.storageClass.inStoragePackage` | 🔶 construct | Resides in `feature.[name].services.storage` |
| `servicesLayer.storageClass.returnsRowTypesOnly` | ✅ tested | Storage classes must take/return `XxxRow` types only — never domain types |
| `servicesLayer.storageClass.partialUpdatesByHand` | 📋 guidance | When an operation touches only a subset of columns, keep the hand-written `update { … it[col] = value … }` block — `setFromRow` writes every column and is wrong here |
| `servicesLayer.storageRecord.rowDataClass` | 🔶 construct | Is a `data class` |
| `servicesLayer.storageRecord.rowNameSuffix` | 🔶 construct | Named `[Name]Row`, `[Name]Record`, or `[Name]Insert` |
| `servicesLayer.storageRecord.inStoragePackage` | 🔶 construct | Resides in `feature.[name].services.storage` |
| `servicesLayer.mappingFunction.topLevelFunction` | 🔶 construct | A mapping function is a top-level function converting between a `XxxRow` and a domain type |
| `servicesLayer.mappingFunction.inStoragePackage` | 🔶 construct | Resides in `feature.[name].services.storage` |
| `servicesLayer.mappingFunction.mappersInStorage` | 📋 guidance | Conversions between a generated `XxxRow` and a domain type live in `services.storage` as plain `internal fun` declarations, conventionally collected in `[Name]Mappers.kt` |
| `servicesLayer.mappingFunction.multiTableLoadHelpers` | 📋 guidance | Where storage operations span multiple tables to assemble a richer record, define those higher-level helpers as `suspend fun [Name]Storage.loadXxx(…)` extensions in `services.storage` |
| `servicesLayer.codecObject.codecObjectShape` | 🔶 construct | A codec is an `object` (discriminator constants, or file-private `Json` + encode/decode helpers) |
| `servicesLayer.codecObject.inStoragePackage` | 🔶 construct | Lives in `services.storage` alongside the Row + mapping functions for the table that uses it |
| `servicesLayer.codecObject.keyedToColumn` | 📋 guidance | Codecs encapsulate the read/write asymmetry `setFromRow` can't express — keep them small and keyed to the column they serve |
| `servicesLayer.mustNotDependOnData` | ✅ tested | `services` may depend on `domain` and on other features' `:api` `services` contracts; it must not depend on `data` |
| `servicesLayer.crossFeatureViaApi` | ✅ tested | May depend on another feature's `services` only via that feature's `:api` module |
| `servicesLayer.internalHierarchicalVisibility` | ✅ tested | A class in `services.internal.<subsystem>.**` may not import from a different subsystem under `services.internal` (ancestor data-shape imports are allowed) |
| `servicesLayer.storageMustNotDependOnInternal` | ✅ tested | Files in `services.storage` must not import from `services.internal` — the dependency direction inside `services` is `internal → storage` |
| `servicesLayer.toolsApiContractOnly` | ✅ tested | Anything placed in `services.tools` may depend on the Service contract via `:api`-defined types only — never on `services.storage` or `services.internal` |
| `servicesLayer.generatedTableRowSources` | ⚙️ codegen | `Table`/`Row` sources are generated by the `dev.isaacudy.udytils.postgres` plugin from the Flyway-migrated schema, into the shared package `platform.server.postgres.tables` |
| `servicesLayer.generatedTableObjects` | ⚙️ codegen | Each persisted entity has a generated `object XxxTable : Table("xxx")` (plural); custom columns use the udytils column types (`JsonbColumnType`, `TextArrayColumnType`, …) |
| `servicesLayer.everyColumnOnTable` | ⚙️ codegen | Every column on the SQL table is declared on the `Table` object, with no omissions; the UUID primary key is `uuid("id").autoGenerate()` but the write path always supplies the id explicitly |
| `servicesLayer.rowDataClassPrimitives` | ⚙️ codegen | The in-memory persistence shape is a top-level `data class XxxRow` (singular) whose fields use only primitive types — no domain wrappers, enums, or sealed hierarchies |
| `servicesLayer.rowFakeConstructorAndSetFromRow` | ⚙️ codegen | Each generated file exposes a fake-constructor `fun XxxRow(row: ResultRow): XxxRow` for reads, and a `fun UpdateBuilder<*>.setFromRow(row: XxxRow)` extension for writes |
| `servicesLayer.exhaustive` | ✅ tested | Every top-level declaration in `feature..services..` matches exactly one construct |
| `featureRules.dependencyModule.inTopLevelFeaturePackage` | 🔶 construct | DI modules must be defined in the top-level `feature.[name]` package of the `:client` and `:server` modules |
| `featureRules.dependencyModule.koinValModule` | 🔶 construct | DI modules are Koin `val` modules whose names end in `Dependencies` |
| `featureRules.dependencyModule.ownFeatureBindingsOnly` | ✅ tested | The DI module for a feature must only bind/provide dependencies that are both defined and implemented in that feature |
| `featureRules.dependencyModule.urpcServiceBinding` | 📋 guidance | Register a service's generated `[Name]ServiceUrpcBinding` by chaining `.bindService(::[Name]ServiceUrpcBinding)` off the implementation's binding, inside the per-call `scope<UrpcCall> { }` block |
| `featureRules.dependencyModuleHelper.declaredAsFunction` | 🔶 construct | A DI registration helper is a function |
| `featureRules.dependencyModuleHelper.internalVisibility` | 🔶 construct | A DI registration helper is `internal` visibility |
| `featureRules.dependencyModuleHelper.moduleReceiver` | 🔶 construct | A DI registration helper has a Koin `Module` receiver |
| `featureRules.constructorReferenceBindings` | ✅ tested | DI bindings must use the constructor reference style `singleOf(::Constructor).bind(BindingType::class)`, not the lambda style `single<BindingType> { Constructor(get()) }` |
| `projectRules.noCatchException` | ✅ tested | `try/catch` blocks must never catch `Exception` — use `catch (t: Throwable)` or a specific exception type |
| `projectRules.serviceExceptionsSerializable` | ✅ tested | Exception types defined in `services` (the cross-the-wire contract) must be annotated with `@Serializable` |
| `projectRules.noWildcardImports` | ✅ tested | Imports must not use wildcards — always list the explicit symbols |
| `projectRules.noDirectAsyncStateConstruction` | ✅ tested | `AsyncState.Loading`/`Success`/`Error` must not be constructed directly — use `AsyncState.fromSuspending`/`fromFlow` |
| `projectRules.sealedActionVariants` | 📋 guidance | Model action/request variants as a `sealed interface`/`sealed class` (each variant a `data class`), not a single type with an `enum` discriminator and nullable fields |
| `projectRules.exceptionsNeedHumanSignOff` | 📋 guidance | Architecture exceptions may only be added after discussing the exception with a human author |
| `projectRules.exceptionNotForFailingTests` | 📋 guidance | Adding an architecture exception is not a valid way to resolve an immediate architecture-test failure without user feedback — fix the code or the rule first |
| `projectRules.exceptionNeedsKdoc` | 📋 guidance | Every architecture exception must include a KDoc-style (`/** ... */`) comment explaining why it exists and the intended resolution |
| `projectRules.exceptionsAreTemporary` | 📋 guidance | Architecture exceptions are temporary — revisit them periodically and remove them once the underlying issue is resolved |
| `moduleRules.featureNotApp` | ✅ tested | `:feature` modules must never depend on `:app` modules |
| `moduleRules.featureMayUsePlatform` | 📋 guidance | `:feature` modules may depend on `:platform` modules |
| `moduleRules.clientApiOnly` | ✅ tested | `:feature:[name]:client` must never depend on another `:client`/`:server` module |
| `moduleRules.clientMayUseApi` | ✅ tested | `:feature:[name]:client` may depend on any `:feature:[name]:api` module |
| `moduleRules.serverApiOnly` | ✅ tested | `:feature:[name]:server` must never depend on another `:client`/`:server` module |
| `moduleRules.serverMayUseApi` | ✅ tested | `:feature:[name]:server` may depend on any `:feature:[name]:api` module |
| `moduleRules.apiMayUseApi` | 📋 guidance | `:feature:[name]:api` may depend on another feature's `:api` module to share models |
| `moduleRules.featuresMayBeGrouped` | 📋 guidance | `:feature` modules may be grouped (`:feature:[group]:[name]:…`) |
| `moduleRules.platformNotApp` | ✅ tested | `:platform` modules must never depend on `:app` modules |
| `moduleRules.platformNotFeature` | ✅ tested | `:platform` modules must never depend on `:feature` modules |
| `moduleRules.platformMayUsePlatform` | 📋 guidance | `:platform` modules may depend on other `:platform` modules |
| `architecture.everyDeclarationBelongsToALayer` | ✅ tested | Every feature-module declaration matches exactly one construct across all layers |

<!-- RULE-INDEX:END -->

## CI enforcement

Run the full architecture suite with `--rerun-tasks` so Konsist's project-scope cache is bypassed — that's load-bearing, because Konsist scans the source tree and a stale cache can mask new violations:

```
./gradlew :platform:common:architecture:test --rerun-tasks
```

This runs both `architecture()` (every catalog rule) and `ruleIndexMatchesReadme()` (the index above stays in sync). ukpt does not yet wire this into CI. When you want it enforced automatically, add a workflow (e.g. `.github/workflows/pr-verification.yml`) that runs the command above on pull requests.

## **1. Gradle Project Structure**

The project is organized into three root-level module groups.

### **1.1 `:app` (Application shells)**

* **Purpose**: Final executable entry points and dependency injection (DI) wiring.
* **Structure**: May contain sub-groups (e.g., `:app:admin`, `:app:customer`) if multiple applications are built from the same codebase.
* **Child Modules**: Each app contains a `:client` (Mobile/Desktop/Web) and/or a `:server` (Ktor executable).
    * **Client structure (AGP 9.0)**: Under AGP 9.0 a single Kotlin Multiplatform module can no longer also be a `com.android.application`, so the client is itself a group: a shared KMP library `:app:client:shared` (the `com.android.kotlin.multiplatform.library` plugin) holding the shared UI, navigation, DI wiring, and the iOS framework entry point (`iosMain`), plus thin per-platform application modules `:app:client:android` (`com.android.application`), `:app:client:desktop` (Compose Desktop), and `:app:client:web` (wasmJs). The per-platform modules contain only their entry point + platform packaging and depend on `:app:client:shared`.
* **Constraints**: Must not contain business logic. Limited to infrastructure configuration and DI module aggregation.

### **1.2 `:feature` (Vertical slices of functionality)**

* **Purpose**: Encapsulated feature-specific functionality.
* **Sub-Modules**:
    * **`:api`**: Mandatory. Contains the shared contract.
    * **`:client`**: Optional. Contains UI and client-side logic.
    * **`:server`**: Optional. Contains server-side implementation.
* **Notes**:
    * Small projects may start with a single `:feature:core` containing all feature/domain code. As complexity increases, logic is migrated into specific `:feature:name` modules.
    * When starting with a single `:feature:core` feature module, it is a good idea to "preempt" the migration of `:feature:core` into individual `:feature:[name]` modules by using `feature.[name]` for package names within `:feature:core` (instead of `feature.core`)
      * If you are following this pattern, the named feature packages within `:feature:core` should only depend on other named packages via the api module
      * Example: If `:feature:core` contains `feature.auth` and `feature.invoices`, code in `feature.auth` should only depend on `feature.invoices` code which is in the `:feature:core:api` module
    * `:client` and `:server` modules are optional, but at least one of the two should exist for every feature.

### **1.3 `:platform` (Infrastructure)**

* **Purpose**: Reusable, non-feature-specific capabilities.
* **Sub-Groups**:
    * **`:common`**: Code shared by both client and server (e.g., utilities).
    * **`:client`**: Client-only infrastructure (e.g., Design System, local DB drivers).
    * **`:server`**: Server-only infrastructure (e.g., Ktor plugins, and `:platform:server:postgres` — which owns the Flyway SQL migrations + `schema.sql` and applies the `dev.isaacudy.udytils.postgres` codegen plugin; the DB runtime itself lives in that udytils library).

---

## **2. Gradle Project Dependency Rules**

These rules are checked against the module dependency graph parsed from the `build.gradle.kts` files (`moduleGraph { }`). Build-file exemptions use the `// architecture-exception:` comment (see [§6](#6-architecture-exceptions)).

### **2.1 Feature Constraints**

* **`moduleRules.featureNotApp`** `✅ tested`: `:feature` modules must never depend on `:app` modules.
* **`moduleRules.featureMayUsePlatform`** `📋 guidance`: `:feature` modules may depend on `:platform` modules.
* **`moduleRules.clientApiOnly`** `✅ tested`: `:feature:[name]:client` must never depend on another `:client`/`:server` module — a feature's client may only reach other features through their `:api` contract, or `:platform`.
* **`moduleRules.clientMayUseApi`** `✅ tested`: `:feature:[name]:client` may depend on any `:feature:[name]:api` module (enforced via `moduleRules.clientApiOnly`).
* **`moduleRules.serverApiOnly`** `✅ tested`: `:feature:[name]:server` must never depend on another `:client`/`:server` module — a feature's server may only reach other features through their `:api` contract, or `:platform`.
* **`moduleRules.serverMayUseApi`** `✅ tested`: `:feature:[name]:server` may depend on any `:feature:[name]:api` module (enforced via `moduleRules.serverApiOnly`).
* **`moduleRules.apiMayUseApi`** `📋 guidance`: `:feature:[name]:api` may depend on another feature's `:api` module to share models or interfaces.
    * **Note**: `:api` to `:api` dependencies are allowed, but should be used sparingly, treated with caution, and minimised where possible.
* **`moduleRules.featuresMayBeGrouped`** `📋 guidance`: `:feature` modules may be grouped (e.g. `:feature:[group]:[name]:api/client/server`).
    * **Note**: A module that serves as a group should exist only as a group, and should not itself contain `:api`, `:server` or `:client` modules.

### **2.2 Platform Constraints**
* **`moduleRules.platformNotApp`** `✅ tested`: `:platform` modules must never depend on `:app` modules.
* **`moduleRules.platformNotFeature`** `✅ tested`: `:platform` modules must never depend on `:feature` modules.
* **`moduleRules.platformMayUsePlatform`** `📋 guidance`: `:platform` modules may depend on other `:platform` modules.
    * **Note**: `:platform` to `:platform` dependencies are allowed, but should be used sparingly, treated with caution, and minimised where possible.

---

## **3. Feature Architecture (Package level)**

Every feature module follows a strict package hierarchy: `feature.[name].[package]`.

The top-level package `feature.[name]` is also used for dependency injection wiring.

All sub-packages may include subpackages for grouping. For example, a `..ui` package in a feature that includes both list and detail functionality may have `feature.[name].ui.list` and `feature.[name].ui.detail`. This same pattern applies for the domain and data packages.

### **3.1 `domain` package (in `:api`, `:client`, and `:server`)**

* **Contents**: Pure Kotlin Data Models and single-function interfaces (Interactors).
* **`domainLayer.noPlatformDeps`** `✅ tested`: Domain must not contain platform-specific dependencies (Android, Ktor, SQL, …).
    * **Why**: The domain layer stays pure Kotlin so it ports across `:client`/`:server` and every KMP target and stays unit-testable. Expose a domain interface and implement it in `data`/`services`.
* **`domainLayer.noUiDataServicesDeps`** `✅ tested`: Domain must not depend on `ui`, `data`, or `services` packages within the feature.
    * **Why**: The dependency graph is `ui → domain ← data`, with `services` depending on domain. Importing those into domain would invert the graph or create a cycle.
* **`domainLayer.crossFeatureViaApi`** `✅ tested`: May depend on another feature's `domain` only via that feature's `:api` module (enforced via the cross-feature module rules — see [§2.1](#21-feature-constraints)).
    * **Note**: Cross-feature domain dependencies should be minimised where possible, but are permitted because real-world domains have genuine dependencies between them. The important thing is getting the direction of dependencies correct and avoiding circular dependencies.

### **3.2 `ui` package (in `:api` and `:client`)**

* **`:api` Contents**: Serializable Navigation Keys.
* **`:client` Contents**: Compose UI, ViewModels, and UI-state models.
* **`uiLayer.mayDependOnDomain`** `📋 guidance`: May depend on `domain`.
* **`uiLayer.noImplementingDomainInterfaces`** `✅ tested`: Forbidden from implementing `domain` interfaces.
    * **Why**: Domain interfaces are the contract between presentation and persistence — implementations belong in `data` (Repositories) or `domain` (UseCases). A ViewModel that implements one would couple two layers' lifecycles and make the ViewModel un-injectable elsewhere.
* **`uiLayer.noDataServicesDeps`** `✅ tested`: Forbidden from depending on `data` or `services`. Calling the server goes through Repositories (in `data`), which expose [Domain Interfaces](#411-domain-interfaces) for the UI to consume.
* **`uiLayer.noKoinInject`** `✅ tested`: Must not use `koinInject` — all dependencies are injected through ViewModels.
    * **Why**: Resolving from Koin inside a Composable side-steps the ViewModel as the single dependency surface, makes the screen untestable in snapshots (no Koin runtime), and re-resolves on every recomposition.

### **3.3 `data` package (in `:client` only)**

* **Contents**: Repository implementations and client-side local persistence (Keychain, SharedPrefs, etc.).
* **`dataLayer.providesDomainImplementations`** `✅ tested`: Provides implementations of `domain` interfaces — by *exposing* them as properties, **not** by inheriting them (enforced via `dataLayer.repository.doesNotImplementDomainInterfaces` + `dataLayer.repository.exposesDomainInterfacesAsProperties`).
* **`dataLayer.noInjectingDomainInterfaces`** `✅ tested`: Forbidden from *injecting* `domain` interfaces. Logic requiring multiple domain interfaces must be moved to a UseCase in the `domain` package.
* **`dataLayer.noUiDeps`** `✅ tested`: Must not depend on the `ui` package.
    * **Why**: UI is the outermost layer; `data` sits beneath it and supplies the domain interfaces the UI consumes. If `data` imports a UI type the layering becomes circular and the Repository can no longer be tested without a Compose runtime.
* **Note**: The `data` axis is **client-only**. Server-side persistence is in `services.storage` (see [§3.4](#34-services-package-in-api-and-server)); the server has no `data.*` package.

#### **3.3.1 `data.storage` package (in `:client` only)**

* **Contents**: Client-side local persistence types — `expect`/`actual` `Storage` classes backed by Keychain (iOS), SharedPreferences (Android), DataStore, etc.
* **`dataLayer.storageInternalVisibility`** `📋 guidance`: `data.storage` classes use `internal` visibility where the language allows — see `dataLayer.clientStorage.internalVisibility` for the canonical statement (incl. the `expect`/`actual` nuance).

### **3.4 `services` package (in `:api` and `:server`)**

The `services` axis defines the contract that crosses the wire between client and server. The contract lives in `:api` (so both sides see it); the server-side implementation lives in `:server` under the same package name (dual-life).

`services` is **not** a UI-equivalent outer layer — it sits *parallel* to the `data` axis and is consumed by it. On the client, Repositories (in `data`) inject Service contracts to call the server. On the server, `services` is where the request-handling implementation lives, and reaches down into `services.storage` for persistence and `services.internal.*` for sub-tasks.

The cross-the-wire mechanism is **urpc** (`dev.isaacudy.udytils:urpc-*`): a service is an `@Urpc` interface, and KSP generates the client, the server binding, and the wire descriptors. See [§4.4.1](#441-services-the-cross-the-wire-contract).

* **`:api` Contents**: Service interface contracts. A service interface is an `interface` annotated `@Urpc` whose functions are plain `suspend fun`/`Flow`-returning methods; each function's `Request`/`Response` types are declared with the service (nested `@Serializable` types under a per-function `object` namespace).
* **`:server` Contents**: `[Name]ServiceImpl` classes implementing the contract.
* **`servicesLayer.mustNotDependOnData`** `✅ tested`: `services` may depend on `domain` and on other features' `:api`-defined `services` contracts; it must not depend on `data` (the server has no `data`; the client's `data` depends on `services`, not the other way around).
* **`servicesLayer.crossFeatureViaApi`** `✅ tested`: May depend on another feature's `services` only via that feature's `:api` module (enforced via the cross-feature module rules).

#### **3.4.1 `services.internal` package (in `:server`)**

Server-side coordinator and helper classes — the things that do the work the ServiceImpl orchestrates.

* **Bare `services.internal`**: top-level orchestrators (e.g. `SessionProcessingManager`) that compose multiple subsystems.
* **`services.internal.<subsystem>`**: subsystem packages. Each direct child of `services.internal` is a sealed island under hierarchical visibility (see [§3.4.5](#345-hierarchical-visibility-within-servicesinternal)).

#### **3.4.2 `services.storage` package (in `:server`)**

Server-side Postgres persistence built on **Exposed** and the **`dev.isaacudy.udytils.postgres`** runtime: `[Name]Storage` classes, `Row ↔ Domain` mapping functions, and codec objects. The generated Exposed `Table` objects and `XxxRow` data classes do **not** live here — they are generated into the shared `platform.server.postgres.tables` package and imported by each feature's storage code. Full details in [§4.4.4](#444-servicesstorage-package--postgres-persistence).

#### **3.4.3 `services.tools` package (in `:server`, reserved)**

Reserved for AI tool-use subclasses. ukpt has no AI subsystem, so this package is intentionally empty (see [§4.4.5](#445-servicestools-package-reserved)).

* **`servicesLayer.toolsApiContractOnly`** `✅ tested`: Anything placed in `services.tools` may depend on the Service contract via `:api`-defined types only — never on `services.storage` or `services.internal`. The isolation rule is enforced now even though the package is empty.

#### **3.4.4 Cross-axis dependency rules**

Within a feature:

* `domain` may not depend on any other axis. It is the deepest layer.
* `services` may depend on `domain`.
* `data` (client only) may depend on `domain` and on `services` contracts (so Repositories can call the server).
* `ui` (client only) may depend on `domain` only. It must not depend on `data` or `services` directly — calling the server goes through Repositories, which expose [Domain Interfaces](#411-domain-interfaces) for the UI to consume.
* No axis may depend on `ui`.
* Inside `services`, the dependency direction is `internal → storage`: **`servicesLayer.storageMustNotDependOnInternal`** `✅ tested` forbids `services.storage` files from importing `services.internal`.

Reading these as a directed graph:

* On the client: `ui → domain ← services ← data` (and `data → domain`).
* On the server: `domain ← services` (with `services` reaching internally into `services.storage` and `services.internal`).

`domain` is the centre of gravity on both sides. `services` is a sibling of `data` (not an outer shell above it) — the wire-crossing contract that `data` consumes on the client and `services` itself implements on the server.

#### **3.4.5 Hierarchical visibility within `services.internal`**

Enforced by **`servicesLayer.internalHierarchicalVisibility`** `✅ tested`. Inside `feature.[name].services.internal.**`, an import is allowed only if it points to:

* the **same package**, or
* a **descendant** package, or
* an **ancestor** package, **and only when the imported declaration is a pure data shape**.

Lateral / cousin imports are forbidden outright. Ancestor imports of behaviour-bearing types (regular classes, regular interfaces, top-level functions, objects with member functions) are forbidden too — those would let a subsystem reach back up to *invoke* its parent or use behaviour from a higher level, which re-introduces the cross-subsystem coupling the rule is designed to prevent.

The carve-out for data shapes lets the orchestrator-mediated composition pattern work: a payload type that flows from one subsystem through the orchestrator into another can live at a common ancestor (typically bare `services.internal`), and both subsystems may name it without invoking any behaviour.

A "data shape" is any of:
* `data class`, `enum class`, `value class`, `data object`,
* `sealed class` / `sealed interface`,
* an `object` that holds only `val` constants (no functions).

Each direct child of `services.internal` is a sealed island. The bare `services.internal` package is where the orchestrator (and the shared-payload data types) live. A subsystem may subdivide into deeper subpackages — the rule applies recursively, so each new subpackage inherits the same sealing rules.

Slogan: *"You can see your children freely, your parents only for shared data, and never your siblings."*

### **3.5 top-level package (in `:client` and `:server`)**
* **Contents**: Dependency injection modules which define dependency injection bindings. The top-level feature package is reserved for DI wiring — concrete classes (ServiceImpls, helpers) live in their layer-specific package.

---

## **4. Feature Architecture (Code level)**

Within the packages of a feature module, every class, function or other code-level construct is defined as a component in the architecture, based on its responsibilities and package location.

Each construct owns its **requirements** (the `🔶 construct` classification predicates — what makes a declaration *be* that construct) and its **rules** (what a declaration of that construct must *do*). Every top-level declaration in a layer package must match exactly one construct (the `<layer>.exhaustive` rule); declarations that fit no layer package are covered by the global `architecture.everyDeclarationBelongsToALayer` membership rule.

### **4.1 `domain` package constructs**

The `domain` package must only contain [domain interfaces](#411-domain-interfaces), [domain objects](#412-domain-objects), [UseCases](#413-usecases), [domain extension functions](#414-domain-extension-functions), [domain extension properties](#415-domain-extension-properties), [domain exceptions](#416-domain-exceptions), and [domain constants](#417-domain-constants).

#### **4.1.1 domain interfaces**
* **Definition**: A functional interface representing domain-level functionality/business logic.
* **Requirements** (`🔶 construct`):
    * **`domainLayer.domainInterface.funInterface`** — Domain interfaces must be a `fun interface`.
    * **`domainLayer.domainInterface.operatorInvoke`** — The primary function of a domain interface must be an `operator fun invoke`.
    * **`domainLayer.domainInterface.suspendingOrFlow`** — All functions in a domain interface must be `suspend` or return a `Flow<T>`.
    * **`domainLayer.domainInterface.flowOfPrefix`** — Flow-returning domain interfaces are prefixed with `FlowOf` (the `StateFlow<T>` subtype of `Flow<T>` is also allowed).
* **Rules**:
    * **`domainLayer.domainInterface.interfaceDefaults`** `📋 guidance` — May define additional default functions that call the primary function.
        * **Note**: Default functions don't need to be `operator fun invoke` and should use expressive names; they should provide commonly used functionality (e.g. handling a particular exception type) or simplify calling the primary function with particular parameters.
        * **Note**: Implementations must never override an interface's default functions; convenience functions belong as default members, not top-level extensions, so they're discoverable and co-located with the interface.
    * **`domainLayer.domainInterface.primaryParameterTypes`** `📋 guidance` — Primary-function parameters must be domain objects, nested types, primitives, or collections of those.
    * **`domainLayer.domainInterface.primaryReturnType`** `📋 guidance` — Primary-function return type must be domain objects, nested types, primitives, collections of those, or no value.
    * **`domainLayer.domainInterface.implementedByRepositoryOrUseCase`** `📋 guidance` — Must be implemented by a [Repository](#431-repositories) (as a property) or by a [UseCase](#413-usecases).
    * **`domainLayer.domainInterface.errorsViaExceptions`** `✅ tested` — Functions propagate errors via thrown exceptions, never via the return type.
        * **Why**: `@Throws` on `suspend` functions must include `CancellationException` (or a superclass like `Exception`) — required for Kotlin/Native: kotlinc rejects the function on iOS targets otherwise.
        * **Note**: Known exceptions should be their own type extending `RuntimeException`, marked with `@Throws`. Generic/unknown errors don't need their own type or `@Throws` entry.
* **Examples**:
    ```kotlin
    fun interface CreateUser {
        @Throws(UserAlreadyExistsException::class, CancellationException::class)
        suspend operator fun invoke(name: String): User

        class UserAlreadyExistsException : RuntimeException()
    }

    fun interface DeleteUser {
        @Throws(UserNotFoundException::class, CancellationException::class)
        suspend operator fun invoke(userId: String)
    }

    fun interface FlowOfCurrentUser {
        operator fun invoke(): StateFlow<User?>
    }

    fun interface FlowOfUser { 
        @Throws(UserNotFoundException::class)
        operator fun invoke(userId: String): Flow<User>

        fun orNull(userId: String): Flow<User?> {
            return invoke(userId)
                .map { it as User? }
                .catch { ex -> 
                    if (ex is UserNotFoundException) { 
                        emit(null) 
                    } else {
                        throw ex
                    }
                }
        }  
    }

    fun interface FlowOfUsers {
        operator fun invoke(params: Input): Flow<List<User>> 

        fun allUsers(): Flow<List<User>> {
            return invoke(UserSearchInput.AllUsers)
        }

        fun nameContains(searchTerm: String): Flow<List<User>> {
            return invoke(UserSearchInput.NameContains(searchTerm = searchTerm))
        }

        fun isFriendOf(userId: String): Flow<List<User>> {
            return invoke(UserSearchInput.FriendOf(userId = userId)) 
        }
      
        sealed interface Input {
            data object AllUsers : Input
            data class NameContains(val searchTerm: String) : Input
            data class FriendOf(val userId: String) : Input
        }
    }

    class UserNotFoundException : RuntimeException()
    ```

#### **4.1.2 domain objects**
* **Definition**: An immutable type representing data at the domain-level.
* **Requirements** (`🔶 construct`):
    * **`domainLayer.domainObject.valueType`** — A domain object is a sealed/data/enum/value class or interface.
    * **`domainLayer.domainObject.immutable`** — Domain objects must be immutable (val properties only).
    * **`domainLayer.domainObject.serializable`** — Domain objects must be annotated with `@Serializable`.
* **Rules**:
    * **`domainLayer.domainObject.nestedValueClassIds`** `📋 guidance` — Should use nested value classes for identifiers where appropriate (e.g. `value class Id(val value: String)`).
    * **`domainLayer.domainObject.sealedHierarchies`** `📋 guidance` — Should use sealed interface hierarchies to model polymorphic data where appropriate.
    * **`domainLayer.domainObject.invariantInitBlocks`** `📋 guidance` — Should include `init` blocks that enforce invariants.
    * **`domainLayer.domainObject.nestedTypes`** `📋 guidance` — Should use nested types (enums, value classes, sealed interfaces/classes) when conceptually inseparable from the parent; otherwise model them as their own domain objects.
* **Examples**:
    ```kotlin
    @Serializable
    data class User(
        val id: Id,
        val name: String,
        val friends: List<Id>,
    ) {
        @Serializable
        @JvmInline
        value class Id(val value: String)
    }
  
    @Serializable
    data class UserAndFriends(
        val user: User,
        val friends: List<User>,
    ) {
        init {
            require(friends.all { friend -> user.friends.contains(friend.id) }) {
                "All users in friends must have an id matching a value in user.friends"
            }
        }
    } 
  
    @Serializable
    sealed interface Transport {
        val id: String
        val name: String
  
        @Serializable
        data class Car(
            override val id: String,
            override val name: String,
            val fuelType: FuelType,
        ) {
            @Serializable
            enum class FuelType {
                Petrol,
                Diesel,
                Electric,
                Hydrogen,
            }
        }     
        
        @Serializable
        data class Bicycle(
            override val id: String,
            override val name: String,
            val type: Type,
        ) {
            @Serializable
            enum class Type {
                Manual,
                Electric,
            }
        } 

        @Serializable
        data class Bus(
            override val id: String,
            override val name: String,
            val routeId: String,
        )
    }
    ```

#### **4.1.3 UseCases**
* **Definition**: A class that implements a single domain interface.
* **Requirements** (`🔶 construct`):
    * **`domainLayer.useCase.implNaming`** — A UseCase is a non-sealed/data/enum/value class named `[DomainInterface]Impl`.
    * **`domainLayer.useCase.implementsOneInterface`** — A UseCase must implement exactly one domain interface.
    * **`domainLayer.useCase.noMutableState`** — A UseCase must not contain mutable state — all properties are `val`. Immutable helper properties (e.g., loggers) are permitted.
* **Rules**:
    * **`domainLayer.useCase.noOverridingDefaults`** `✅ tested` — Must not override any default function of its domain interface.
        * **Why**: The only abstract member is the primary `operator fun invoke`; every other function is a default. Overriding a default per-implementation defeats the point of the interface helpers.
    * **`domainLayer.useCase.mayInjectDomainInterfaces`** `📋 guidance` — May inject domain interfaces to perform its logic.
        * **Note**: If a UseCase only injects a single other domain interface, consider whether that logic should become a default function of the other domain interface instead.
    * **`domainLayer.useCase.breakDownComplexUseCases`** `📋 guidance` — If it becomes too complex, break it into file-private extension functions, private functions, or nested classes — not additional domain interfaces/UseCases that pollute the namespace.

#### **4.1.4 domain extension functions**
* **Definition**: A top-level extension function on a domain object that adds derived or convenience behavior.
* **Requirements** (`🔶 construct`):
    * **`domainLayer.domainExtensionFunction.compatibleTypes`** — Receiver/return/parameter types are domain objects, primitives, or collections of those.
* **Rules**:
    * **`domainLayer.domainExtensionFunction.noPlatformDeps`** `📋 guidance` — Must not introduce platform-specific dependencies.
* **Note**: Prefer default member functions on domain interfaces for domain-interface convenience logic (see [§4.1.1](#411-domain-interfaces)). Extension functions are appropriate for adding behavior to domain objects (e.g., `CampaignRole.permissions()`).

#### **4.1.5 domain extension properties**
* **Definition**: A top-level extension property on a domain object that exposes derived state.
* **Requirements** (`🔶 construct`):
    * **`domainLayer.domainExtensionProperty.compatibleTypes`** — Receiver/type is a domain object, primitive, or collection of those.
* **Note**: Same constraints as [domain extension functions](#414-domain-extension-functions). Prefer a property when the value is a pure projection of the receiver and is cheap to compute on every read.

#### **4.1.6 domain exceptions**
* **Definition**: A class that represents a known failure mode raised by a domain interface.
* **Requirements** (`🔶 construct`):
    * **`domainLayer.domainException.extendsThrowable`** — A domain exception extends `RuntimeException`, `Exception`, or `PresentableException`.
* **Note**: Domain exceptions live at the top of the `domain` package when shared between multiple domain interfaces, or as a nested class on the domain interface that throws them (see [§4.1.1](#411-domain-interfaces)); they must be listed in `@Throws` on the throwing interface's primary function.

#### **4.1.7 domain constants**
* **Definition**: An `object` declaration whose only members are `val` constants — used to anchor domain-level magic numbers, lookup tables, or named tags.
* **Requirements** (`🔶 construct`):
    * **`domainLayer.domainConstants.objectOfVals`** — Domain constants are an `object` with only `val` properties and no functions.
* **Note**: A constants object is the right home for things like `val MAX_PARTY_SIZE = 6` or a sealed-but-keyed lookup table. Anything that wants behaviour belongs on a domain object as a member or extension.

### **4.2 `ui` package constructs**
#### **4.2.1 Screens**
* **Definition**: A Composable function (or property-based `navigationDestination`) that defines the layout and visual representation of a feature or portion of a feature.
* **Requirements** (`🔶 construct`):
    * **`uiLayer.screen.boundToDestination`** — Screen functions/properties must be bound to their Destination via the `@NavigationDestination` annotation.
    * **`uiLayer.screen.screenNaming`** — Screen functions are named `[Name]Screen`; property-based screens end in `Screen` or `Destination`.
    * **`uiLayer.screen.singleViewModelParameter`** — Screen functions must have a single parameter — the associated `[Name]ViewModel`.
* **Rules**:
    * **`uiLayer.screen.composableFunction`** `📋 guidance` — Screen functions must be annotated with `@Composable`.
    * **`uiLayer.screen.viewModelStateRelationship`** `📋 guidance` — Screen functions have a 1:1 relationship with a [ViewModel](#423-viewmodels) and [ViewModel State](#424-viewmodel-state).
    * **`uiLayer.screen.observesState`** `📋 guidance` — Screen functions must observe the ViewModel's `state` property and use it to drive the UI.
    * **`uiLayer.screen.delegatesInteraction`** `📋 guidance` — Screen functions should delegate all user interaction handling to the ViewModel.
    * **`uiLayer.screen.overlayViaDsl`** `📋 guidance` — Dialog/overlay screens must use the `navigationDestination` DSL with `metadata = { directOverlay() }` (see [§4.2.1.1](#4211-dialog--overlay-screens)).
    * **`uiLayer.screen.overlayViewModel`** `📋 guidance` — Dialog/overlay screens that need a ViewModel should call `viewModel()` inside the `navigationDestination` block.
    * **`uiLayer.screen.screenContentCompanion`** `✅ tested` — Screen functions must be paired with an `internal [Name]ScreenContent` composable in the same file.
        * **Why**: The Screen function plumbs the ViewModel; the `ScreenContent` function takes only state + callbacks so snapshot tests can render every state without a ViewModel. Marking it `internal` lets the host-test source set call it; `private` makes the screen untestable.
    * **`uiLayer.screen.viewModelInjection`** `✅ tested` — ViewModels must be injected into screens using `viewModel()`, not `koinViewModel()`.
        * **Why**: `viewModel()` ties the ViewModel's lifecycle to the navigation backstack entry — when the entry is popped, the ViewModel is cleared. `koinViewModel()` resolves through Koin and either scopes to the wrong lifecycle or returns a singleton, leaking state between screens or returning stale state on re-entry.

#### **4.2.1.1 Dialog / Overlay Screens**
* **Definition**: A Screen that is presented as a dialog or overlay on top of the current screen, rather than pushing to the navigation backstack. Governed by the `screen` construct's `overlayViaDsl` / `overlayViewModel` rules.
* **Note**: Regular screens that push to the backstack should use the standard `@Composable fun` pattern. The property-based `navigationDestination` DSL is specifically for screens that need to declare custom metadata (such as `directOverlay()`). The property name may end in `Screen` or `Destination` — both are accepted because the property *is* the destination declaration site.
* **Example**:
```kotlin
// Destination (in :api)
@Serializable
data class ChangeRoleDestination(
    val memberName: String,
    val currentRole: CampaignRole,
) : NavigationKey.WithResult<CampaignRole>

// Screen (in :client) — property-based with directOverlay metadata
@NavigationDestination(ChangeRoleDestination::class)
val changeRoleScreen = navigationDestination<ChangeRoleDestination>(
    metadata = { directOverlay() }
) {
    val viewModel: ChangeRoleViewModel = viewModel()
    val state by viewModel.state.collectAsState()

    ChangeRoleDialog(
        memberName = state.memberName,
        selectedRole = state.selectedRole,
        onRoleSelected = viewModel::onRoleSelected,
        onConfirm = viewModel::onConfirm,
        onDismiss = viewModel::onDismiss,
    )
}
```

#### **4.2.1.2 Snapshot tests**
* **Definition**: A [Paparazzi](https://github.com/cashapp/paparazzi) host-side test that renders a Screen's `[Name]ScreenContent` (see `uiLayer.screen.screenContentCompanion`) and records a golden image, catching visual regressions without a device or emulator.
* **`uiLayer.composable.screenContentSnapshotTest`** `✅ tested`: Every `[Name]ScreenContent` composable must be exercised by at least one snapshot test. This is enforced **softly** — the test only checks that each ScreenContent is *called* from a `@Test` in an `androidHostTest` source set; it does not require a minimum number of snapshots or coverage of specific states.
    * **Why**: `ScreenContent` exists specifically so the screen body can be rendered from `state` + callbacks. Requiring a snapshot per ScreenContent keeps that affordance honest — a screen can't ship without a recorded visual baseline.
    * **Note**: Snapshot tests live in `feature/.../src/androidHostTest/` (the host-test source set under AGP 9.0's KMP library plugin) and use the `SnapshotRule` helper (`platform.snapshot.SnapshotRule`):
        * `snapshot.screen { ... }` — screen content / composables needing bounded layout constraints (`fillMaxSize()` etc.); renders in a fixed-size container.
        * `snapshot.component { ... }` — small, self-sizing composables; renders at content size with padding.
    * **Note**: The composable under test must be `internal` (not `private`) so the host-test source set can reach it — the same constraint `uiLayer.screen.screenContentCompanion` enforces. Add a `@Test` per meaningful state (loaded, empty, error, …) as a screen grows.
    * **Note**: Record golden images after adding or changing a snapshot test, then verify they match (goldens are committed under `src/androidHostTest/snapshots/images/`):
        ```
        ./gradlew :feature:core:client:recordPaparazzi
        ./gradlew :feature:core:client:verifyPaparazzi
        ```

#### **4.2.2 Destinations (NavigationKeys)**
* **Definition**: A serializable data class or object representing the navigation contract for a particular screen; the input parameters required by that screen (if any) and the output result type provided by that screen (if any).
* **Requirements** (`🔶 construct`):
    * **`uiLayer.destination.classOrObjectShape`** — A Destination is a class or object.
    * **`uiLayer.destination.navigationKeyParent`** — Destinations must implement `dev.enro.NavigationKey` or `NavigationKey.WithResult<T>`, depending on whether the Destination returns a result.
    * **`uiLayer.destination.destinationNaming`** — Destinations must be named `[Name]Destination`.
    * **`uiLayer.destination.serializable`** — Destinations must be serializable and annotated with `@Serializable`.
    * **`uiLayer.destination.fileNameMatchesDeclaration`** — Destinations are declared in a file matching their name.
* **Rules**:
    * **`uiLayer.destination.minimalData`** `📋 guidance` — Should accept the minimal data required to initialise the associated Screen.
        * **Example**: A Destination should accept a `User.Id`, and then the Screen should use this to load the associated `User`, rather than the Destination accepting an entire `User`.
    * **`uiLayer.destination.definedInApiOrClient`** `📋 guidance` — May live in `:api` (shared entry point / server-driven navigation) or `:client` (used only internally within the feature).

#### **4.2.3 ViewModels**
* **Definition**: A class that manages the UI state for a Screen and orchestrates calls to domain interfaces to load data and perform side effects based on user actions.
* **Requirements** (`🔶 construct`):
    * **`uiLayer.viewModel.extendsViewModel`** — ViewModels extend `androidx.lifecycle.ViewModel`.
    * **`uiLayer.viewModel.viewModelNaming`** — ViewModels must be named `[Name]ViewModel`.
    * **`uiLayer.viewModel.singlePublicStateProperty`** — ViewModels expose a single public `state` property, or no public properties at all.
    * **`uiLayer.viewModel.stateIsViewModelStateType`** — The `state` property is a `ViewModelState<[Name]State>` (1:1 with the ViewModel's State type).
    * **`uiLayer.viewModel.navigationHandleProperty`** — ViewModels have a `private val navigation` obtained via `navigationHandle<[Name]Destination>()`, used to read Destination parameters and perform navigation.
    * **`uiLayer.viewModel.publicFunctionsReturnUnit`** — `public`/`internal` functions on a ViewModel must only return `Unit` (or omit a return type).
    * **`uiLayer.viewModel.fileNameMatchesDeclaration`** — ViewModels must be defined in their own file (`[Name]ViewModel.kt`).
* **Rules**:
    * **`uiLayer.viewModel.injectsDomainInterfaces`** `📋 guidance` — Should inject domain interfaces to load and manipulate domain objects.
    * **`uiLayer.viewModel.usesJobManager`** `✅ tested` — Must use `JobManager` to manage coroutines — never hold `var job: Job?` references.
        * **Why**: Manual `var job: Job?` tracking is error-prone: the previous job leaks if a new one starts before the old one completes, and lifecycle cancellation is easy to forget. `dev.isaacudy.udytils.coroutines.JobManager` handles cancel-then-replace and ties everything to `viewModelScope`.
* **Note**: When closing/completing a screen, use `NavigationHandle.close` when the user is cancelling or backing out, and `NavigationHandle.complete` when the user has successfully performed an action.

#### **4.2.4 ViewModel State**
* **Definition**: The complete, immutable representation of a Screen's data at a single point in time.
* **Requirements** (`🔶 construct`):
    * **`uiLayer.viewModelState.dataClass`** — ViewModel State objects must be a `data class`.
    * **`uiLayer.viewModelState.stateNaming`** — ViewModel State objects are named `[Name]State`.
    * **`uiLayer.viewModelState.immutable`** — ViewModel State objects must be immutable (val properties only).
    * **`uiLayer.viewModelState.fileNameMatchesDeclaration`** — ViewModel State objects must be defined in their own file (`[Name]State.kt`).
* **Rules**:
    * **`uiLayer.viewModelState.viewModelRelationship`** `📋 guidance` — Have a 1:1 relationship with a [ViewModel](#423-viewmodels) type.
    * **`uiLayer.viewModelState.usesAsyncState`** `📋 guidance` — Must use `AsyncState<T>` / `UpdatableState<T>` for asynchronously loaded data and action progress (e.g. a "save" action as `AsyncState<Unit>`).
        * **Note**: Never directly construct `AsyncState.Loading`/`Success`/`Error` — use `AsyncState.fromSuspending`/`fromFlow`. That prohibition is enforced project-wide by `projectRules.noDirectAsyncStateConstruction` (`✅ tested`).
    * **`uiLayer.viewModelState.noCustomAsyncSealedTypes`** `📋 guidance` — Must not define custom sealed types for loading/success/error — use `AsyncState<T>` (custom types duplicate its semantics and bypass its exception handling).
    * **`uiLayer.viewModelState.transparentContainer`** `📋 guidance` — Should be a transparent container for domain objects, not lossy UI-level mappings (e.g. mapping a `User` into a `UserListItem`).
    * **`uiLayer.viewModelState.invariantInitBlocks`** `📋 guidance` — Should include `init` blocks that enforce invariants.
    * **`uiLayer.viewModelState.formattingInScreen`** `📋 guidance` — Formatting and visual representation (string concatenation, date formatting, resource resolution) must be handled by the [Screen](#421-screens) or specialized `@Composable` properties/functions.
* **Example**:
    ```kotlin
    // feature.user.ui.UserDetailState.kt
    data class UserDetailState(
        val user: User,
        val isEditing: Boolean,
    ) {
        // Calculated property for logic
        val canEditName: Boolean get() = user.isVerified && isEditing
    }
        
    // feature.user.ui.UserDetailScreen.kt
    // Extension property for display
    val User.displayRole: String
        @Composable
        get() = when(role) {
            User.Role.Admin -> stringResource(Res.string.role_admin)
            User.Role.Member -> stringResource(Res.string.role_member)
        }
    ```

#### **4.2.5 UI Composables (non-screen)**
* **Definition**: A `@Composable` function defined in the `..ui..` package that is **not** a [Screen](#421-screens) — typically a sub-component used by one or more screens, an inline editor, or a feature-specific overlay.
* **Requirements** (`🔶 construct`):
    * **`uiLayer.composable.notAScreen`** — Is not a Screen.
    * **`uiLayer.composable.composableFunction`** — Is a `@Composable` function.
* **Note**: The `screenContentSnapshotTest` rule (see [§4.2.1.2](#4212-snapshot-tests)) belongs to this construct — `[Name]ScreenContent` is a non-Screen composable. For reusable design-system primitives (buttons, fields, marks), prefer a Parchment composable in `:platform:client:ui`. Feature-local composables live alongside the Screen they support, and may be `internal` so snapshot tests can drive them.

#### **4.2.6 UI value types**
* **Definition**: A small closed value type (enum, sealed class, or sealed interface) that lives in `..ui..` and crosses feature boundaries — e.g. a `Slot` tag that one feature's ViewModel passes back to another feature's screen.
* **Requirements** (`🔶 construct`):
    * **`uiLayer.uiValueType.enumOrSealed`** — Is an `enum class`, `sealed class`, or `sealed interface`.
    * **`uiLayer.uiValueType.noMemberFunctions`** — Has no member functions; pure data shape only.
* **Note**: If a value type grows behaviour, it stops being a value type — promote it into a State, Destination, or domain object as appropriate.

### **4.3 `data` package constructs (`:client` only)**

The `data` axis is **client-only**. Server-side persistence and service implementations live in the `services` axis (see [§4.4](#44-services-package-constructs)). The client's `data` package holds Repositories that fan out across [Services](#441-services-the-cross-the-wire-contract) (the `:api` contract) and client-side local storage. Layer-level rules (`dataLayer.providesDomainImplementations`, `dataLayer.noInjectingDomainInterfaces`, `dataLayer.noUiDeps`) are documented in [§3.3](#33-data-package-in-client-only).

#### **4.3.1 Repositories**
* **Definition**: A class that provides implementations for [Domain Interfaces](#411-domain-interfaces), providing the "edge" of the domain layer.
* **Requirements** (`🔶 construct`):
    * **`dataLayer.repository.isClass`** — A Repository is a class.
    * **`dataLayer.repository.nameEndsWithRepository`** — Repositories must be named `[Name]Repository`.
    * **`dataLayer.repository.internalVisibility`** — Repositories must be marked as `internal`.
    * **`dataLayer.repository.fileNameMatchesDeclaration`** — A Repository is declared in a file whose name matches the class.
    * **`dataLayer.repository.doesNotImplementDomainInterfaces`** — Repositories must not implement domain interfaces directly.
    * **`dataLayer.repository.exposesDomainInterfacesAsProperties`** — Repositories must expose domain interfaces as `public val` properties.
    * **`dataLayer.repository.doesNotInjectDomainInterfaces`** — Repositories are forbidden from injecting domain interfaces.
    * **`dataLayer.repository.doesNotInjectRepositories`** — Repositories are forbidden from injecting other Repositories.
* **Rules**:
    * **`dataLayer.repository.propertiesEagerlyInitialized`** `✅ tested` — Domain-interface properties must be initialized immediately — no `by lazy`, no custom getter.
        * **Why**: Eager initialisation lets Koin's graph validation catch missing or cyclic dependencies at startup instead of at the first injection at runtime, and it makes the wiring obvious from a quick read of the constructor.
    * **`dataLayer.repository.mayInjectServicesStorageOrClients`** `📋 guidance` — May inject [Services](#441-services-the-cross-the-wire-contract), client-side `data.storage` Storage objects, or database clients to fulfill their domain properties.
* **Note**: The property name must match the interface name using `lowerCamelCase` (e.g., `val createUser = CreateUser { ... }`).
* **Example**:
```kotlin
internal class UserRepository(
    private val userService: UserService,
    private val userStorage: UserStorage, // Local storage
) {
    val getUser = GetUser { id ->
        userService.getUser(UserService.GetUser.Request(id)).user
    }

    val deleteUser = DeleteUser { id ->
        userService.deleteUser(UserService.DeleteUser.Request(id))
    }
}
```

#### **4.3.1.1 Non-Repository client data abstractions**
* **Definition**: A client-side interface or class declared in `..data..` that is **not** a Repository — typically a low-level concern with platform-specific actuals (e.g., `BinaryUploadClient` for chunked file upload). Modelled by two constructs:
* **`dataLayer.clientDataInterface`** (`🔶 construct`): an `interface` (`isInterface`) that resides in `feature.[name].data`, not `data.storage` (`residesInData`).
* **`dataLayer.clientDataImplementation`** (`🔶 construct`): a class (`isClass`) that is not named `Repository` (`notNamedRepository`) and resides in `feature.[name].data` (`residesInData`).
* **Note**: These exist to give Repositories a clean abstraction over a concrete platform capability. If you find yourself writing one, ask whether it belongs in `:platform:client` instead — feature-local data abstractions are appropriate when the contract is feature-specific.

#### **4.3.2 `data.storage` package constructs (`:client` only)**

##### **4.3.2.1 Client-side Storage classes**
* **Definition**: A class responsible for local-device data persistence and retrieval (e.g., credentials, preferences, cached data on disk). Modelled by the `dataLayer.clientStorage` construct.
* **Requirements** (`🔶 construct`):
    * **`dataLayer.clientStorage.isClass`** — A client Storage class is a class.
    * **`dataLayer.clientStorage.nameEndsWithStorage`** — Storage classes must be named `[Name]Storage`.
    * **`dataLayer.clientStorage.notAbstract`** — Storage classes must not be abstract.
    * **`dataLayer.clientStorage.notDataClass`** — Storage classes must not be `data class`.
    * **`dataLayer.clientStorage.residesInDataStorage`** — Storage classes must reside in the `data.storage` package on `:client`.
* **Rules**:
    * **`dataLayer.clientStorage.internalVisibility`** `📋 guidance` — Must be marked `internal` (the `expect` declaration may be public, but `actual` implementations should be `internal` where the language allows).
    * **`dataLayer.clientStorage.doesNotInjectDomainRepositoriesOrServices`** `✅ tested` — Forbidden from injecting domain interfaces, [Repositories](#431-repositories), or [Services](#441-services-the-cross-the-wire-contract).
        * **Why**: Storage is the lowest layer of the stack — it should depend on the database/keychain client and nothing higher. Injecting a domain interface, Repository, or Service would embed orchestration logic in the persistence layer.
* **Note**: Client-side Storage classes may be `expect`/`actual` classes when the underlying storage mechanism is platform-specific (e.g., Keychain on iOS, SharedPreferences on Android).
* **Example**:
```kotlin
// commonMain
expect class AuthCredentialStorage() {
    val authCredentials: StateFlow<AuthCredentials?>
    fun setAuthCredentials(authCredentials: AuthCredentials?)
}

// androidMain
actual class AuthCredentialStorage actual constructor() {
    // Android-specific implementation using SharedPreferences/DataStore
}
```

### **4.4 `services` package constructs**

The `services` axis covers both the `:api` Service contract and the entire `:server` implementation surface — ServiceImpls, internal helpers/orchestrators, and Postgres storage. Layer-level rules (`servicesLayer.mustNotDependOnData`, `servicesLayer.crossFeatureViaApi`, `servicesLayer.internalHierarchicalVisibility`, `servicesLayer.storageMustNotDependOnInternal`, `servicesLayer.toolsApiContractOnly`) are documented in [§3.4](#34-services-package-in-api-and-server).

#### **4.4.1 Services (the cross-the-wire contract)**
* **Definition**: The client-server contract (in `:api`) and its implementation (in `:server`). Services use **urpc** (`dev.isaacudy.udytils:urpc-*`): KSP generates the client, the `UrpcService` server binding, and the wire descriptors from the annotated interface.
* **Requirements** (`🔶 construct`):
    * **`servicesLayer.serviceInterface.urpcInterface`** — A service is an `interface` annotated `@Urpc`.
    * **`servicesLayer.serviceInterface.nameEndsWithService`** — Is named `[Name]Service`.
    * **`servicesLayer.serviceInterface.inServicesContractPackage`** — Resides in the top-level `feature.[name].services` package.
* **Rules**:
    * **`servicesLayer.serviceInterface.noClientOnlyServices`** `📋 guidance` — Always implement services as urpc service functions in the appropriate server module — do not build client-only local services.
    * **`servicesLayer.serviceInterface.plainFunctionShapes`** `📋 guidance` — Functions are plain `suspend fun f(req): Res` (unary), `fun f(req): Flow<Res>` (server-streaming), or `fun f(reqs: Flow<Req>): Flow<Res>` (bidirectional), each taking 0 or 1 parameter.
    * **`servicesLayer.serviceInterface.nestedRequestResponseTypes`** `📋 guidance` — Each function's `Request`/`Response` types are nested `@Serializable` types grouped under a per-function `object` namespace.
    * **`servicesLayer.serviceInterface.contractLivesInApi`** `📋 guidance` — Service interfaces live in `feature.[name].services` of the `:api` module.
    * **`servicesLayer.serviceInterface.errorsViaExceptions`** `✅ tested` — Service functions propagate errors via thrown exceptions; the return type only ever represents a successful result.
        * **Why**: `@Throws` on `suspend` functions must include `CancellationException` (or a superclass like `Exception`) — required for Kotlin/Native: kotlinc rejects the function on iOS targets otherwise.
        * **Note**: Known service exceptions should be their own `@Serializable` type (ideally a `PresentableException` with a deliberate `retryable` flag — see [§5.1](#51-exception-handling)).
* **Example**:
```kotlin
// feature.user.services.UserService.kt (:api)
@Urpc
interface UserService {
    suspend fun createUser(request: CreateUser.Request): CreateUser.Response
    suspend fun getUser(request: GetUser.Request): GetUser.Response
    fun observeUsers(): Flow<ObserveUsers.Response>

    object CreateUser {
        @Serializable data class Request(val name: String, val email: String)
        @Serializable data class Response(val user: User)
    }
    // ...
}
```

#### **4.4.2 Service implementations (`:server`)**
* **Definition**: Implementations of `Service` interfaces (see [§4.4.1](#441-services-the-cross-the-wire-contract)). Modelled by the `servicesLayer.serviceImpl` construct (a ServiceImpl lives in `feature.[name].services` of `:server`, so it belongs to the `services` axis, not the top-level feature group).
* **Requirements** (`🔶 construct`):
    * **`servicesLayer.serviceImpl.serviceImplClass`** — For a service named `[Name]Service` the implementation is a class named `[Name]ServiceImpl`.
    * **`servicesLayer.serviceImpl.internalVisibility`** — Service implementations must be `internal`.
    * **`servicesLayer.serviceImpl.inServerServicesPackage`** — Resides in `feature.[name].services` of the `:server` module (dual-life with the contract).
* **Rules**:
    * **`servicesLayer.serviceImpl.noInjectingDomainInterfaces`** `📋 guidance` — Service implementations are forbidden from injecting domain interfaces.
        * **Why**: A ServiceImpl is the server-side request handler; it reaches *down* into `services.storage` and `services.internal`, not sideways into the domain interfaces a client would consume.
        * **Note**: Surfaced as guidance rather than a construct shape, because forbidding domain-interface injection is a prohibition (not a classification) and re-expressing it would require resolving the domain-interface classifier from another layer.
    * **`servicesLayer.serviceImpl.mayInjectStorageAndInternal`** `📋 guidance` — May inject `services.storage` Storage classes and `services.internal` orchestrators of the same feature, plus other features' Service contracts via `:api`.
    * **`servicesLayer.serviceImpl.noUiDependency`** `✅ tested` — Service implementations must not depend on the `ui` package.
        * **Why**: ServiceImpls run on the server and have no Compose runtime — a UI import here would either fail to compile in `:server` or mean a UI type has been pulled out of `ui` and is being treated as data, both of which are wrong. If you need a shared shape with the UI, put it in the feature's `:api` domain or services package.

#### **4.4.3 `services.internal` package**

* **Definition**: Server-side coordinator and helper classes — the things that do the work the ServiceImpl orchestrates. The bare `services.internal` package holds top-level orchestrators plus shared-payload data types; `services.internal.<subsystem>` packages are sealed islands under hierarchical visibility (`servicesLayer.internalHierarchicalVisibility`, see [§3.4.5](#345-hierarchical-visibility-within-servicesinternal)).
* The package is modelled by five constructs, each requiring its shape plus residence in `feature.[name].services.internal`:
    * **`servicesLayer.internalCoordinator`** (`🔶 construct`): a concrete (non-`abstract`, non-`data`) class that is not a `Job` or `Exception` — the orchestrators that compose subsystems.
    * **`servicesLayer.internalDataCarrier`** (`🔶 construct`): a `data class` payload that flows between subsystems through the orchestrator (lives at the bare `services.internal` ancestor so both producer and consumer can name it under the data-shape carve-out).
    * **`servicesLayer.internalInterface`** (`🔶 construct`): an abstraction used inside a subsystem (e.g. a strategy contract whose implementations live in the same subpackage).
    * **`servicesLayer.internalObjectHelper`** (`🔶 construct`): an `object` holding pure helpers.
    * **`servicesLayer.internalException`** (`🔶 construct`): a class named `[Name]Exception`, thrown only by internal helpers — service-level exceptions belong on the `Service` interface (see [§4.4.1](#441-services-the-cross-the-wire-contract)).

#### **4.4.4 `services.storage` package — Postgres persistence**

> **ukpt status**: the Postgres toolkit lives in the `embedded-udytils` submodule (`:postgres-core/koin/codegen/gradle-plugin/embedded`), so these rules are the documented persistence standard. The `:platform:server:postgres` module that applies the codegen plugin and owns the Flyway migrations is **created when the first server feature needs persistence** — until then the `services.storage` rules below pass vacuously (no storage code exists yet).

* **Definition**: A feature's persistence storage classes and mappings, built on **[Exposed](https://github.com/JetBrains/Exposed)** and the **`dev.isaacudy.udytils.postgres`** runtime. That runtime (in the `embedded-udytils` submodule, re-exported by `:platform:server:postgres` via `api(libs.udytils.postgres.core)`) provides `PostgresConfig`, `PostgresMigrator`, `PgNotificationBus`, and the custom Exposed column types (`JsonbColumnType`, `JsonColumnType`, `TextArrayColumnType`, `TimestampColumnType`) — do **not** hand-roll these in feature code; extend the library instead.
* **Contents (hand-written, in the feature)**: `[Name]Storage` classes, mapping functions (conventionally collected in `[Name]Mappers.kt`), and codec objects.
* **Contents (generated, NOT in the feature)**: the Exposed `Table` objects and `XxxRow` data classes are generated into the **shared `platform.server.postgres.tables` package** (`:platform:server:postgres`) and imported across every feature's storage code — see [§4.4.4.2](#4442-table-objects-generated) and the pipeline in [§4.4.4.7](#4447-postgres-codegen-pipeline--runtime).

##### **4.4.4.1 Storage classes**
* Modelled by the `servicesLayer.storageClass` construct.
* **Requirements** (`🔶 construct`):
    * **`servicesLayer.storageClass.storageNameSuffix`** — Named `[Name]Storage` (or `[Name]Store` where the broader name fits).
    * **`servicesLayer.storageClass.notAbstractNotData`** — Not abstract, not a `data class`.
    * **`servicesLayer.storageClass.internalVisibility`** — `internal` visibility.
    * **`servicesLayer.storageClass.inStoragePackage`** — Resides in `feature.[name].services.storage`.
* **Rules**:
    * **`servicesLayer.storageClass.returnsRowTypesOnly`** `✅ tested` — Must take/return `XxxRow` types only — never domain types.
        * **Why**: Domain conversion lives in mapping functions (`XxxRow.toDomain()`). A Storage method that returns a domain type embeds mapping logic in the persistence layer; the ServiceImpl should do the Row→Domain conversion instead.
    * **`servicesLayer.storageClass.partialUpdatesByHand`** `📋 guidance` — When an operation touches only a subset of columns, keep the hand-written `update { … it[col] = value … }` block — `setFromRow` writes every column and is wrong here (see [§4.4.4.5](#4445-partial-updates)).

##### **4.4.4.2 `Table` objects (generated)**

> All `Table`/`Row` rules in §4.4.4.2–§4.4.4.3 are `⚙️ codegen` — guaranteed by the `dev.isaacudy.udytils.postgres` plugin, not by Konsist (the generated sources live under `build/generated/` and are never scanned). They live in the shared `platform.server.postgres.tables` package, not in any feature's `services.storage`. They are declared as group-level codegen rules on `servicesLayer`.

* **`servicesLayer.generatedTableRowSources`** `⚙️ codegen`: `Table`/`Row` sources are generated by the **`dev.isaacudy.udytils.postgres`** Gradle plugin (applied in `:platform:server:postgres`) from the Flyway-migrated schema, into the shared package `platform.server.postgres.tables`. The plugin registers two tasks — `generatePostgresTables` (the Exposed sources) and `exportPostgresSchema` (the committed `schema.sql` snapshot). Generated files live under `build/generated/source/postgres-tables/`, carry a `Generated by the dev.isaacudy.udytils.postgres Gradle plugin` header, and are not committed.
* **`servicesLayer.generatedTableObjects`** `⚙️ codegen`: Each persisted entity has a generated `object XxxTable : Table("xxx")` (plural, matching the SQL table name); custom columns are typed with the udytils column types (`JsonbColumnType`, `TextArrayColumnType`, …).
* **`servicesLayer.everyColumnOnTable`** `⚙️ codegen`: Every column on the SQL table is declared on the `Table` object, with no omissions. The generated UUID primary key is emitted as `uuid("id").autoGenerate()`, but the write path always supplies the id explicitly via `Domain.toRow(...)` / `setFromRow`, so the generated default is never relied upon.

##### **4.4.4.3 `Row` data classes (generated)**
* **`servicesLayer.rowDataClassPrimitives`** `⚙️ codegen`: The in-memory persistence shape is a top-level `data class XxxRow` (singular). Fields use only **primitive types** — no domain wrappers, no enums, no sealed hierarchies.
* **`servicesLayer.rowFakeConstructorAndSetFromRow`** `⚙️ codegen`: Each generated file exposes a "fake-constructor" `fun XxxRow(row: ResultRow): XxxRow` for reads, and a `fun UpdateBuilder<*>.setFromRow(row: XxxRow)` extension for writes.
* The hand-written persistence record shape (the `XxxRow`/`XxxRecord`/`XxxInsert` `data class`es that live in a feature's `services.storage`) is classified by the **`servicesLayer.storageRecord`** construct (`🔶 construct`: `rowDataClass`, `rowNameSuffix`, `inStoragePackage`).
* **Example**:
```kotlin
// Read
val row: UserProfileRow? = UserProfilesTable
    .selectAll()
    .where { UserProfilesTable.userId eq userId }
    .singleOrNull()
    ?.let(::UserProfileRow)

// Write
UserProfilesTable.upsert(UserProfilesTable.userId) {
    it.setFromRow(row)
}
```

##### **4.4.4.4 Mapping functions**
* Modelled by the `servicesLayer.mappingFunction` construct (`🔶 construct`: `topLevelFunction`, `inStoragePackage`).
* **`servicesLayer.mappingFunction.mappersInStorage`** `📋 guidance`: Conversions between a generated `XxxRow` and a domain type live in `services.storage` as plain `internal fun` declarations, conventionally collected in `[Name]Mappers.kt`.
    * **Convention**: `XxxRow.toDomain()` for `Row → Domain`; `Domain.toRow(...)` for the inverse.
* **`servicesLayer.mappingFunction.multiTableLoadHelpers`** `📋 guidance`: Where storage operations span multiple tables to assemble a richer "record" type, define those higher-level helpers as `suspend fun [Name]Storage.loadXxx(...)` extensions in `services.storage`.

##### **4.4.4.5 Partial updates**
* **`servicesLayer.storageClass.partialUpdatesByHand`** `📋 guidance`: When an operation touches only a subset of columns, keep the hand-written `update { ... it[col] = value ... }` block. `setFromRow` writes every column and is wrong for these cases.

##### **4.4.4.6 Codec objects**
* **Definition**: The read/write codec for a column whose on-disk shape differs from the domain shape — either an `object` holding discriminator constants (e.g. `ChatMessageContentTypeCodec`, `ProcessingStatusCodec`) or file-private `Json` + `encode`/`decode` helpers in the `[Name]Mappers.kt` file. Modelled by the `servicesLayer.codecObject` construct.
* **Requirements** (`🔶 construct`):
    * **`servicesLayer.codecObject.codecObjectShape`** — A codec is an `object` (discriminator constants, or file-private `Json` + encode/decode helpers).
    * **`servicesLayer.codecObject.inStoragePackage`** — Lives in `services.storage` alongside the Row + mapping functions for the table that uses it.
* **Rules**:
    * **`servicesLayer.codecObject.keyedToColumn`** `📋 guidance` — Encapsulate the read/write asymmetry `setFromRow` can't express; keep them small and keyed to the column they serve.

##### **4.4.4.7 Postgres codegen pipeline & runtime**

The persistence stack is built on the **`dev.isaacudy.udytils.postgres`** library (developed in the `embedded-udytils` submodule) plus **Exposed**, **Flyway**, and a **Zonky** embedded Postgres:

* **Schema** lives only in `:platform:server:postgres/src/main/resources/db/migration/` as Flyway scripts — versioned `V<n>__snake_name.sql` (run once, in order) and repeatable `R__name.sql` (re-run whenever their checksum changes, e.g. `R__notify_triggers.sql`). A schema change is a **new** `V<n>` file; existing `V<n>` files are never edited in place.
* **`exportPostgresSchema`** Flyway-migrates a throwaway Zonky Postgres and writes a normalised `schema.sql` snapshot; **`generatePostgresTables`** then emits the Exposed `Table`/`Row` sources from it into `platform.server.postgres.tables`. Both tasks are registered by the `dev.isaacudy.udytils.postgres` Gradle plugin and run before `compileKotlin`.
* **Runtime ownership**: the DB primitives (`PostgresConfig`, `PostgresMigrator`, `PgNotificationBus`, the column types) live in the udytils library; `:platform:server:postgres` owns only the SQL migrations + codegen wiring and re-exports the runtime; the **application** (`:app:server`) owns its connection config (`ukptPostgresConfigFromEnv()`), wires `postgresDependencies(config)` (from `dev.isaacudy.udytils.postgres.koin`), and runs `PostgresMigrator.migrate()` before it starts serving.

##### **4.4.4.8 Reactive storage flows (`PgNotificationBus`)**

A `[Name]Storage` class may expose `Flow` reads that re-query when a Postgres `NOTIFY` fires, by injecting `dev.isaacudy.udytils.postgres.PgNotificationBus`. The channel name is a `companion object const val CHANNEL` and **must** match a `pg_notify(...)` trigger in the migrations (e.g. `R__notify_triggers.sql`). The shape is: emit an initial query, then `bus.listen(CHANNEL).filter { it == key }.collect { emit(query()) }`. This is convention, not a statically-enforced rule.

#### **4.4.5 `services.tools` package (reserved)**
* **Definition**: Reserved for AI tool-use subclasses (e.g. `AssistantTool` wrappers around a service). ukpt has no AI subsystem, so `services.tools` is intentionally **empty** — it defines no construct, so any declaration placed here fails the layer-exhaustiveness check until a construct is defined for it.
* Its isolation is enforced by the layer-level **`servicesLayer.toolsApiContractOnly`** `✅ tested` (see [§3.4.3](#343-servicestools-package-in-server-reserved)) — tools may only depend on the `:api`-defined Service contract, never on `services.storage` or `services.internal`.
* **Note**: If an AI subsystem is added later, reintroduce an `assistantTool` construct (extends `AssistantTool`, named `[Action][Entity]Tool`) on `servicesLayer` to populate this layer.

### **4.5 top-level package constructs**

The top-level `feature.[name]` package is reserved for DI wiring. Concrete classes (ServiceImpls, helpers, etc.) live in their layer-specific package; nothing else belongs here.

#### **4.5.1 Dependency modules**
* **Definition**: The configuration for Dependency Injection (DI) that wires the feature together.
* **Requirements** (`🔶 construct`):
    * **`featureRules.dependencyModule.inTopLevelFeaturePackage`** — DI modules must be defined in the top-level `feature.[name]` package of the `:client` and `:server` modules.
    * **`featureRules.dependencyModule.koinValModule`** — DI modules are Koin `val` modules whose names end in `Dependencies`. The convention is `[name]ClientDependencies` in `:client` and `[name]ServerDependencies` in `:server`; the construct enforces the `Dependencies` suffix, the `Client`/`Server` infix is convention.
* **Rules**:
    * **`featureRules.dependencyModule.ownFeatureBindingsOnly`** `✅ tested` — The DI module for a feature must only bind/provide dependencies that are both defined and implemented in that feature.
        * **Example**: It is forbidden for "featureA" to implement and bind a domain interface that is defined by "featureB".
    * **`featureRules.dependencyModule.urpcServiceBinding`** `📋 guidance` — Register a service's generated `[Name]ServiceUrpcBinding` by chaining `.bindService(::[Name]ServiceUrpcBinding)` off the implementation's binding, inside the per-call `scope<UrpcCall> { }` block:
      ```kotlin
      scope<UrpcCall> {
          scopedOf(::UserProfileServiceImpl)
              .bind(UserProfileService::class)
              .bindService(::UserProfileServiceUrpcBinding)
      }
      ```
        * **Note**: `bindService` (from `dev.isaacudy.udytils.urpc.koin`) registers the binding under its own concrete type, bound to `UrpcService`, with the impl resolved lazily.
        * **Note**: Do **not** use `scoped<UrpcService> { [Name]ServiceUrpcBinding { get() } }` — every such binding shares the same `UrpcService` definition key, so co-registered services override each other and the host's `getAll<UrpcService>()` returns only one, 404-ing the rest. (`urpcService(::[Name]ServiceUrpcBinding)` is the equivalent standalone form when there is no impl definition to chain off.)
* **DI registration helpers**: a `Module`-receiver registration function is classified by the **`featureRules.dependencyModuleHelper`** construct (`🔶 construct`: `declaredAsFunction`, `internalVisibility`, `moduleReceiver`).
* **`featureRules.constructorReferenceBindings`** `✅ tested` (layer-level): DI bindings must use the constructor reference style `singleOf(::Constructor).bind(BindingType::class)`, not the lambda style `single<BindingType> { Constructor(get()) }`.
    * **Why**: The reference style lets Koin validate the constructor parameters against the graph at startup; the lambda style hides missing or cyclic dependencies until the first injection at runtime.
* **Note**: It is the responsibility of `:app` level modules (application shells) to collect all of the DI modules provided by feature modules and create the final dependency graph. When a new dependency module is added, it must be registered in both `:app:client:shared` and `:app:server`; when a new Service is added, it must be registered in `:app:server`.
* **Example**:
```kotlin
// feature.user.UserServiceImpl.kt (:server)
internal class UserServiceImpl(
    private val userStorage: UserStorage,
    private val sessionAuth: SessionAuth,
) : UserService {

    override suspend fun createUser(request: UserService.CreateUser.Request): UserService.CreateUser.Response {
        val userId = sessionAuth.requireUser().first()
        val user = userStorage.insertUser(
            row = Users.Row(name = request.name, email = request.email)
        )
        return UserService.CreateUser.Response(user = user)
    }

    override suspend fun getUser(request: UserService.GetUser.Request): UserService.GetUser.Response {
        val user = userStorage.getUser(request.userId)
        return UserService.GetUser.Response(user = user)
    }

    override fun observeUsers(): Flow<UserService.ObserveUsers.Response> =
        userStorage.observeAll()
            .map { UserService.ObserveUsers.Response(users = it) }
}
```

---

## **5. Project-Wide Code Rules**

These are layer-level `projectRules` — they are not tied to a construct or a single package.

### **5.1 Exception handling**
* **`projectRules.noCatchException`** `✅ tested`: `try/catch` blocks must never catch `Exception` — use `catch (t: Throwable)` or a specific exception type instead.
    * **Why**: The urpc transport (`dev.isaacudy.udytils:urpc-*`) deserialises server-side exceptions into types that may not extend `Exception` (e.g. kotlinx-serialization / kRPC error types). A `catch (Exception)` block silently misses these, so the error propagates uncaught and crashes on an internal thread instead of being handled by application code.
    * **Note**: On the client, prefer `AsyncState.fromSuspending` over manual `try/catch` — it captures exceptions correctly and integrates with the ViewModel state pattern. Catching a specific exception type (e.g. `catch (t: IllegalArgumentException)`) is always acceptable when you only want to handle that case.
* **`projectRules.serviceExceptionsSerializable`** `✅ tested`: Exception types defined in `services` (the cross-the-wire contract) must be annotated with `@Serializable`.
    * **Why**: The urpc transport deserialises server-side exceptions into typed payloads on the client; without `@Serializable` the type and message are lost in transit and the client receives a generic deserialisation failure. Exceptions inside `services.internal.*` stay server-side and don't cross the wire, so they are out of scope.
    * **Note**: Prefer subclassing `PresentableException` with a deliberate `retryable` flag — streaming flows auto-retry retryable errors and surface terminal ones; the unary error UI offers a Retry action only when `retryable`.
* **`projectRules.noDirectAsyncStateConstruction`** `✅ tested`: `AsyncState.Loading`/`Success`/`Error` must not be constructed directly — use `AsyncState.fromSuspending`/`fromFlow`.
    * **Why**: Direct construction skips the exception capture, cancellation, and state-flow protocol that `fromSuspending`/`fromFlow` handle uniformly. Files that legitimately build `AsyncState` values (defining its semantics, or the server-side status pattern) opt out with `@file:ArchitectureException`.

### **5.2 Imports**
* **`projectRules.noWildcardImports`** `✅ tested`: Imports must not use wildcards — always list the explicit symbols.
    * **Why**: Wildcards hide which symbols a file depends on, break a number of architecture-test checks (which inspect import names directly), and silently pull in new names when the imported package adds members.

### **5.3 Action and request types**
* **`projectRules.sealedActionVariants`** `📋 guidance`: Model action/request variants as a `sealed interface`/`sealed class` (each variant a `data class` holding only the fields it needs), not a single type with an `enum` discriminator and nullable/optional fields.
    * **Why**: A sealed hierarchy makes illegal field combinations unrepresentable and lets `when` exhaustiveness drive handling, so adding a variant surfaces every site that must handle it.
    * **Example**:
        ```kotlin
        // Good
        sealed interface UserAction {
            data class Rename(val id: User.Id, val newName: String) : UserAction
            data class Delete(val id: User.Id) : UserAction
        }

        // Avoid
        enum class ActionType { RENAME, DELETE }
        data class UserActionRequest(val id: User.Id, val type: ActionType, val newName: String? = null)
        ```
    * **Note**: Enforced by review, not a static test — "an enum that should be a sealed class" can't be detected reliably by Konsist.

---

## **6. Architecture Exceptions**

Architecture rules are enforced by the registry-driven Konsist tests in `:platform:common:architecture`. When a specific declaration cannot conform to a rule (e.g. a transitional class whose ideal location hasn't been determined yet), the declaration can be marked exempt from that rule so the tests pass while the exception is tracked explicitly.

### **6.1 How to add an exception**

There are two exemption mechanisms, depending on what kind of file the exempt code lives in. Both reference rules by their [path id](#rule-ids).

#### **Kotlin source files: `@ArchitectureException`**

Add the [`@ArchitectureException`](src/main/kotlin/architecture/ArchitectureException.kt) annotation either at file level (above the `package` line) or on the specific declaration:

```kotlin
@file:ArchitectureException(
    ruleIds = ["servicesLayer.internalHierarchicalVisibility"],
    reason = "Sessions' audio subsystem reaches a sibling subsystem's helper for transcription " +
        "phrase hints. The shared accessor hasn't been promoted to a common ancestor yet — until " +
        "it is, this cross-subsystem import is the cheapest way to keep a single authoritative path.",
    trackingIssue = "",
)

package feature.sessions.services.internal.audio

import architecture.ArchitectureException
// ...
```

`ruleIds` lists the rule path ids the declaration is exempt from (see the [Rule IDs](#rule-ids) section). `reason` is free-form prose; `trackingIssue` is optional but recommended.

The architecture tests look up the annotation via Konsist when checking each rule, and skip declarations / files that list the rule's id.

#### **Gradle build files: `// architecture-exception:` comment**

`build.gradle.kts` files can't carry the annotation (no compile classpath), and the module-dependency rules (`moduleRules.*`) are the ones that apply to them. Place a comment immediately above the dependency line:

```kotlin
sourceSets {
    commonMain.dependencies {
        // architecture-exception: moduleRules.platformNotFeature
        // reason="Pulls feature-level analytics types that haven't yet been promoted to " +
        //   ":platform:common:analytics. Refactor tracked separately."
        implementation(projects.feature.core.api)
    }
}
```

The exemption applies to the immediately-following dependency line. Multiple `architecture-exception:` lines may stack to exempt one declaration from several rules (`// architecture-exception: moduleRules.platformNotFeature, moduleRules.platformNotApp`).

### **6.3 Rules for adding exceptions**

* **`projectRules.exceptionsNeedHumanSignOff`** `📋 guidance`: Architecture exceptions must only be added after discussing the exception with a human author. Adding one is an acknowledgement that the code does not currently conform to the architecture, and requires human judgement to determine whether the exception is acceptable.
* **`projectRules.exceptionNotForFailingTests`** `📋 guidance`: Adding an architecture exception is **not** a valid way to resolve an immediate architecture-test failure without user feedback. If a test fails, the correct first step is to fix the code or update the architectural rules — not to suppress the failure with an exception.
* **`projectRules.exceptionNeedsKdoc`** `📋 guidance`: Every exception must include a KDoc-style (`/** ... */`) comment explaining why it exists and what the intended resolution is.
* **`projectRules.exceptionsAreTemporary`** `📋 guidance`: Exceptions should be treated as temporary. They should be revisited periodically and removed once the underlying issue is resolved.
