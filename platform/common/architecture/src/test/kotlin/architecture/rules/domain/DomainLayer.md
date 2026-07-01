# The `domain` layer

The `domain` axis is the deepest layer of a feature and appears in all three modules — `:api`, `:client`, and `:server`. Its contents are pure Kotlin: data models ([domain objects](#domain-objects)) and single-function interfaces, sometimes called Interactors ([domain interfaces](#domain-interfaces)). `domain` is the centre of gravity on both sides of the wire: it depends on no other axis, and every other axis depends on it — on the client, [Repositories](data.md#repositories) implement the domain interfaces that [ViewModels](ui.md#viewmodels) consume; on the server, the [`services` axis](services.md) implements them.

The `domain` package must only contain [domain interfaces](#domain-interfaces), [domain objects](#domain-objects), [UseCases](#usecases), [domain exceptions](#domain-exceptions), [domain constants](#domain-constants), [domain extension functions](#domain-extension-functions), and [domain extension properties](#domain-extension-properties).

The [Rules](#rules) below apply across the whole `feature.[name].domain` package.

* **Note**: Cross-feature domain dependencies should be minimised where possible, but are permitted because real-world domains have genuine dependencies between them. The important thing is getting the direction of dependencies correct and avoiding circular dependencies.
