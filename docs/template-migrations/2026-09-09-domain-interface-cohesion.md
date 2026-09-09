# Domain interface cohesion guidance

Guidance, advisory audits, and one new enforced rule. Existing code fails only where a UseCase has
its own file beside its interface's file in one domain package.

New rule on the shared UseCase rules, instantiated for `ClientDomain.UseCase` and
`ServerDomain.UseCase`:

- **`declaredInItsInterfaceFile`**: a UseCase in the same module and package as its domain
  interface is declared in the interface's file. `Reorder.kt` holds `Reorder` and `ReorderImpl`.
  The implementation of an interface published to `:api` keeps its own file in the client or
  server module.

New Guidance on the shared DomainInterface rules, instantiated for `ClientDomain.DomainInterface`
and `ServerDomain.DomainInterface`:

- **`namesACapability`**: a Domain Interface names a capability a consumer asks for, or returns a
  domain model a consumer needs; it does not mirror one storage call, one property of a model, or
  one step of an implementation. An implementation step with one caller is a private function.
- **`readProjections`**: facts about one model that a consumer needs together, sharing scope,
  freshness, and failure behaviour, come back as one immutable domain model from the Repository
  that owns the storage. The notes state when reads stay separate, that a data class alone does
  not make its facts consistent, and that filter variants over one collection share one interface.
- **`mutationResults`**: a mutation returns the value its caller needs next, and no value when an
  observed read projection carries the outcome.

Advisory audits, reported by `auditArchitecture` and counted in the `verifyArchitecture` summary
line, never failing the build. Each grouped finding names one candidate, lists its declarations as
evidence, and ends in a review question:

- **`ClientDomain.inventory` / `ServerDomain.inventory`**: one line per feature with the domain
  interface, domain model, and UseCase counts and how many interfaces have no consumer, one, or
  several. Printed on every run.
- **`…DomainInterface.readProjections`**: three or more reads from one provider whose only
  consumer is one class.
- **`…DomainInterface.namesACapability`**: a class injecting six or more domain interfaces; a
  mixed group of three or more with one consumer; a `Get<Model><Part>` name.
- **`…DomainInterface.collapsedUpdateFamilies`**: three or more mutations on one noun from one
  provider.
- **`…DomainInterface.consumedInProduction`** (new Guidance): an interface no class injects.
- **`…UseCase.existsForADecision`** (new Guidance): a UseCase whose constructor takes one domain
  interface.

A consumer is a class whose primary constructor takes the interface. Expect a long report on a
project with many one-consumer interfaces; the findings are the review's starting list.

Reworded: `collapsedUpdateFamilies` no longer states that reads stay separate because their return
types differ. `ClientData.Repository.doesNotInjectDomainInterfaces` and
`ServerData.Repository.doesNotInjectDomainInterfaces` now say a Repository assembles owned storage
into a domain model behind one property and a UseCase composes independent capabilities. The
Repository narratives lead with domain model assembly. `ClientUi.ViewModel.aggregateReadProjection`
points the reviewer through the aggregate to its input contracts.

Examples: the Repository, DomainInterface, and DomainModel examples show a consumer before and
after the Repository assembles the model, a read that stays separate, and a lifecycle-state model.
`UseCase.examples.md` is new on both groups.

Skills, carried by the file sync: `ukpt-architecture-review` gains a domain contract inventory and
covers server capability design; `ukpt-feature-slice` and `ukpt-urpc-service` gain a
models-before-interfaces step.

The `:feature:core` worked example follows the guidance. Its client domain is now
`FlowOfGreetingSummary` and `UpdateGreetings` (an `Update.Add`/`Update.Reset` family) provided by
`GreetingRepository`, and `Greet`, a UseCase that decides the greeting text and calls
`updateGreetings.add`. `GreetingSummary` holds the greeting list and derives `latest`.
`FlowOfGreetings`, `FlowOfLatestGreeting`, `FlowOfGreetingHistory`, `FlowOfGreetingSummaryImpl`,
`GetGreeting`, and `ResetGreetings` are gone. A project that kept the greeting example may reshape
it the same way or drop it.

## Detection

`verifyArchitecture` fails `ClientDomain.UseCase.declaredInItsInterfaceFile` or
`ServerDomain.UseCase.declaredInItsInterfaceFile` once per UseCase file beside its interface's
file:

```bash
find feature -path "*/build" -prune -o -path "*/domain/*" -name "*Impl.kt" -print \
    | grep -v "/src/[^/]*[Tt]est/" \
    | while read -r f; do
        [ -f "$(dirname "$f")/$(basename "$f" Impl.kt).kt" ] && echo "$f"
      done
```

Everything else is advisory. Candidates for review, per feature and layer:

```bash
# Domain interfaces per feature and layer
grep -rl "^fun interface " --include="*.kt" feature | grep "/domain/" \
    | sed -E 's#feature/([^/]+)/([^/]+)/.*#\1 \2#' | sort | uniq -c

# Interfaces referenced from at most one production file besides their provider (approximate)
for f in $(grep -rl "^fun interface " --include="*.kt" feature | grep "/domain/"); do
    name=$(basename "$f" .kt)
    n=$(grep -rlw "$name" --include="*.kt" feature app \
        | grep -v "/$name.kt\|Dependencies.kt\|Test" | wc -l)
    [ "$n" -le 2 ] && echo "$n $name"
done | sort -n
```

A feature whose `domain` package holds many interfaces and few domain models, or whose
interfaces mostly have one consumer, is where the review pays.

## Migration

For each UseCase file the detection lists, move the class into the interface's file below the
interface and delete the `Impl` file. The package, the class name, and the Koin binding do not
change, so nothing else is edited.

The rest is optional. For each candidate group, run the domain contract inventory in the
`ukpt-architecture-review` skill. Where a group of reads has one consumer and one provider, and the
facts share scope, freshness, and failure behaviour, replace the group with one Repository property
returning one domain model, and delete the interfaces, properties, and bindings it replaces. Keep
reads that a consumer uses alone or whose boundaries differ.

## Verification

```bash
./gradlew :platform:common:architecture:verifyArchitecture
```
