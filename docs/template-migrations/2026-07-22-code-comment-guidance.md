# Code comment guidance

UKPT now ships comment discipline as [`docs/code-comments.md`](../code-comments.md), pointed at from
`UKPT.md`'s Architecture section. The rule it sets: a comment must say something the code cannot.
Comments address the next reader of the file, never the reviewer of the change — change narration
belongs in the commit message.

This is a convention, not an enforced architecture rule; there is no Konsist rule for it. It applies
to code that only exists downstream, hence this entry.

## Detection

A project is affected if it has no `docs/code-comments.md`, or if `UKPT.md` carries no pointer to
it:

```bash
test -f docs/code-comments.md && grep -q "code-comments.md" UKPT.md
```

Both files are template-owned, so a non-zero exit means the file sync has not run yet.

## Migration

1. Sync `docs/code-comments.md` and `UKPT.md` from the template. Both are template-owned, so the
   file sync carries them; no hand-editing is required.
2. Adopt the convention for new and modified code from this point on. Do **not** open a
   repo-wide comment-pruning pass as part of the template update — prune opportunistically, in the
   files a change already touches, so the cleanup stays reviewable.
3. If the project keeps its own comment or KDoc guidance in `AGENTS.md` (or elsewhere), reconcile it
   with `docs/code-comments.md` and delete whatever now duplicates it, so there is one canonical
   home.

## Verification

There is no mechanical check — the guidance is a review standard. To find the mechanically
detectable categories it forbids, audit for commented-out code and section banners:

```bash
grep -rnE "^\s*// ?-{3,}|^\s*// ?={3,}" --include="*.kt" .
```

Confirm adoption by reading `docs/code-comments.md` and checking that a recent change's comments
survive its "could a competent reader recover this from the names, types, and structure?" test.
