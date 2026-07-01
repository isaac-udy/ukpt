# Feature wiring (top-level package & DI)

The top-level `feature.[name]` package (in `:client` and `:server`) is reserved for dependency-injection wiring: Koin modules that define the feature's DI bindings, wiring its [ViewModels](ui.md#viewmodels), [Repositories](data.md#repositories), [UseCases](domain.md#usecases), and [Service](services.md#services-the-cross-the-wire-contract) implementations into the graph. Concrete classes (ServiceImpls, helpers, etc.) live in their layer-specific package; nothing else belongs here.
