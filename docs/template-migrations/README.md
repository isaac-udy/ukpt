# Template migrations

A log of ukpt template changes that downstream projects must apply by hand — convention changes,
rule changes, and structural changes that a file sync cannot express. The `ukpt-template-update`
skill applies, in filename order, the entries added between the project's `templateCommit` and the
template's current commit.

## Template branches

A template branch other than `main` (named by `templateBranch` in `.ukpt/template.json`) is a
flavour of the template that merges `main` regularly. Entries arrive on a flavour with the merge,
so their dates can precede the flavour's own earlier versions; selection is by commit range for
that reason. A flavour deletes entries that do not apply to it and records them in
`.ukpt/flavour.json` under `migrations.skipped`, and edits entries it applies differently, recorded
under `migrations.adapted`.

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
