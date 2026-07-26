> **Illustrative.** No feature ships Postgres storage yet — `:platform:server:postgres` (which generates the `Table`/`Row` sources) is created with the first server feature that needs persistence. The read/write below shows the shape against those generated types.

A Storage class reading via the generated fake-constructor and writing via `setFromRow` (see [generated `Table`/`Row` sources](#generated-tablerow-sources)):

```kotlin
// Read
val row: UserProfileRow? = UserProfilesTable
    .selectAll()
    .where { UserProfilesTable.userId eq userId }
    .singleOrNull()
    ?.let(::UserProfileRow)

// Write (rowToWrite is a non-null UserProfileRow)
UserProfilesTable.upsert(UserProfilesTable.userId) {
    it.setFromRow(rowToWrite)
}
```
