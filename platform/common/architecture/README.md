> [!NOTE]
> **This file is generated — do not edit it by hand.**
> Source: the @Describe annotation on `UkptArchitecture` (`src/test/kotlin/architecture/rules/UkptArchitecture.kt`).
> Regenerate with `UPDATE_ARCHITECTURE_DOCS=true ./gradlew :platform:common:architecture:test`.

# UKPT Architecture

How this works:

- The **rules** are Kotlin (Konsist tests), maintained by hand: one `RuleGroup` object per layer, one top-level `Construct<Group>` object per code shape (in its own file, e.g. `Repository.kt`, listed in the group's `constructs`), a rule or guidance property on each. They live in [`src/test/kotlin/architecture/rules/`](src/test/kotlin/architecture/rules).
- The **narrative** lives in the catalog too: `@Describe("…")` annotations carry the documentation text for every group, construct, rule, and guidance entry — including this README, which is the annotation on `UkptArchitecture`.
- **Examples** are markdown files next to the rules they belong to: `Repository.examples.md` beside `Repository.kt` holds the example blocks for that construct, rendered after its rules.
- The **documentation** — this README and everything under `docs/` — is **generated** from those sources. Never edit the generated files; edit the catalog or an examples file, then regenerate.

## Running the checks

- Run: `./gradlew :platform:common:architecture:test --rerun-tasks`
- Expect `BUILD SUCCESSFUL`.
- Every rule reports as its own nested test: `<Layer> › <Construct> › <rule>`. A failure names the exact rule (e.g. `DataLayer › Repository › doesNotInjectDomainInterfaces`).
- HTML report: `platform/common/architecture/build/reports/tests/test/index.html`.
- `--rerun-tasks` is load-bearing: Konsist caches the project scope, and a stale cache hides new violations.
- Not wired into CI yet — to enforce automatically, run the command above on pull requests.

## Changing rules or docs

- Change a **rule or its documentation**: edit the layer's `.kt` in `src/test/kotlin/architecture/rules/<layer>/` — statements and narrative are `@Describe` annotations there.
- Change an **example**: edit the `<Construct>.examples.md` file next to that construct's `.kt` (each generated file's banner names its sources).
- Change **this README**: edit the `@Describe` on `UkptArchitecture`.
- Then regenerate the docs:

```
UPDATE_ARCHITECTURE_DOCS=true ./gradlew :platform:common:architecture:test
```

- The test suite fails if the generated docs drift from the sources, if prose references a rule id that doesn't exist, or if a link/anchor is broken.

## The documents

- [Module Rules](docs/module.md)
- [Domain Layer](docs/domain.md)
- [Ui Layer](docs/ui.md)
- [Data Layer](docs/data.md)
- [Services Layer](docs/services.md)
- [Feature Rules](docs/feature.md)
- [Project Rules](docs/project.md)
- [Architecture exceptions](docs/exceptions.md)
- [Rule index](docs/rule-index.md)

## Rule IDs

Every rule and construct has a stable ID: the **path of the object/property names that declare it**.

| ID | Reads as |
| --- | --- |
| `DomainLayer.DomainInterface` | the `DomainInterface` construct (a classification) in the `DomainLayer` group |
| `DomainLayer.DomainInterface.errorsViaExceptions` | the `errorsViaExceptions` rule of the `DomainInterface` construct |
| `DomainLayer.noPlatformDeps` | a layer-level rule (not tied to a construct) |
| `ModuleRules.platformNotFeature` | a group-level module-graph rule |

- Groups and constructs are PascalCase `object`s; rules are camelCase properties on them.
- A construct's **requirements** (the predicates that decide whether a declaration *is* that construct) are not individually identified — the construct is the unit.
- Test failures, the [rule index](docs/rule-index.md), and [architecture exceptions](docs/exceptions.md) all reference rules by this path.
- The layer docs don't repeat ids next to each rule — to find a rule's id (e.g. for an exception), look it up by statement in the [rule index](docs/rule-index.md) or in the layer's `.kt`.

The groups:

| Group | Covers | Doc |
| --- | --- | --- |
| `ModuleRules` | Gradle module / dependency structure | [docs/module.md](docs/module.md) |
| `DomainLayer` | `domain` package | [docs/domain.md](docs/domain.md) |
| `UiLayer` | `ui` package | [docs/ui.md](docs/ui.md) |
| `DataLayer` | `data` package (client) | [docs/data.md](docs/data.md) |
| `ServicesLayer` | `services` package (contract + server) | [docs/services.md](docs/services.md) |
| `FeatureRules` | feature top-level / DI wiring | [docs/feature.md](docs/feature.md) |
| `ProjectRules` | project-wide code rules + exceptions | [docs/project.md](docs/project.md) |

## Enforcement status

Each entry's status is **derived from how it is declared in the catalog**, so it can never disagree with reality:

| Status | Declared as | Meaning |
| --- | --- | --- |
| `tested` | a `rule` ending in `scope { }` / `constrain { }` / `moduleGraph { }` / `enforcedBy(...)` | A Konsist check enforces the rule and fails citing its id. `enforcedBy(...)` rules are enforced transitively by the rules they name. |
| `construct` | a `Construct(...)`'s requirement predicates | A classification. A declaration matching no construct (or more than one) fails the layer exhaustiveness / membership check. |
| `guidance` | `@Describe("…") val x by guidance` | A convention static analysis can't reliably check. Enforced by review. Renders under **Guidance** in the docs, separate from **Rules**. Guidance may declare an `audit { }` — a check that never fails the build but reports non-conforming code in the test output. |
| `codegen` | a `rule` ending in `codegen()` | Guaranteed by the `dev.isaacudy.udytils.postgres` code generator — nothing in `src/` for Konsist to scan. |
