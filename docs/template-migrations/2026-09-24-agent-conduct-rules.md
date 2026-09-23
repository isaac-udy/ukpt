# Agent conduct rules in UKPT.md

`UKPT.md` has a new `## Working as an agent` section. It states the comment rule (no comment by
default; one or two lines when one is earned; written for the next reader; no history), a review
pass over the comments a diff adds, and a merge and release rule: merging, enabling auto-merge,
dispatching a release or deploy, and changing cloud infrastructure each need the project owner's
explicit go-ahead for that specific action. `docs/code-comments.md` is tightened to match.

Both files are template-owned. This entry exists because a project may already carry the same rules
in its own `AGENTS.md`, which the file sync does not touch.

## Detection

```bash
grep -q "## Working as an agent" UKPT.md
grep -niE "comment|kdoc|merge|auto-merge|release|deploy" AGENTS.md
```

A project is affected if `AGENTS.md` states its own comment, merge, release, deploy or cloud-change
rules.

## Migration

1. Sync `UKPT.md` and `docs/code-comments.md` from the template.
2. In `AGENTS.md`, delete each rule that `UKPT.md` or `docs/code-comments.md` now states. Keep only
   what is specific to the project, such as a named release workflow or the cloud resources the
   owner controls.
3. Do not prune existing comments across the codebase as part of the update. Apply the rule to the
   files a change already touches.

## Verification

```bash
./gradlew validateTemplate
```

Read `AGENTS.md` and confirm it no longer restates the comment or merge rules.
