# UKPT Architecture

This is the entry point to the UKPT architecture rules. The rules themselves are **not maintained by hand**: they are a machine-readable catalog in [`src/test/kotlin/architecture/rules/`](src/test/kotlin/architecture/rules) — each layer a `RuleGroup` object, each construct a nested `Construct` object, each rule a property on one of them. `RegistryArchitectureTest` enforces the rules, and every document listed below (including this README) is **generated** from the catalog plus a narrative source that lives next to each layer's rules, kept in lock-step by `ArchitectureDocsTest`.

## Running the checks

Run the architecture tests — the whole suite, no device or server needed:

```
./gradlew :platform:common:architecture:test --rerun-tasks
```

Expect `BUILD SUCCESSFUL`. `architecture()` reports **one nested test per rule** (`<Layer> › <Construct> › <rule>`), so the IDE test runner — or the HTML report at `platform/common/architecture/build/reports/tests/test/index.html` — shows the tree, and a failure names the exact rule (e.g. `DataLayer › Repository › doesNotInjectDomainInterfaces`) rather than one aggregate pass/fail. `--rerun-tasks` is load-bearing: Konsist caches the project scope and a stale cache hides new violations.

After changing a rule or a narrative source, regenerate this README and everything under `docs/`:

```
UPDATE_ARCHITECTURE_DOCS=true ./gradlew :platform:common:architecture:test
```

ukpt doesn't wire this into CI yet; when you want it enforced automatically, run the first command on pull requests (e.g. a `.github/workflows/pr-verification.yml`).

## The documents

{{toc}}

To change a document, edit its narrative source (each generated file's header comment names it) or the rule catalog itself, then regenerate — hand-edits to generated files fail `ArchitectureDocsTest`.

## Rule IDs

Every rule and construct has a stable ID that is the **path of the object/property names that declare it**:

| ID | Reads as |
| --- | --- |
| `DomainLayer.DomainInterface` | the `DomainInterface` construct (a `🔶 construct` classification) in the `DomainLayer` group |
| `DomainLayer.DomainInterface.errorsViaExceptions` | the `errorsViaExceptions` rule of the `DomainInterface` construct |
| `DomainLayer.noPlatformDeps` | a layer-level rule (not tied to a construct) |
| `ModuleRules.platformNotFeature` | a group-level module-graph rule |

Groups and constructs are PascalCase `object`s; rules are the camelCase properties on them. A construct's **requirements** — the predicates that decide whether a declaration *is* that construct — are not individually identified; the construct itself is the unit (its `🔶 construct` entry lists them, AND-composed). Test failures, the [rule index](docs/rule-index.md), and [architecture exceptions](docs/exceptions.md) reference rules and constructs by this path. Search the [catalog sources](src/test/kotlin/architecture/rules) for an id.

The groups are:

| Group | Covers | Doc |
| --- | --- | --- |
| `ModuleRules` | Gradle module / dependency structure | [docs/module.md](docs/module.md) |
| `DomainLayer` | `domain` package | [docs/domain.md](docs/domain.md) |
| `UiLayer` | `ui` package | [docs/ui.md](docs/ui.md) |
| `DataLayer` | `data` package (client) | [docs/data.md](docs/data.md) |
| `ServicesLayer` | `services` package (contract + server) | [docs/services.md](docs/services.md) |
| `FeatureRules` | feature top-level / DI wiring | [docs/feature.md](docs/feature.md) |
| `ProjectRules` | project-wide code rules + exceptions | [docs/project.md](docs/project.md) |

### Enforcement status

Each rule's enforcement tag is **derived from how it is declared in the catalog**, so it can never disagree with reality:

| Tag | Declared as | Meaning |
| --- | --- | --- |
| `✅ tested` | `scope { }` / `constrain { }` (construct-scoped) / `moduleGraph { }`, or `enforcedBy(...)` | A Konsist check enforces the rule directly and fails citing its id. `enforcedBy(...)` rules are enforced *transitively* by the rules they name. |
| `🔶 construct` | a `Construct(...)`'s requirement predicates | A classification — what it means to *be* a construct. Enforced indirectly: a declaration matching no construct (or more than one) fails the layer exhaustiveness / membership check (`<Group>.exhaustive`, `architecture.everyDeclarationBelongsToALayer`) rather than a named-rule failure. |
| `📋 guidance` | `guidance()` | A convention static analysis can't reliably check (the "should…" / permissive "may…" rules). Enforced by review. |
| `⚙️ codegen` | `codegen()` | Guaranteed by the `dev.isaacudy.udytils.postgres` code generator — the shape is generated from the migrated schema, so there is nothing in `src/` for Konsist to scan. |

So `✅ tested` rules have a check citing their id; `🔶 construct` constructs are enforced through the exhaustiveness / membership check; `📋 guidance` and `⚙️ codegen` rules are not machine-checked in `src/`.
