# Template migrations

A log of ukpt template changes that downstream projects must apply by hand — convention changes,
rule changes, and structural changes that a file sync cannot express. The `ukpt-template-update`
skill walks these entries in order when updating a project.

## When to add an entry

Add an entry when a template change affects code that only exists in downstream projects: a
convention changes (how screens, services, or tests are written), an architecture rule is added,
renamed, or made stricter, or a module/source-set structure changes. Version bumps and changes to
template-owned files don't need an entry; the file sync carries those.

## Authoring an entry

- File name: `<templateVersion>-<short-name>.md`, where `<templateVersion>` matches the value in
  `.ukpt/template.json` at the time the change lands. Bump that version in the same commit.
- Write for an agent to execute: what changed, how to detect whether the project is affected,
  the migration steps, and how to verify. Use the documentation voice (see the architecture
  [authoring guide](../../platform/common/architecture/docs/authoring.md)).
- If the change renames or removes an architecture rule ID, say so: downstream
  `@ArchitectureException` annotations may reference the old ID.
