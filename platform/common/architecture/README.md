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

- [Rule index](docs/rule-index.md) — all rules, ids, and enforcement
- [Authoring rules](docs/authoring.md) — conventions for new rules
- [Architecture exceptions](docs/exceptions.md) — exempting code from rules

---

# Architecture Testing System

This project uses the [udytils architecture system](https://github.com/isaac-udy/udytils) to define, test, and document its architecture rules. Rules are declared in Kotlin code, built on the Konsist library, and structured using the following types:

- **RuleGroup** — names and defines a set of Constructs, Rules, and Guidance.
  - A RuleGroup can be (optionally) scoped to a particular package pattern
- **Construct** — names and defines the rules for a code-level construct (such as a class, interface, function or property).  
  - A Construct must be associated with a RuleGroup.
  - A Construct defines a set of requirements in its constructor. If a piece of code matches the requirements for a particular Construct, it will be required to meet the rules associated with that construct. 
  - To provide example code for a Construct, create a `<Construct>.examples.md` file next to the associated `<Construct.kt>` file
- **Rule** — a mandatory statement about a `Construct` or `RuleGroup`.
- **Guidance** — an advisory statement about a `Construct` or `RuleGroup`.
 
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

Every rule and construct has a stable id: the path of the object/property names that declare it.

| ID | Reads as |
| --- | --- |
| `DomainLayer.DomainInterface` | the `DomainInterface` construct (a classification) in the `DomainLayer` group |
| `DomainLayer.DomainInterface.interfaceDefaults` | the `interfaceDefaults` rule of the `DomainInterface` construct |
| `ModuleRules.featureNotApp` | a layer-level rule (not tied to a construct) |

Test failures, the [rule index](docs/rule-index.md), and [architecture exceptions](docs/exceptions.md) reference rules by id. Requirements don't have their own ids — they belong to their construct.
