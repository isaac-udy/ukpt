# Explicit backing fields replace `_[name]` backing properties

One enforced rule. A property is not backed by a separate `_[name]` property; the stored value is
an explicit backing field of the property that exposes it.

Kotlin 2.4 promotes explicit backing fields to Stable, with no compiler flag. The template's Kotlin
version is 2.4.0, so the backing-property pattern (`private val _items = MutableStateFlow(…)` and
`val items: StateFlow<…> get() = _items`) has a one-declaration form in every module.

## Rules

- **`ProjectRules.noBackingProperties`** (new, enforced): a `_[name]` property beside a `[name]`
  property in the same class, object, interface, or file is reported. Covers every module in the
  scope, platform and `:app` modules included. A `_`-prefixed property with no such sibling is not
  reported.

## Detection

```bash
./gradlew verifyArchitecture
grep -rn --include=*.kt -E '(val|var) _[a-zA-Z]' --exclude-dir=build .
```

Every failing `ProjectRules.noBackingProperties` names the backing property and the property it
backs.

## Migration

For each reported pair, decide what the `_[name]` property was for:

1. A `val` whose stored type is a subtype of the exposed type, with no custom getter beyond
   `get() = _[name]` and not `open` or delegated: declare the field on the property and drop the
   `_[name]` property. Writes inside the class name the property; the compiler smart casts it to
   the field's type there.

   ```kotlin
   val items: StateFlow<List<Item>>
       field = MutableStateFlow(emptyList())

   fun add(item: Item) {
       items.value = items.value + item
   }
   ```

   `_[name].asStateFlow()` and `_[name].asSharedFlow()` wrappers go with the backing property: the
   exposed type is what callers see.
2. A value the class reassigns (`private var _[name]` behind `val [name] get() = _[name]`):
   `var [name]: T = …` with a `private set`. An explicit backing field cannot be reassigned.
3. A value whose type is not a subtype of the exposed type (a `Channel` behind a `Flow`, a
   `MutableSharedFlow` behind a mapped `Flow`): keep two properties and name the private one for
   what it holds (`eventChannel`), not `_events`.

A project whose Kotlin version is below 2.4 takes option 1 only after the version bump; until
then options 2 and 3 apply, and the remaining pairs carry an `@ArchitectureException` with the
bump as their resolution.

## Verification

```bash
./gradlew verifyArchitecture
```

Then the compile sweep in `ukpt-verify`: a field type that is not a subtype of the property type,
or a property that is `open`, delegated, or has a custom getter, is a compile error at the
property.
