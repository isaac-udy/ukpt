# UseCases

* **Definition**: A class that implements a single [domain interface](#domain-interfaces).
* **Note**: Immutable helper properties (e.g., loggers) are permitted — "no mutable state" forbids `var` properties, not properties in general.
* **Note**: If a UseCase only injects a single other domain interface, consider whether that logic should become a default function of the other domain interface instead.
* **Note**: When breaking down a complex UseCase, reach for file-private extension functions, private functions, or nested classes — not additional domain interfaces/UseCases that pollute the namespace.
