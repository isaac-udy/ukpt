package architecture.rules.project

import architecture.definitions.FeatureLayerPath
import architecture.definitions.containingFilePackage
import architecture.definitions.featureLayerPath
import architecture.definitions.isAncestorSubsystem
import architecture.definitions.isDirectChildSubsystem
import architecture.definitions.isFeatureRootPackage
import architecture.definitions.isKotlinxSerializable
import architecture.definitions.isNavigationKey
import architecture.definitions.participatesInPolymorphicSerialization
import architecture.definitions.codeBodyText
import architecture.definitions.resolveTypeToken
import architecture.definitions.sealedParentSimpleNames
import architecture.definitions.serialNameValue
import architecture.definitions.typeNestingChain
import architecture.definitions.typeTokens
import architecture.rules.serverservices.servicesPackageRegex
import dev.isaacudy.udytils.architecture.*

import architecture.definitions.isFeatureModule
import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.declaration.KoBaseDeclaration
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.provider.KoAnnotationProvider
import com.lemonappdev.konsist.api.provider.KoFullyQualifiedNameProvider

/*
 * Not a package layer: no classifying constructs, no `inPackage`, hence no exhaustiveness rule.
 */
@Describe("""
    These rules are not tied to a Construct or a single package; they apply across every feature
    module. Several govern the process for [architecture exceptions](exceptions.md); the mechanism
    itself is documented there.

    Context for the exception-handling rules: exceptions defined in the
    [services contract](serverservices.md#service-interface) cross the client/server boundary as
    serialised payloads, and the deserialised types don't always extend `Exception`. `AsyncState`
    is the async-result wrapper that [ViewModels](clientui.md#view-model) consume.
""")
object ProjectRules : RuleGroup() {

    // ---- §5.1 Exception handling -------------------------------------------------------------
    @Describe("A `try/catch` block must never catch `Exception`; use `catch (t: Throwable)` or a specific exception type instead")
    val noCatchException by rule {
        rationale(
            """
            The urpc transport (`dev.isaacudy.udytils:urpc-*`) deserialises server-side exceptions
            into types that may not extend `Exception`, such as kotlinx-serialization error types.
            A `catch (Exception)` block silently misses these, so the error propagates uncaught and
            crashes on an internal thread instead of being handled by application code.
            """.trimIndent(),
        )
        note("On the client, prefer `AsyncState.fromSuspending` over manual `try/catch`: it captures exceptions correctly and integrates with the ViewModel state pattern.")
        note("Catching a specific exception type, such as `catch (t: IllegalArgumentException)`, is always acceptable when you only want to handle that case.")
        scope { scope, exempt ->
            val tryDeclarationRegex = Regex(
                pattern = ".*\\btry\\s*\\{.*\\}.*\\bcatch\\s*\\(.*\\bException\\s*\\).*",
                option = RegexOption.DOT_MATCHES_ALL,
            )
            (scope.functions() + scope.properties())
                .filterNot { exempt(it) }
                .filter { it.text.matches(tryDeclarationRegex) }
                .map { Violation(it, "try/catch catches `Exception` — catch `Throwable` or a specific type instead") }
        }
    }

    @Describe("An exception type defined in `server.services` (the client/server contract) must be annotated with `@Serializable`")
    val serviceExceptionsSerializable by rule {
        rationale(
            """
            The urpc transport (`dev.isaacudy.udytils:urpc-*`) deserialises server-side exceptions
            into typed payloads on the client; without `@Serializable` the type and message are lost
            in transit and the client receives a generic deserialisation failure. Exceptions under a
            `server.services` sub-package stay server-side and never reach the client, so they are
            out of scope.
            """.trimIndent(),
        )
        note("Prefer subclassing `PresentableException` with a deliberate `retryable` flag: streaming flows auto-retry retryable errors and surface terminal ones, and the unary error UI offers a Retry action only when `retryable`.")
        scope { scope, exempt ->
            scope.classes(includeNested = true)
                .filter { it.isFeatureModule() }
                .filter { isInServicesContractPackage(it) }
                .filter { clazz ->
                    clazz.parents().any { parent ->
                        parent.name == "RuntimeException" ||
                            parent.name == "Exception" ||
                            parent.name == "PresentableException"
                    }
                }
                .filterNot { exempt(it) }
                .filterNot { it.hasAnnotationWithName("Serializable") }
                .map { Violation(it, "service-defined exception is not annotated with @Serializable") }
        }
    }

    // ---- §5.2 Imports ------------------------------------------------------------------------
    @Describe("An import must not use a wildcard; always list the explicit symbols")
    val noWildcardImports by rule {
        rationale(
            """
            Wildcards hide which symbols a file depends on, break several architecture tests
            (which inspect import names directly), and silently pull in new names when the
            imported package adds members.
            """.trimIndent(),
        )
        scope { scope, exempt ->
            scope.files
                .filter { it.isFeatureModule() }
                .filterNot { exempt(it) }
                .filter { file -> file.imports.any { it.isWildcard } }
                .map { Violation(it.path, "file uses a wildcard import") }
        }
    }

    @Describe("An `AsyncState` must never be constructed directly via `Loading`/`Success`/`Error`; use `AsyncState.fromSuspending`/`fromFlow` instead")
    val noDirectAsyncStateConstruction by rule {
        rationale(
            """
            Direct construction skips the exception capture, cancellation, and state-flow protocol
            that `AsyncState.fromSuspending`/`fromFlow` handle uniformly, silently breaking the
            contract the rest of the codebase relies on. Files that legitimately build AsyncState
            values (defining its semantics, or the server-side status pattern) opt out with
            `@file:ArchitectureException`.
            """.trimIndent(),
        )
        note("A construction inside a `@Preview` function is sample state for a snapshot/preview, not production wiring, so it is exempt — no `@ArchitectureException` is needed. The rule still flags direct construction in any real code, including a `@Preview`'s non-preview helpers.")
        scope { scope, exempt ->
            val constructionRegex = Regex("""AsyncState\.(Loading|Success|Error)\s*[(<]""")
            scope.files
                .filter { it.isFeatureModule() }
                .filterNot { exempt(it) }
                .filter { file ->
                    // A `@Preview` composable legitimately builds sample AsyncState values for each
                    // previewed state. Strip every `@Preview` function's own text from the file, then
                    // scan the remainder: constructions inside a preview are gone, real code stays.
                    val remainder = file.functions(includeNested = true, includeLocal = true)
                        .filter { it.hasAnnotationWithName("Preview") }
                        .map { it.text }
                        .fold(file.text) { acc, previewText -> acc.replace(previewText, "") }
                    constructionRegex.containsMatchIn(remainder)
                }
                .map { Violation(it.path, "constructs AsyncState.Loading/Success/Error directly outside a `@Preview` — use fromSuspending/fromFlow") }
        }
    }

    // ---- subsystem packages ------------------------------------------------------------------
    @Describe("A package in a feature layer must name that layer only through its own package, its direct child subsystems, and its ancestors up to the layer root")
    val subsystemVisibility by rule {
        rationale(
            """
            A subsystem package is a boundary, not a namespace. One level inward is what gives it an
            interior: the parent names the subsystem, and the subsystem chooses what of itself the
            parent may see — the same property depth-is-privacy gives the whole taxonomy. Unlimited
            inward visibility would make a subtree a prefix and nothing more, so the root could name
            a vendor client three levels down and no boundary would exist anywhere.

            Sideways is forbidden because two subsystems that name each other are one subsystem with
            a package split through it. Composition between them belongs to their shared ancestor,
            which is the package that is allowed to know both.
            """.trimIndent(),
        )
        note("Upward is unrestricted: a shared payload is an ordinary domain model at the shared ancestor and a shared contract an ordinary domain interface there, and the layer's own purity rules already bound what either can do.")
        note("`server.data.storage` and `client.data.storage` are visible layer-wide within their own feature's data layer. Storage is not a subsystem — it is the Row-speaking half of the layer, and one flat persistence surface is what gives a table a single owner.")
        note("Tested over imports and over fully-qualified references in the file body, because a type named in a type position has no import to inspect. A name that resolves to no project declaration — generated code, a library — is not tested.")
        note("Keyed on the package alone. A declaration's visibility modifier says nothing about which package may name it, so `internal` and `public` neighbours are governed identically.")
        scope { scope, exempt ->
            val resolve = packageResolver(scope)
            scope.files
                .filter { it.isFeatureModule() }
                .filterNot { exempt(it) }
                .flatMap { file ->
                    val ownPackage = file.packagee?.name.orEmpty()
                    val own = featureLayerPath(ownPackage) ?: return@flatMap emptyList<Violation>()
                    file.namedPackages(resolve)
                        .mapNotNull { (name, namedPackage) ->
                            val target = featureLayerPath(namedPackage) ?: return@mapNotNull null
                            if (!target.sameLayerAs(own) || own.sees(target.subsystem)) return@mapNotNull null
                            Violation(
                                file.path,
                                "`$ownPackage` names `$name` across a subsystem boundary — a package sees its own " +
                                    "package, its direct children, and its ancestors, never a sibling or a deeper descendant",
                            )
                        }
                }
        }
    }

    @Describe("A subsystem package outside the domain layer must name its own side's domain only through its mirror subsystem, that subsystem's direct children, and their ancestors")
    val subsystemMirrorsDomain by rule {
        rationale(
            """
            An outer layer's subsystem gets exactly its domain twin's visibility, plus the twin
            itself. That is what keeps a subsystem's adapters contained: the package that answers
            `domain.processing.audio`'s ports cannot also answer `domain.processing.events`', so a
            subsystem's edge sits beside the subsystem rather than at the layer root. The mirror's
            depth is therefore set by the ports it satisfies rather than chosen.
            """.trimIndent(),
        )
        note("A layer-root file is unconstrained by the mirror: a root Repository provides root-declared contracts, as it always has. The rule binds only a file that is itself in a subsystem package.")
        note("An outer subsystem with no domain twin is legal and needs no special case — the rule restricts domain imports, and a package with none has nothing to restrict.")
        scope { scope, exempt ->
            val resolve = packageResolver(scope)
            scope.files
                .filter { it.isFeatureModule() }
                .filterNot { exempt(it) }
                .flatMap { file ->
                    val ownPackage = file.packagee?.name.orEmpty()
                    val own = featureLayerPath(ownPackage) ?: return@flatMap emptyList<Violation>()
                    if (own.layer == "domain" || own.subsystem.isEmpty()) return@flatMap emptyList<Violation>()
                    file.namedPackages(resolve)
                        .mapNotNull { (name, namedPackage) ->
                            val target = featureLayerPath(namedPackage) ?: return@mapNotNull null
                            if (!target.sameSideAs(own) || target.layer != "domain") return@mapNotNull null
                            if (own.mirrors(target.subsystem)) return@mapNotNull null
                            Violation(
                                file.path,
                                "`$ownPackage` names `$name` — it mirrors " +
                                    "`${own.side}.domain.${own.subsystem}`, that subsystem's direct children, and its ancestors",
                            )
                        }
                }
        }
    }

    // ---- §5.3 Action and request types -------------------------------------------------------
    @Describe("An action/request type must model its variants as a `sealed interface`/`sealed class` (each variant a `data class`), not as a single type with an `enum` discriminator and nullable fields")
    val sealedActionVariants by rule {
        rationale(
            """
            A sealed hierarchy makes illegal field combinations unrepresentable and lets `when`
            exhaustiveness drive handling, so adding a variant surfaces every site that must handle it.
            """.trimIndent(),
        )
        note("\"An enum that should be a sealed class\" can't be detected reliably by the tests.")
        unverifiable()
    }

    // ---- §6.3 Architecture-exception sign-off (all guidance — enforced by human review) ----
    @Describe("An architecture exception may only be added after discussing the exception with a human author")
    val exceptionsNeedHumanSignOff by rule { unverifiable() }

    @Describe("An architecture exception is not a valid way to resolve an immediate architecture-test failure; fix the code or the rule first")
    val exceptionNotForFailingTests by rule { unverifiable() }

    @Describe("An architecture exception must explain why it exists and the intended resolution in a non-blank `reason` argument on the `@ArchitectureException`")
    val exceptionNeedsReason by rule {
        note("The test covers `@ArchitectureException` on declarations, including file-level `@file:` annotations; `// architecture-exception:` comments in build files carry their reason inline and are out of scope.")
        note("The explanation must be the annotation's own `reason` argument — it is machine-readable, travels with the annotation, and is the natural form for a file-level `@file:ArchitectureException(reason = …)`. A KDoc comment alone does not satisfy this rule.")
        scope { scope, exempt ->
            scope.declarations(includeNested = true)
                .filter { (it as? KoAnnotationProvider)?.hasAnnotationWithName("ArchitectureException") == true }
                .filterNot { exempt(it) }
                .filterNot { decl ->
                    // The `reason = "…"` argument is the required, machine-readable explanation. The
                    // regex demands a non-whitespace character after the opening quote, so a
                    // blank sign-off like `reason = " "` does not satisfy the rule.
                    (decl as? KoAnnotationProvider)?.annotations
                        ?.firstOrNull { it.name == "ArchitectureException" }
                        ?.text?.contains(Regex("""reason\s*=\s*"\s*[^\s"]""")) == true
                }
                .map { Violation(it, "declaration carries @ArchitectureException without a non-blank `reason` explaining why and the intended resolution") }
        }
    }

    @Describe("An architecture exception should be temporary; revisit it periodically and remove it once the underlying issue is resolved")
    val exceptionsAreTemporary by guidance

    @Describe("Every `@Serializable` type that participates in polymorphic serialization must pin an explicit `@SerialName`")
    val serialNamePinnedOnPolymorphicTypes by rule {
        rationale(
            """
            Without a `@SerialName`, kotlinx derives the discriminator from the fully-qualified class
            name — so the package path silently becomes part of the serialized format, and the first
            package move invalidates every stored row and every persisted client state that carries
            one. Pinning makes the wire value an explicit, reviewable decision.

            Sealed variants are the obvious case. The one that bites is the non-obvious one: a
            top-level `@Serializable` class registered for polymorphic dispatch — an Enro
            `NavigationKey` is exactly this — is *not* a sealed variant and slips past a rule that
            only checks those. All 44 navigation destinations relied on a package-derived
            discriminator, and several of their files contain a `@SerialName` on a nested result
            type, so a file-level check reads as safe and is not.
            """.trimIndent(),
        )
        note("Checked per declaration, not per file: an annotation on a nested type does not pin its parent.")
        note("A derived discriminator fails *silently* wherever the decoder is tolerant — see `EntityMetadata`, whose decoder returns an empty list and whose next write persists it.")
        scope { scope, exempt ->
            val violations = mutableListOf<Violation>()
            scope.classesAndInterfacesAndObjects(includeNested = true)
                .filterNot { exempt(it) || ArchitectureExceptions.isIgnored(it) }
                .filter { it.isKotlinxSerializable() }
                .filter { it.participatesInPolymorphicSerialization() }
                .filterNot { it.hasAnnotationWithName("SerialName") }
                .forEach {
                    violations += Violation(it, "`${it.name}` is polymorphically serialized but has no `@SerialName` — its discriminator is its package path")
                }
            violations
        }
    }

    @Describe("A `@SerialName` on a polymorphically serialized type must encode the type that encloses it: exactly `NavigationKey.<Name>` for a navigation destination, and a value ending with the type-nesting chain from the outermost declaring type for a sealed variant")
    val serialNameEncodesEnclosingType by rule {
        rationale(
            """
            A discriminator is read far from the class that produced it — in a stored JSONB row, a
            captured request, a browser history entry — and a bare word is unreadable there. Three
            different destinations declare a sealed `Action` with a `Delete` variant, so `"Delete"`
            names four things and identifies none of them; `"EventCardOptionsDestination.Action.Delete"`
            identifies exactly one. Encoding the enclosing type is what makes a payload
            self-describing to whoever is holding it.

            The value stays **package-free**, which is the other half of the requirement. A package
            path in a discriminator is what couples the wire format to where the class lives and
            makes moving it a migration; a type-nesting chain moves with the class, so repackaging
            stays free.
            """.trimIndent(),
        )
        note("Only the required suffix is checked for a sealed variant, so a hierarchy pinned to a pre-move fully-qualified name for compatibility already satisfies this — the type chain is the end of an FQN.")
        note("The required chain runs from the outermost declaring type, not just the immediate sealed parent: two destinations each nesting a sealed `Action` with a `Delete` variant would otherwise share the discriminator `\"Action.Delete\"`, and a value two readers can claim identifies neither.")
        note("A destination is checked exactly, not by suffix: nothing durable rides on a navigation key, so there is no compatibility case that would justify a longer value.")
        scope { scope, exempt ->
            scope.classesAndInterfacesAndObjects(includeNested = true)
                .filterNot { exempt(it) || ArchitectureExceptions.isIgnored(it) }
                .filter { it.isKotlinxSerializable() }
                .filter { it.participatesInPolymorphicSerialization() }
                .mapNotNull { declaration ->
                    val pinned = declaration.serialNameValue() ?: return@mapNotNull null
                    val name = declaration.name

                    if (declaration.isNavigationKey()) {
                        val required = "NavigationKey.$name"
                        return@mapNotNull when (pinned) {
                            required -> null
                            else -> Violation(
                                declaration,
                                "`$name` is a navigation destination pinned to `\"$pinned\"` — it must be `\"$required\"`",
                            )
                        }
                    }

                    // A nested variant's identity is its full nesting chain; a top-level variant
                    // has no chain of its own, so its sealed parents' names stand in.
                    val chain = declaration.typeNestingChain()
                    val accepted = if (chain.size >= 2) {
                        listOf(chain.joinToString("."))
                    } else {
                        declaration.sealedParentSimpleNames().map { "$it.$name" }
                    }
                    when {
                        accepted.any { pinned.endsWith(it) } -> null
                        else -> Violation(
                            declaration,
                            "`$name` is pinned to `\"$pinned\"`, which does not end with " +
                                accepted.joinToString(" or ") { "`\"$it\"`" } +
                                " — the discriminator has to name the type that encloses it",
                        )
                    }
                }
        }
    }

    @Describe("A `TransactionRunner` may only be injected by a UseCase or a Repository")
    val transactionRunnerInjectedByUseCaseOrRepository by rule {
        rationale(
            """
            Opening a transaction is a statement about which writes have to land together, and only
            two places are positioned to make it. A [UseCase](serverdomain.md#use-case) composes
            several domain interfaces and is the one place that knows the whole unit of work; a
            [Repository](serverdata.md#repository) owns the writes it makes through its
            StorageClasses. Everything else is on the wrong side of that knowledge: an entry point in
            `server.services` would be scoping a transaction around contracts whose implementations
            it cannot see, and a [StorageClass](serverdata.md#storage-class) already runs inside
            whatever transaction its caller opened — taking the runner would let it widen a boundary
            it is a participant in.
            """.trimIndent(),
        )
        note("A block that spans two features' writes is a UseCase by construction: a Repository may not inject a domain interface, so it cannot reach another feature's contract to put inside one.")
        scope { scope, exempt ->
            scope.classes(includeNested = true)
                .filter { it.isFeatureModule() }
                .filterNot { exempt(it) }
                .filter { cls ->
                    // Resolved through the file's imports so an alias (`import … as TxRunner`)
                    // or a qualified reference is still recognized — and matched against the one
                    // canonical FQN, so an unrelated vendor type named TransactionRunner is not.
                    cls.primaryConstructor?.parameters.orEmpty().any { param ->
                        typeTokens(param.type.name).any { token ->
                            cls.containingFile.resolveTypeToken(token) == "platform.server.postgres.TransactionRunner"
                        }
                    }
                }
                .filterNot { it.mayOpenTransactions() }
                .map { Violation(it, "`${it.name}` injects `TransactionRunner` — only a UseCase or a Repository may open a transaction") }
        }
    }

    @Describe("Two features must not declare service exceptions with the same simple name")
    val serviceExceptionSimpleNamesUnique by rule {
        rationale(
            """
            urpc identifies an error by `throwable::class.simpleName` — `ServiceError.from` sends it
            and the client matches on it — so the simple name *is* the wire contract. That makes
            package moves free, which is why the migration is safe, but it also means two features
            with a same-named exception are indistinguishable to a client.
            """.trimIndent(),
        )
        note("Scoped to exceptions that reach the wire: those declared in the services contract package (`feature.x.server.services`, excluding its server-only sub-packages) or in a feature root, which is the vocabulary both sides name. Two server-private exceptions with one name never meet, so they do not collide.")
        scope { scope, exempt ->
            scope.classes(includeNested = true)
                .filter { it.isFeatureModule() }
                .filterNot { exempt(it) }
                .filter { cls -> cls.name.endsWith("Exception") }
                .filter { isWireVisible(it) }
                .groupBy { it.name }
                .filterValues { it.size > 1 }
                .flatMap { (name, declarations) ->
                    declarations.map { Violation(it, "service exception simple name `$name` is declared in ${declarations.size} places — urpc cannot tell them apart on the wire") }
                }
        }
    }
}

/**
 * A package sees its own, each **direct child** subsystem, and **any ancestor** up to the layer
 * root. A feature's `data.storage` is the exception: one flat persistence surface, visible from
 * anywhere in its own data layer.
 */
private fun FeatureLayerPath.sees(named: String): Boolean =
    named == subsystem ||
        isDirectChildSubsystem(child = named, of = subsystem) ||
        isAncestorSubsystem(ancestor = named, of = subsystem) ||
        (layer == "data" && named == "storage")

/** The mirror: the twin subsystem, its direct children, and its ancestors — and nothing else. */
private fun FeatureLayerPath.mirrors(domainSubsystem: String): Boolean =
    domainSubsystem == subsystem ||
        isDirectChildSubsystem(child = domainSubsystem, of = subsystem) ||
        isAncestorSubsystem(ancestor = domainSubsystem, of = subsystem)

/**
 * Resolves a named type to the package that *declares* it, rather than reading the package off the
 * name. Two measured shapes make the string reading wrong: a nested member carries more segments
 * than its package (`ProcessingConfig.Companion.DEFAULT` names its own file), and a name that
 * resolves to nothing is not project source — a generated binding or a library — so it is untested.
 */
private fun packageResolver(scope: KoScope): (String) -> String? {
    val packageByFqn: Map<String, String> = scope.declarations(includeNested = true)
        .filterIsInstance<KoFullyQualifiedNameProvider>()
        .mapNotNull { declaration ->
            val fqn = declaration.fullyQualifiedName ?: return@mapNotNull null
            fqn to (declaration as KoBaseDeclaration).containingFilePackage()
        }
        .toMap()
    return fun(name: String): String? {
        var candidate = name.trimEnd('.')
        while (true) {
            packageByFqn[candidate]?.let { return it }
            if (!candidate.contains('.')) return null
            candidate = candidate.substringBeforeLast('.')
        }
    }
}

/** A side-first name as it appears in a file body, where there is no import to inspect. */
private val sideFirstReferenceRegex = Regex("""feature\.\w+\.(?:client|server)\.[\w.]+""")

/** Every project declaration a file names — import or fully-qualified body reference — and its package. */
private fun KoFileDeclaration.namedPackages(resolve: (String) -> String?): List<Pair<String, String>> {
    val body = codeBodyText()
    return (imports.map { it.name } + sideFirstReferenceRegex.findAll(body).map { it.value })
        .distinct()
        .mapNotNull { name -> resolve(name)?.let { name to it } }
}

/**
 * The cross-the-wire contract package itself, `feature.[name].server.services`. The server-only
 * sub-packages — `internal`, `tools` — are deeper and are not the contract.
 */
private fun isInServicesContractPackage(declaration: KoBaseDeclaration): Boolean =
    servicesPackageRegex.matchEntire(declaration.containingFilePackage())?.groupValues?.get(2) == ""

/**
 * True for a [UseCase](serverdomain.md#use-case) — a `[DomainInterface]Impl` in a `domain` package —
 * or a [Repository](serverdata.md#repository) in a `data` package, on either side. Matched by name
 * and package rather than by running the Constructs' own predicates, because this rule is scoped to
 * the whole project rather than to one layer's population.
 */
private fun KoClassDeclaration.mayOpenTransactions(): Boolean {
    val pkg = containingFilePackage()
    if (name.endsWith("Repository")) return pkg.contains(".data")
    if (name.endsWith("Impl")) return pkg.contains(".domain")
    return false
}

/**
 * True for an exception a client can see: one declared in the services contract package, or in a
 * feature root — the vocabulary both sides speak. Everything deeper is side-private and never
 * crosses the wire under its own name.
 */
private fun isWireVisible(declaration: KoBaseDeclaration): Boolean {
    if (isInServicesContractPackage(declaration)) return true
    return declaration.isFeatureRootPackage()
}
