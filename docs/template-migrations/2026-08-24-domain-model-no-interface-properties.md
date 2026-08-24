# Domain model must not hold domain interfaces, and DI binding check is now per-binding

Two architecture rule changes:

**New rule: `ClientDomain.DomainModel.noDomainInterfaceProperties` / `ServerDomain.DomainModel.noDomainInterfaceProperties`** — a domain model (data class, sealed class/interface, enum, or value class in `client.domain` or `server.domain`) must not declare a property or constructor parameter whose type is a domain interface from the same `domain` layer. The type is matched bare, nullable, or inside a wrapper such as `Lazy<…>` or `List<…>`.

**Changed rule: `FeatureRules.constructorReferenceBindings`** — the check is now per-binding rather than per-file. It scans the file text for `Name(get<` / `Name(get(` patterns, resolves `Name` through imports, and looks up the class in the scope. A binding whose resolved class has more than 22 constructor parameters is permitted (Koin's constructor-reference DSL has overloads only up to 22 parameters). A binding for an unresolvable class is still a violation. An existing project may have had multi-line lambda bindings (constructor call spanning lines, with `get()` on a continuation line) that the previous per-line regex missed; those now fail.

## Detection

Domain models holding domain interfaces:

```bash
# Find fun interfaces in client.domain / server.domain
grep -rn "^fun interface " --include="*.kt" -- '**/client/domain/**' '**/server/domain/**' \
    | sed 's/.*fun interface \([A-Za-z0-9_]*\).*/\1/' | sort -u > /tmp/ifaces.txt

# Find data/sealed/value classes in the same layers whose properties name one of those interfaces
grep -rn "val " --include="*.kt" -- '**/client/domain/**' '**/server/domain/**' \
    | grep -f /tmp/ifaces.txt
```

Lambda-style DI bindings:

```bash
grep -En '[A-Z][A-Za-z0-9_]*[[:space:]]*\([[:space:]]*get[[:space:]]*[<(]' --include="*.kt" -r -- '**/feature/**'
```

## Migration

For each domain model that holds a domain interface: remove the interface-typed property and inject the interface directly into the consumer (the ViewModel, UseCase, or Repository that constructs the model). If the consumer's constructor then exceeds 22 parameters, the lambda-style DI binding is permitted.

For each lambda-style DI binding the new per-binding check catches: replace it with the constructor-reference form (`singleOf(::Name).bind(...)`, `factoryOf(::Name)`, or `viewModelOf(::Name)`).

## Verification

```bash
./gradlew :platform:common:architecture:verifyArchitecture
```
