> [!NOTE]
> **This file is generated — do not edit it by hand.**
> Intro source: the @Describe annotation on `UkptArchitecture` (`src/main/kotlin/architecture/rules/UkptArchitecture.kt`); the standard sections come from the framework.
> Regenerate with `./gradlew :platform:common:architecture:updateArchitectureDocumentation`.

# UKPT Architecture

The architecture of UKPT: a Kotlin Multiplatform template with vertical feature slices
(`:feature:[name]:{api,client,server}`) over shared infrastructure (`:platform`), assembled by
thin application shells (`:app`). Features are organised along four axes — `domain`, `ui`,
`data` (client), and `services` (the urpc client↔server contract plus its server-side
implementation) — with module-graph rules keeping the slices independent.

The rules govern the feature modules (`projectScope`, excluding the embedded composite builds
and test sources); the catalog itself is meta-code and is not scanned. `:feature:core` is the
worked example the rules describe.

## Rules

- [Module Rules](docs/module.md)
- [Domain Layer](docs/domain.md)
- [Ui Layer](docs/ui.md)
- [Data Layer](docs/data.md)
- [Services Layer](docs/services.md)
- [Feature Rules](docs/feature.md)
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

This README and everything under `docs/` is generated from the catalog. Never edit these files directly — edit the catalog and regenerate. Read [authoring](docs/authoring.md) before adding rules.

## Run the tests

```
./gradlew :platform:common:architecture:verifyArchitecture
```

## Regenerate the documentation

```
./gradlew :platform:common:architecture:updateArchitectureDocumentation
```

Run this after changing the catalog or an examples file. The tests fail if the generated documentation is manually edited, or if the documentation references a rule that doesn't exist.

## Rule IDs

Every Rule/Guidance/Construct has a stable ID based on the object/property that declares it.

| ID | Reads as |
| --- | --- |
| `ModuleRules.featureNotApp` | a RuleGroup-level rule (not tied to a Construct) |
| `DomainLayer.DomainInterface.interfaceDefaults` | the `interfaceDefaults` Rule of the `DomainInterface` Construct |
| `DomainLayer.DomainInterface` | the `DomainInterface` Construct (a classification) in the `DomainLayer` RuleGroup |

Test failures, the [rule index](docs/rule-index.md), and [architecture exceptions](docs/exceptions.md) reference rules by id. Construct requirements don't have their own IDs, they belong to their Construct.
