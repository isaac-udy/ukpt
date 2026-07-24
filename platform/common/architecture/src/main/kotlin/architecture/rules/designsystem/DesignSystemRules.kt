package architecture.rules.designsystem

import dev.isaacudy.udytils.architecture.*

/*
 * Declared WITHOUT `inPackage`: `platform.design` is not a governed layer with a construct catalog,
 * and the global layer-membership universe is feature modules only. These are two boundary rules
 * about the design system and how features consume it, not a classification of its contents.
 */
@Describe("""
    The design system lives in `:platform:client:design` (package root `platform.design`): a token
    layer, the primitives built on it, and a `design-system/` docs folder whose every image is a
    committed Paparazzi golden. Its own conventions are documented there — these two rules are the
    boundaries that the architecture suite can check.

    It is deliberately the leanest client module: pure Compose, no navigation or DI. Broader shared
    UI that needs those — nav-aware scaffolds, common components — belongs in `:platform:client:ui`,
    which builds on this module rather than the reverse.

    The first rule keeps the module lean. The second is the contract in the other direction:
    features read tokens rather than restating values.
""")
object DesignSystemRules : RuleGroup() {

    @Describe("The design-system module must not depend on navigation or dependency injection")
    val noNavigationOrDi by rule {
        rationale(
            """
            The design system is a pure Compose module: tokens and stateless primitives. Pulling in
            navigation or DI turns it into application infrastructure, and every consumer then
            inherits those dependencies to draw a button. Shared UI that needs navigation or DI —
            common scaffolds and nav-aware components — belongs in a sibling `:platform:client:*`
            module such as `:platform:client:ui`, which builds on this one, not here.
            """.trimIndent(),
        )
        note("A primitive that seems to need navigation is usually missing a callback parameter — the caller navigates, the primitive reports.")
        scope { scope, exempt ->
            scope.files
                .filter { it.path.contains("/platform/client/design/") }
                .filterNot { exempt(it) }
                .filter { file ->
                    file.imports.any { import ->
                        val name = import.name
                        name.startsWith("dev.enro.") || name.startsWith("org.koin.")
                    }
                }
                .map { Violation(it.path, "design-system file imports navigation or dependency injection") }
        }
    }

    @Describe("A feature's `ui` package should read colours and dimensions from the design system rather than declaring them literally")
    val noLiteralsInFeatureUi by guidance {
        note("Audited rather than enforced: a literal is occasionally right — a one-off illustration, an aspect ratio — and the judgement is easier to make in review than in a rule.")
        note("A value the tokens don't have is a signal the palette or scale is missing a role. Add the role rather than the literal, so a theme change reaches it.")
        audit { scope, exempt ->
            // `Color(0x…)` and a bare numeric `.dp`/`.sp`: the two forms that silently survive a
            // palette or scale change. Token reads (`UkptTheme.colors.accent`, `UkptSpacing.md`)
            // carry no numeric literal and so never match.
            val literalValue = Regex("""Color\(\s*0x|(?<![\w.])\d+(\.\d+)?\.(dp|sp)\b""")
            scope.functions()
                .filter { it.resideInPackage("feature..ui..") }
                .filterNot { exempt(it) }
                .filter { literalValue.containsMatchIn(it.text) }
                .map { Violation(it, "literal colour or dimension in feature `ui` — prefer a design-system token") }
        }
    }
}
