> [!NOTE]
> **This file is generated. Do not edit it directly.**
> The introduction comes from the `@Describe` annotation on `UkptArchitecture` (`src/main/kotlin/architecture/rules/UkptArchitecture.kt`); the remaining sections are provided by the udytils architecture system.
> Regenerate with `./gradlew :platform:common:architecture:updateArchitectureDocumentation`.

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

## Rules

- [Module Rules](docs/module.md)
- [Feature Rules](docs/feature.md)
- [Server Web](docs/serverweb.md)
- [Server Domain](docs/serverdomain.md)
- [Server Data](docs/serverdata.md)
- [Project Rules](docs/project.md)

## Reference

- [Rule index](docs/rule-index.md): An index of all rules used in this project
- [Authoring rules](docs/authoring.md): A guide for authoring new architecture rules
- [Architecture exceptions](docs/exceptions.md): A guide for using `@ArchitectureException` to ignore rules

---

# Architecture Testing System

This project uses the [udytils architecture system](https://github.com/isaac-udy/udytils) to define, test, and document its architecture rules. Rules are declared in Kotlin code, built on the Konsist library, and structured using the following types:

- **RuleGroup:** names and defines a set of Constructs, Rules, and Guidance.
  - A RuleGroup may be scoped to a particular package pattern. Scoping a RuleGroup to a package pattern will require all associated Constructs to be defined in a package matching that pattern.
- **Construct:** names and defines the Rules and Guidance for a code-level construct (such as a class, interface, function or property).  
  - A Construct must be associated with a RuleGroup.
  - A Construct defines a set of requirements in its constructor. If a piece of code matches the requirements for a particular Construct, it will be required to meet the rules associated with that construct. 
  - To provide example code for a Construct, create a `<Construct>.examples.md` file next to the associated `<Construct.kt>` file.
- **Rule:** a mandatory statement about a `Construct` or `RuleGroup`.
- **Guidance:** an advisory statement about a `Construct` or `RuleGroup`.
 
Documentation for RuleGroups and Constructs is recorded by annotating the RuleGroup or Construct with the `@Describe` annotation. Documentation for Rules and Guidance is also provided by annotating the Rule or Guidance statement with `@Describe` but Rules and Guidance also provide the ability to add "rationale" and "notes" through functions in their builder definitions.

Every Rule/Guidance/Construct has a stable ID based on the object/property that declares it:

| ID | Reads as |
| --- | --- |
| `ModuleRules.featureNotApp` | a RuleGroup-level rule (not tied to a Construct) |
| `FeatureRules.SharedDomainModel.immutable` | the `immutable` Rule of the `SharedDomainModel` Construct |
| `FeatureRules.SharedDomainModel` | the `SharedDomainModel` Construct (a classification) in the `FeatureRules` RuleGroup |

Test failures, the [rule index](docs/rule-index.md), and [architecture exceptions](docs/exceptions.md) reference rules by ID. Construct requirements don't have their own IDs, they belong to their Construct.

This README and everything under `docs/` is generated based on the RuleGroups/Constructs in this project. Never edit these files directly. Read [authoring](docs/authoring.md) before adding rules.

## Run the tests

```
./gradlew :platform:common:architecture:verifyArchitecture
```

## Regenerate the documentation

```
./gradlew :platform:common:architecture:updateArchitectureDocumentation
```

Run this after changing the catalog or an examples file. The tests fail if the generated documentation is manually edited, or if the documentation references a rule that doesn't exist.
