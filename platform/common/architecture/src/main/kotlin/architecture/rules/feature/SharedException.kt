package architecture.rules.feature

import architecture.definitions.isApiModule
import architecture.definitions.isFeatureRootPackage

import dev.isaacudy.udytils.architecture.*

@Describe("""
    A class representing a known failure mode that both sides name: thrown by a server
    implementation, carried across the service boundary, and matched by client code. Because it
    crosses the wire, it is part of the feature's shared language and lives in the root.

    * **Note:** A shared exception must be listed in `@Throws` on the primary function of every
      [domain interface](clientdomain.md#domain-interface)
      ([server](serverdomain.md#domain-interface)) that raises it.
    * **Note:** `@Serializable` is part of what a shared exception *is* — a failure mode that
      cannot be serialized cannot arrive on the other side, and
      `ProjectRules.serviceExceptionsSerializable` holds the same line inside a service contract's
      reach. An exception that is not wire-visible is side-private: it belongs in that side's
      `domain`, not in the root.
""")
object SharedException : Construct<FeatureRules>(
    requirements = listOf(
        predicate("resides in the feature root package `feature.[name]`") { it.isFeatureRootPackage() },
        predicate("is declared in the feature's `:api` module") { it.isApiModule() },
        isClassWhere("is a class extending RuntimeException/Exception/PresentableException") { decl ->
            decl.parents().any { it.name == "RuntimeException" || it.name == "Exception" || it.name == "PresentableException" }
        },
        isAnnotatedWith("Serializable"),
    ),
)
