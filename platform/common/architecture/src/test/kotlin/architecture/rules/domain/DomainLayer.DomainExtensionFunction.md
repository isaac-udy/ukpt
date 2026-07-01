# Domain extension functions

* **Definition**: A top-level extension function on a domain object that adds derived or convenience behavior.
* **Note**: Prefer default member functions on [domain interfaces](#domain-interfaces) for domain-interface convenience logic. Extension functions are appropriate for adding behavior to domain objects (e.g., `CampaignRole.permissions()`).
