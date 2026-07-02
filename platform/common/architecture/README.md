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

## How this works

- The **rules** are Kotlin (checked with Konsist), maintained by hand: one `RuleGroup` object per layer, one top-level `Construct<Group>` object per code shape (in its own file, listed in the group's `constructs`), a rule or guidance property on each. They live in [`src/main/kotlin/architecture/rules/`](src/main/kotlin/architecture/rules).
- The **narrative** lives in the catalog too: `@Describe("…")` annotations carry the documentation text for every group, construct, rule, and guidance entry — including this README's introduction, which is the annotation on `UkptArchitecture`.
- **Examples** are markdown files next to the rules they belong to: `<Construct>.examples.md` beside `<Construct>.kt` holds the example blocks for that construct, rendered after its rules.
- The **documentation** — this README and everything under `docs/` — is **generated** from those sources. Never edit the generated files; edit the catalog or an examples file, then regenerate.
- Read [authoring](docs/authoring.md) before adding rules: what should be a requirement vs a rule vs guidance, what audits are for, and the language conventions.

## Running the checks

- Run: `./gradlew :platform:common:architecture:verifyArchitecture`
- Expect `BUILD SUCCESSFUL`. The task always re-executes — no `--rerun-tasks` needed.
- Every rule reports as its own nested test: `<Layer> › <Construct> › <rule>`, so a failure names the exact rule.
- HTML report: `platform/common/architecture/build/reports/tests/verifyArchitecture/index.html`.
- After changing the catalog or an examples file, regenerate the docs with `./gradlew :platform:common:architecture:updateArchitectureDocumentation`. The suite fails if the generated docs drift from the sources, if prose references a rule id that doesn't exist, or if a link/anchor is broken.

## The documents

- [Module Rules](docs/module.md)
- [Domain Layer](docs/domain.md)
- [Ui Layer](docs/ui.md)
- [Data Layer](docs/data.md)
- [Services Layer](docs/services.md)
- [Feature Rules](docs/feature.md)
- [Project Rules](docs/project.md)
- [Authoring rules](docs/authoring.md)
- [Architecture exceptions](docs/exceptions.md)
- [Rule index](docs/rule-index.md)

## Rule IDs

Every rule and construct has a stable ID: the **path of the object/property names that declare it**.

| ID | Reads as |
| --- | --- |
| `DomainLayer.DomainInterface` | the `DomainInterface` construct (a classification) in the `DomainLayer` group |
| `DomainLayer.DomainInterface.interfaceDefaults` | the `interfaceDefaults` rule of the `DomainInterface` construct |
| `ModuleRules.featureNotApp` | a layer-level rule (not tied to a construct) |

- Groups and constructs are PascalCase `object`s; rules are camelCase properties on them.
- A construct's **requirements** (the predicates that decide whether a declaration *is* that construct) are not individually identified — the construct is the unit.
- Test failures, the [rule index](docs/rule-index.md), and [architecture exceptions](docs/exceptions.md) all reference rules by this path.
- The layer docs don't repeat ids next to each rule — to find a rule's id (e.g. for an exception), look it up by statement in the [rule index](docs/rule-index.md) or in the layer's `.kt`.

The groups:

| Group | Doc |
| --- | --- |
| `ModuleRules` | [docs/module.md](docs/module.md) |
| `DomainLayer` | [docs/domain.md](docs/domain.md) |
| `UiLayer` | [docs/ui.md](docs/ui.md) |
| `DataLayer` | [docs/data.md](docs/data.md) |
| `ServicesLayer` | [docs/services.md](docs/services.md) |
| `FeatureRules` | [docs/feature.md](docs/feature.md) |
| `ProjectRules` | [docs/project.md](docs/project.md) |

## Enforcement status

Each entry's status is **derived from how it is declared in the catalog**, so it can never disagree with reality:

| Status | Declared as | Meaning |
| --- | --- | --- |
| `tested` | a `rule` ending in `scope { }` / `constrain { }` / `moduleGraph { }` / `enforcedBy(...)` | A check enforces the rule and fails citing its id. `enforcedBy(...)` rules are enforced transitively by the rules they name. |
| `construct` | a `Construct(...)`'s requirement predicates | A classification. A declaration matching no construct (or more than one) fails the layer exhaustiveness / membership check. |
| `unverifiable` | a `rule` ending in `unverifiable()` | A mandatory rule that static analysis can't reliably check — enforced by review. Renders under **Rules** with an automatic "not automatically verifiable" note, and may carry an audit. |
| `guidance` | `@Describe("…") val x by guidance` | An advisory convention (may/should). Enforced by review; renders under **Guidance**, separate from **Rules**. Guidance may declare an `audit { }` — a check that never fails the build but reports non-conforming code in the test output. |
| `codegen` | a `rule` ending in `codegen()` | Guaranteed by a code generator — nothing in source for the checks to scan. |
