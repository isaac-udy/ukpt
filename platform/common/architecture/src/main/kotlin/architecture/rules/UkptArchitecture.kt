package architecture.rules

import architecture.definitions.isFeatureModule
import architecture.projectScope
import dev.isaacudy.udytils.architecture.*
import architecture.rules.feature.FeatureRules
import architecture.rules.module.ModuleRules
import architecture.rules.project.ProjectRules
import architecture.rules.serverdata.ServerData
import architecture.rules.serverdomain.ServerDomain

/**
 * UKPT's architecture definition for the `htmx` template branch: the rule groups in document order,
 * the scope the rules govern, and the docs layout. The [Describe] text is the README template —
 * `{{toc}}` expands to the generated doc list.
 */
@Describe("""
    # UKPT Architecture

    This is the `htmx` branch of the UKPT template: a Ktor server that renders HTML with kotlinx.html
    and updates it with htmx. Its architecture is built from vertical feature slices
    (`:feature:[name]:{api,server}`) over shared infrastructure (`:platform`), assembled by the
    `:app:server` application module. Module-graph rules keep the slices independent.

    A declaration's **package** says what it is; the Gradle **module** it lives in says who may see
    it. A feature is rooted at `feature.[name]`, which holds its shared vocabulary — the domain
    models its layers and other features use. One level down is `server`; two levels down is a
    layer within it. The deeper the package, the more private the code.

    ```
    server.web → server.domain ← server.data
    ```

    The domain layer is the core of the application: it defines the interfaces and models that the
    other layers consume or implement. `server.web` consumes them to answer HTTP requests with
    pages, fragments and event streams; `server.data` defines `Repository` classes that implement
    the interfaces and produce the models.

    The `main` branch of the template adds Compose clients and an RPC contract between client and
    server. The shared layer pages below still name them in places; this branch has neither, and
    links to their pages render as plain text.

    The rules govern the feature modules. The composite build (`embedded-udytils`), `build-logic`,
    test sources, and this rule module itself are not tested. `:feature:core` is the worked example
    the rules describe: it keeps its feature code in `feature.[name]` package namespaces so each
    slice stays liftable into its own module.

    Rules land enforced from their first commit, never as audits, and no declaration carries an
    `@ArchitectureException`. A rule that cannot be met is a design question, not a setting.
""")
object UkptArchitecture : ArchitectureDefinition(
    groups = listOf(
        ModuleRules,
        FeatureRules,
        ServerDomain,
        ServerData,
        ProjectRules,
    ),
    scope = { projectScope },
    membership = { it.isFeatureModule() },
    docs = DocsConfig(
        module = "platform/common/architecture",
        omittedDocs = setOf("clientui.md", "clientdomain.md", "clientdata.md", "serverservices.md", "designsystem.md"),
    ),
)
