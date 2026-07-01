# The `ui` layer

The `ui` axis spans `:api` and `:client`. **`:api` contents**: serializable Navigation Keys (Destinations) — a feature's shared navigation entry points. **`:client` contents**: Compose UI (Screens and supporting composables), ViewModels, and UI-state models. Everything the UI loads or mutates arrives through [domain interfaces](domain.md#domain-interfaces), implemented by [Repositories](data.md#repositories) in `data` — which is also how server calls (via [Services](services.md#services-the-cross-the-wire-contract)) reach the screen.

The layer rules below apply across the whole `feature.[name].ui` package.
