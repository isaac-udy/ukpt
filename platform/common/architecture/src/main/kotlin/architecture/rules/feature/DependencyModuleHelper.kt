package architecture.rules.feature

import dev.isaacudy.udytils.architecture.*

@Describe("""
    An `internal` function with a Koin `Module` receiver that a `Dependencies` module calls to
    register a group of bindings — used to split a large module into readable, named chunks.
""")
object DependencyModuleHelper : Construct<FeatureRules>(
    requirements = listOf(
        isFunction,
        isInternal,
        isFunctionWhere("has a Koin `Module` receiver") { declaration -> declaration.receiverType?.name == "Module" },
    ),
)
