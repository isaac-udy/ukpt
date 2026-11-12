# Writing code comments

A comment must say something the code cannot. Before writing one, ask: could a competent
reader recover this from the names, types, and structure in front of them? If yes, delete
the comment: every later reader has to read the comment, and the comment becomes wrong
the first time the code beside the comment changes. Assume the reader infers from naming
and common patterns.

The default is no comment. When code seems to require a comment, first try a clearer
name, a `require(...)` whose message states the rule, or a restructured flow. A comment
that remains after those attempts is one line, two at most.

A comment addresses the **next reader of the file**, never the reviewer of the change.
Text addressed to the reviewer — why the diff exists, what the diff replaces, which issue
requested the change — belongs in the commit message or PR description, which stay
attached to the change.

## Wording

These rules apply to comments, KDoc, documentation, commit messages and PR descriptions.

- State the component, the action and the condition: "the parser requires a terminator",
  not "the parser wants a terminator".
- Software does not intend, travel or pay. Re-read each use of: wants, asks, reaches,
  arrives, lands, survives, holds, carries, spends, costs, earns, remembers. Replace the
  word with the literal verb: requires, calls, imports, contains, declares, stores, is
  merged, takes.
- State the actor. "Nothing" and "nobody" are not subjects: "no second class applies the
  rule", not "nothing carries the rule across".
- Repeat the noun. "It", "this" and "they" are allowed only when the noun is the subject
  of the same sentence and no other noun could be meant.
- A counting word keeps its noun: "each migration", "all three modules".
- Each sentence must be clear when quoted alone.

## Comments to write

These are the only kinds of comment to write, and each comment still has to state
something the names can't. The design reasoning behind the code belongs in the commit
message.

**Invariants the type system can't express.** A field that only a human action may set, a
string that must stay byte-identical to a value defined elsewhere, an ordering the code
relies on but doesn't enforce.

```kotlin
/** Set only by an explicit user action — sync and import must never write this. */
val displayNameOverride: String?
```

**Deliberate decisions that look wrong.** Where a maintainer would "fix" correct code,
say why the code is correct.

```kotlin
// Unknown kinds decode to Hidden deliberately: failing open would show rows
// written by newer app versions to clients that can't enforce their gating.
else -> Visibility.Hidden
```

**Root-caused workarounds.** State the root cause (and the upstream issue, if one
exists) so the workaround can be deleted when the root cause is fixed. A reader cannot
tell when to delete a workaround whose comment only says "workaround".

```kotlin
// LayoutLib's preview renderer lacks View.requestFocus (NoSuchMethodError);
// guard until previews run on a renderer that supports focus.
if (!LocalInspectionMode.current) focusRequester.requestFocus()
```

**Evolution paths in append-only artifacts.** SQL migrations especially: a column
reserved for a planned feature, or the planned columns of a table, tells the author of
the next migration the intended design.

**Contract KDoc on public API — when the KDoc adds contract.** Units, edge-case behavior,
ordering and nullability guarantees, what happens on failure. KDoc is not mandatory:
`/** The session id. */ val sessionId` is worse than no KDoc. Interface KDoc does not
restate function names, describe an implementation, or explain what the contract leaves
out.

## Comments not to write

- **Next-line narration** — `// fetch the user` above `userStore.get(id)`.
- **Name restatement** — KDoc or comments that paraphrase the identifier or type, or
  explain a self-describing name such as `SessionId`.
- **Dependency declarations** — a constructor parameter, an injected field, or a build
  dependency.
- **Change narration** — "now uses X", "previously this…", "no longer", "per review
  feedback", "the issue asked for…". Commit message.
- **Justification to the reviewer** — arguing the code is correct instead of stating the
  invariant that makes it correct, or arguing against an alternative that wasn't chosen.
- **Rationale essays** — a paragraph of design reasoning above a declaration. State the
  constraint in one line, or put the reasoning in the commit message.
- **Section banners** — `// ---- helpers ----`. Split a file that needs banners.
- **Commented-out code** — delete the code; git history keeps the deleted lines.

## One canonical home

State an invariant once, at the invariant's declaration — not at every usage site.
Repeat the invariant only at a *temptation site*: a place where a maintainer could
violate the invariant locally without seeing the declaration (for example, each site
that assembles a prompt, if the invariant constrains what prompts may say). Keep the
repeat to a single line.

How the pieces of a feature or module fit together goes in one `README.md` in that module,
not in file headers or class KDoc.

## Tests

Test names state what each test checks; don't restate the test names in a class-level
KDoc list. Comment only where an assertion is ambiguous — above all when asserting
**absence**, where the reader can't tell "correctly excluded" from "forgot to include":

```kotlin
// The glossary must not name entities whose pronouns are unset —
// absence here is the never-guess rule working, not missing coverage.
assertFalse(glossary.contains("Vorel"))
```

## Defaults

When in doubt, write no comment. Don't raise the comment density of the file you're
editing. A comment that has to change whenever the code beside the comment changes is a
second copy of the code, and one of the two copies will become wrong.
