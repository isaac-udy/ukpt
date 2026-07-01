# Destinations (NavigationKeys)

A serializable data class or object representing the navigation contract for a particular screen; the input parameters required by that screen (if any) and the output result type provided by that screen (if any).
* **Note**: "Minimal data" means identifiers, not payloads — a Destination should accept a `User.Id` and let the Screen load the associated `User`, rather than accepting an entire `User`.
