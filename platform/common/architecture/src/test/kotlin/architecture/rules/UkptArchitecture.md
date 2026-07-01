# UKPT Architecture

How this works:

- The **rules** are Kotlin (Konsist tests), maintained by hand: one `RuleGroup` object per layer, a nested `Construct` object per code shape, a rule per property. They live in [`src/test/kotlin/architecture/rules/`](src/test/kotlin/architecture/rules).
- The **narrative** is markdown, also maintained by hand: fragment files next to the rules they describe — `DataLayer.md` for a layer, `DataLayer.Repository.md` for one construct.
- The **documentation** — this README and everything under `docs/` — is **generated** from those two sources. Never edit the generated files; edit a fragment or the catalog, then regenerate.

## Running the checks

- Run: `./gradlew :platform:common:architecture:test --rerun-tasks`
- Expect `BUILD SUCCESSFUL`.
- Every rule reports as its own nested test: `<Layer> › <Construct> › <rule>`. A failure names the exact rule (e.g. `DataLayer › Repository › doesNotInjectDomainInterfaces`).
- HTML report: `platform/common/architecture/build/reports/tests/test/index.html`.
- `--rerun-tasks` is load-bearing: Konsist caches the project scope, and a stale cache hides new violations.
- Not wired into CI yet — to enforce automatically, run the command above on pull requests.

## Changing rules or docs

- Change a **rule**: edit the layer's `.kt` in `src/test/kotlin/architecture/rules/<layer>/`.
- Change **narrative**: edit the fragment `.md` next to that `.kt` (each generated file's banner names its sources).
- Then regenerate the docs:

```
UPDATE_ARCHITECTURE_DOCS=true ./gradlew :platform:common:architecture:test
```

- The test suite fails if the generated docs drift from the sources, if prose references a rule id that doesn't exist, or if a link/anchor is broken.

## The documents

{{toc}}

## Rule IDs

Every rule and construct has a stable ID: the **path of the object/property names that declare it**.

| ID | Reads as |
| --- | --- |
| `DomainLayer.DomainInterface` | the `DomainInterface` construct (a `🔶 construct` classification) in the `DomainLayer` group |
| `DomainLayer.DomainInterface.errorsViaExceptions` | the `errorsViaExceptions` rule of the `DomainInterface` construct |
| `DomainLayer.noPlatformDeps` | a layer-level rule (not tied to a construct) |
| `ModuleRules.platformNotFeature` | a group-level module-graph rule |

- Groups and constructs are PascalCase `object`s; rules are camelCase properties on them.
- A construct's **requirements** (the predicates that decide whether a declaration *is* that construct) are not individually identified — the construct is the unit.
- Test failures, the [rule index](docs/rule-index.md), and [architecture exceptions](docs/exceptions.md) all reference rules by this path.

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

Each rule's tag is **derived from how it is declared in the catalog**, so it can never disagree with reality:

| Tag | Declared as | Meaning |
| --- | --- | --- |
| `✅ tested` | `scope { }` / `constrain { }` / `moduleGraph { }` / `enforcedBy(...)` | A Konsist check enforces the rule and fails citing its id. `enforcedBy(...)` rules are enforced transitively by the rules they name. |
| `🔶 construct` | a `Construct(...)`'s requirement predicates | A classification. A declaration matching no construct (or more than one) fails the layer exhaustiveness / membership check. |
| `📋 guidance` | `guidance()` | A convention static analysis can't reliably check. Enforced by review. |
| `⚙️ codegen` | `codegen()` | Guaranteed by the `dev.isaacudy.udytils.postgres` code generator — nothing in `src/` for Konsist to scan. |
