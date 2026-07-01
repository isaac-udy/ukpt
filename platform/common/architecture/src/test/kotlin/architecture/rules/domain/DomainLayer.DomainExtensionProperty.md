# Domain extension properties

A top-level extension property on a domain object that exposes derived state.
* **Note**: Same constraints as [domain extension functions](#domain-extension-functions). Prefer a property when the value is a pure projection of the receiver and is cheap to compute on every read.
