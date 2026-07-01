# The `data` layer

The `data` axis is **client-only**: Repository implementations and client-side local persistence (Keychain, SharedPreferences, etc.). Server-side persistence and service implementations live in the `services` axis — the server has no `data.*` package (see [the `services` layer](services.md)). Repositories fan out across [Services](services.md#services-the-cross-the-wire-contract) (the `:api` contract) and client-side local storage, and expose [domain interfaces](domain.md#domain-interfaces) for the rest of the feature to consume.
