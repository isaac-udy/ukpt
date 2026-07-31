# The architecture catalog is side-first: nine groups, shared rule base classes

The four axis-first layer groups (`DomainLayer`, `UiLayer`, `DataLayer`, `ServicesLayer`) no longer
exist. The catalog now has nine feature groups — `ModuleRules`, `FeatureRules` (with the `Shared*`
root constructs), `ClientDomain`, `ClientData`, `ClientUi`, `ServerServices`, `ServerDomain`,
`ServerData` — plus `DesignSystemRules` and `ProjectRules`. The server has a real domain layer:
`server.services` and `server.data` never see each other, and `server.domain` sits between them.

Rules shared by the client/server twins of one construct (the nine domain constructs and
`Repository`) are declared once on abstract base classes in `rules/shared/` and instantiated by one
concrete `object` per group, each under its own id — see "Sharing rules across groups" in the
generated authoring guide. Editing a base-class rule edits both sides; a rule that stops being
universal moves onto both concrete objects in the same change.

The template deliberately ships without three constructs the source architecture has: an assistant
tool surface (`ServerServices.AssistantTool`, `ServerData.AssistantConfig`) and a scheduled-job
shell (`ServerServices.ServicesJob`). A project that carries them keeps them through the update —
the catalog sync merges semantically and must not delete downstream constructs the template lacks.
The five Postgres codegen rules live on `ServerData`, beside the constructs they describe.

## Detection

The project is affected if `platform/common/architecture/src/main/kotlin/architecture/rules/`
contains `domain/`, `ui/`, `data/`, or `services/` sub-packages, or if any source carries an
`@ArchitectureException` whose `ruleIds` begin with `DomainLayer.`, `UiLayer.`, `DataLayer.`, or
`ServicesLayer.`.

## Migration

1. Take the template's catalog (the sync's semantic merge), keeping any downstream-only constructs
   and each group's project-specific `@Describe` narrative.
2. Re-root every `@ArchitectureException` id string — an exemption whose id no longer matches
   simply stops exempting, silently. The new id follows the declaration's **new home**, not a
   mechanical prefix swap:
   - `DomainLayer.DomainObject.*` → `FeatureRules.SharedDomainModel.*` when the model moves to the
     feature root, or `ClientDomain.DomainModel.*` / `ServerDomain.DomainModel.*` when it stays
     side-private;
   - other `DomainLayer.*` → `ClientDomain.*` (or `ServerDomain.*` once the declaration is
     server-side);
   - `UiLayer.*` → `ClientUi.*`;
   - `DataLayer.*` → `ClientData.*` (or `ServerData.*`);
   - `ServicesLayer.*` → `ServerServices.*`; storage-construct ids under it → `ServerData.*` —
     **except** the ServiceImpl dependency rules, whose semantics reversed rather than renamed: a
     ServiceImpl now consumes domain interfaces and never persistence, so
     `ServicesLayer.ServiceImpl.noInjectingDomainInterfaces` and
     `ServicesLayer.ServiceImpl.mayInjectStorageAndInternal` have **no successor**. Remove those
     exemptions and redesign the exempted code against `ServerServices.ServiceImpl.noPersistenceInjection`.
   The same applies to `// architecture-exception:` comments in build files.
3. Regenerate the docs: `./gradlew :platform:common:architecture:updateArchitectureDocumentation`.
   Never hand-edit the README or `docs/` — they are generated.

## Verification

`./gradlew :platform:common:architecture:verifyArchitecture` reports one nested test per rule under
the new group names; `./gradlew validateTemplate` passes (its skill-citation check knows the retired
group names and reports any skill still citing them). Code still in axis-first packages fails the
membership and `ModuleRules.sidePackageMatchesModule` tests until the package migration
(`2026-07-31.1`) is applied.
