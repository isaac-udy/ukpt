---
name: template-flavour-sync
description: >-
  Merge UKPT's main branch into a template flavour branch (such as htmx) —
  resolving conflicts by the classes in .ukpt/flavour.json, removing files main
  added under dropped paths, triaging new migrations, regenerating the
  architecture docs, and verifying. Template maintainers only; use when asked
  to sync, update or merge main into a flavour branch.
---

# template-flavour-sync

A flavour is a long-lived branch of the template that drops part of `main` and changes some of
what remains. It follows `main` by merging it. `.ukpt/flavour.json` says how each file merges:

| Class | Meaning | On conflict |
|---|---|---|
| `dropped` | Globs of paths the flavour removed | Remove the path: `git rm` |
| `replaced` | The flavour's own version of a file `main` also has | Keep ours; report `main`'s change for a person to port |
| `diverged` | A file both branches edit | Merge by hand, following the note |
| `generated` | Output of `generated.command` | Take either side, then regenerate |
| `migrations.skipped` | `main`'s migrations that do not apply here | Removed; `git rm` again if `main` edits one |
| `migrations.adapted` | `main`'s migrations edited for this branch | Merge by hand |

This skill ships with the template repository only; downstream projects never run it. It works
in a separate worktree for the flavour (`git worktree add ../ukpt-<flavour> <flavour>`), never by
switching branches in one checkout: the branches have different submodules.

## 1. Start

```
git status --porcelain                  # must print nothing
git fetch origin
git merge --ff-only origin/<flavour>
git merge --no-ff --no-commit origin/main
```

A non-zero exit from the merge is expected when there are conflicts.

## 2. Resolve by class

List the conflicts with `git status --porcelain=v1`, then for each entry:

- `DU` (deleted here, modified on `main`) under a `dropped` glob or a `migrations.skipped` entry:
  `git rm -q -- <path>`.
- `UD` (modified here, deleted on `main`): stop and ask; `main` removed something this branch
  still uses.
- `UU`/`AA` under `generated.paths`: `git checkout --theirs -- <path>`; §5 regenerates it. A
  generated file that merged without a conflict is regenerated there too.
- `UU` under `replaced`: `git checkout --ours -- <path>`, then record `main`'s change for the report
  (`git diff HEAD...MERGE_HEAD -- <path>`).
- `UU` under `diverged` or `migrations.adapted`: merge by hand, following the manifest note. Keep
  this branch's edits to shared paragraphs surgical, so the next merge is small.
- `UU` anywhere else: merge by hand, and decide whether the file belongs in the manifest or needs a
  seam on `main` so the next merge does not conflict.
- The `embedded-udytils` gitlink: keep the descendant of the two commits
  (`git -C embedded-udytils merge-base --is-ancestor <a> <b>`); `git add embedded-udytils`.
- A submodule `main` has and this branch dropped (such as `embedded-enro`): `git rm -q <path>`.

## 3. Remove what `main` added under dropped paths

A file `main` adds inside a dropped directory merges without a conflict. Find and remove each one:

```
git diff --cached --name-only --diff-filter=A HEAD
```

Remove every listed path that matches a `dropped` glob (`git rm -q --cached <path>` and delete the
file). `validateTemplate` in §6 fails on any that remain.

## 4. Triage new migrations

For each entry the merge added under `docs/template-migrations/`, decide:

- **Applies unchanged** — keep it.
- **Applies differently** — edit it for this branch, and add it to `migrations.adapted` with what changed.
- **Does not apply** (it changes code this branch dropped) — `git rm -f` it (the merge staged it,
  so a plain `git rm` refuses), and add it to `migrations.skipped` with the reason.

## 5. Marker and generated files

- `.ukpt/template.json`: `templateVersion` is the later of the two branches' versions;
  `templateBranch` stays this branch's name.
- Regenerate: run `generated.command` from the manifest, then `git add` its `generated.paths`.

## 6. Verify

```
./gradlew validateTemplate verifyArchitecture test
./gradlew :app:server:smokeTestFatJar
```

Run the rest of the `ukpt-verify` sweep when the merge touched build logic or dependencies.

## 7. Commit and report

Commit the merge with a message that lists:

- the `main` commit merged;
- each `replaced` file whose `main` change was not ported, with a one-line summary of that change;
- the migrations kept, adapted, and skipped;
- any file that conflicted outside the manifest, and the seam or manifest entry proposed for it.

Stop at the commit. Pushing and opening a pull request follow the owner's instructions for the
repository.
