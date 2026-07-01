# Domain constants

An `object` declaration whose only members are `val` constants — used to anchor domain-level magic numbers, lookup tables, or named tags.
* **Note**: A constants object is the right home for things like `val MAX_PARTY_SIZE = 6` or a sealed-but-keyed lookup table. Anything that wants behaviour belongs on a domain object as a member or extension.
