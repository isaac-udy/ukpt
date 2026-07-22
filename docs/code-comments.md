# Writing code comments

A comment must say something the code cannot. Before writing one, ask: could a competent
reader recover this from the names, types, and structure in front of them? If yes, the
comment is noise — it costs attention on every future read and rots the first time the
code moves.

Comments talk to the **next reader of the file**, never to the reviewer of the change.
Anything addressed to the reviewer — why the diff exists, what it replaces, which issue
asked for it — belongs in the commit message or PR description, where it stays attached
to the change instead of haunting the code.

## What earns a comment

**Invariants the type system can't express.** A field that only a human action may set, a
string that must stay byte-identical to a value defined elsewhere, an ordering the code
relies on but doesn't enforce.

```kotlin
/** Set only by an explicit user action — sync and import must never write this. */
val displayNameOverride: String?
```

**Deliberate decisions that look wrong.** Anywhere a well-meaning maintainer would "fix"
correct code, say why it's correct.

```kotlin
// Unknown kinds decode to Hidden deliberately: failing open would show rows
// written by newer app versions to clients that can't enforce their gating.
else -> Visibility.Hidden
```

**Root-caused workarounds.** Name the actual root cause (and the upstream issue, if one
exists) so the workaround can be deleted the day it's fixed — a workaround comment that
just says "workaround" is permanent.

```kotlin
// LayoutLib's preview renderer lacks View.requestFocus (NoSuchMethodError);
// guard until previews run on a renderer that supports focus.
if (!LocalInspectionMode.current) focusRequester.requestFocus()
```

**Evolution paths in append-only artifacts.** SQL migrations especially: a column
reserved for a planned feature, or the documented direction a table grows in, saves the
next migration author from redesigning it blind.

**Contract KDoc on public API — when it adds contract.** Units, edge-case behavior,
ordering and nullability guarantees, what happens on failure. KDoc is not a checkbox:
`/** The session id. */ val sessionId` is worse than nothing.

## What never earns a comment

- **Next-line narration** — `// fetch the user` above `userStore.get(id)`.
- **Name restatement** — KDoc or comments that paraphrase the identifier or type.
- **Change narration** — "now uses X", "previously this…", "per review feedback",
  "the issue asked for…". Commit message.
- **Justification to the reviewer** — arguing the code is correct instead of stating the
  invariant that makes it correct.
- **Section banners** — `// ---- helpers ----`. If a file needs banners, it needs
  splitting.
- **Commented-out code** — delete it; git remembers.

## One canonical home

State an invariant once, at its declaration — not at every usage site. Repeat it only at
a *temptation site*: a place where someone could plausibly violate it locally without
ever seeing the canonical home (for example, each site that assembles a prompt, if the
invariant constrains what prompts may say). Keep the repeat to a single line.

## Tests

Test names carry the *what*; don't restate them in a class-level KDoc list. Comment only
where an assertion is genuinely ambiguous — above all when asserting **absence**, where
the reader can't tell "correctly excluded" from "forgot to include":

```kotlin
// The glossary must not name entities whose pronouns are unset —
// absence here is the never-guess rule working, not missing coverage.
assertFalse(glossary.contains("Vorel"))
```

## Defaults

When in doubt, leave it out. Match the comment density of the surrounding file rather
than importing your own. And if a comment would have to change whenever the code beside
it changes, it's not a comment — it's a second copy of the code, and one of them will
end up wrong.
