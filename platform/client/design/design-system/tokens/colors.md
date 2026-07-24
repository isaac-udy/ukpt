# Colours

Source: [`UkptColors.kt`](../../src/commonMain/kotlin/platform/design/UkptColors.kt)

Colour is expressed as **semantic roles**, never as a palette of named hues. A screen asks for
`surface` and `onSurface`; it never asks for "grey 100". That indirection is the whole point — it is
what lets a palette change without touching a single call site.

A theme *is* a `UkptColors` instance. There is no enum of theme names and no `isDark` flag to branch
on: to add a palette, add a `UkptColors` value.

## Roles

| Role | Job |
|---|---|
| `background` | The page behind everything |
| `surface` | Raised areas on `background`: cards, sheets, bars |
| `onSurface` | Primary content |
| `onSurfaceVariant` | Secondary content: supporting text, inactive icons |
| `accent` | The one colour that draws the eye: primary actions, selection |
| `onAccent` | Content on `accent` |
| `outline` | Hairlines, dividers, borders |
| `error` | Destructive and failure states |
| `onError` | Content on `error` |

Each `on*` role is paired with the surface it sits on. Using `onSurface` over `accent` is a
contrast bug waiting to happen — take the pairing that exists.

## Palettes

`UkptColors.Light` and `UkptColors.Dark`. Both are **placeholders**: a neutral greyscale with a
restrained accent, chosen so the scaffold renders honestly without pretending to an identity it
doesn't have. Replacing them is the first real design task on a project.

Note that `accent` is a neutral in both palettes. That is a deliberate signal that no brand colour
has been chosen yet, not a recommendation.

## Reading a colour

Always through the theme accessor, so the active palette wins:

```kotlin
val colors = UkptTheme.colors
Text(text = label, color = colors.onSurface)
```

Never `UkptColors.Light.onSurface` directly — that pins one palette and will not follow a theme
change.

## Adding a role

Add one when a component needs a colour the palette genuinely lacks. That is a real signal, not a
failure: the alternative is a literal, which a palette swap will silently miss.

If the role is an addition the design spec never defined — a scrim, a disabled overlay — mark it in
a comment as a non-spec addition and name whose judgement the value is. That keeps the palette
explainable line by line instead of accumulating unattributed values.

## Rules

- Feature code reads colour through `UkptTheme.colors`, never `UkptColors.Light`/`Dark` directly
  and never a literal `Color(0xFF…)`.
- Use the `on*` role paired with the surface underneath; don't mix pairings.
- Meaning is never carried by colour alone — pair it with text, icon or shape.
- A new role is preferable to a literal, and a non-spec role is labelled as such.
- Changing a palette value means re-recording every golden in the same change.
