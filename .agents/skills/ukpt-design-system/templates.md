# ukpt-design-system templates

`<Prefix>` is the project's type prefix (`Ukpt` in the template itself). `<Name>` is the primitive's
PascalCase name, `<name>` its lowercase form.

Read the live `platform/client/design` module alongside these — it is the working reference, and it wins
if the two ever disagree.

---

## §1 — Primitive on **Material3**

Wraps a material component and styles it entirely from tokens. Prefer this basis unless the project
has a reason not to: it inherits semantics, minimum touch targets, state layers, ripple and focus.

Requires `api(compose.material3)` in `platform/client/design/build.gradle.kts` — material types now
appear in the primitive's own surface.

```kotlin
package platform.design.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import platform.design.<Prefix>Shapes
import platform.design.<Prefix>Spacing
import platform.design.<Prefix>Theme

enum class <Prefix><Name>Variant { Primary, Secondary, Ghost }

@Composable
fun <Prefix><Name>(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: <Prefix><Name>Variant = <Prefix><Name>Variant.Primary,
    enabled: Boolean = true,
) {
    val colors = <Prefix>Theme.colors
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = <Prefix>Shapes.small,
        contentPadding = PaddingValues(
            horizontal = <Prefix>Spacing.md,
            vertical = <Prefix>Spacing.sm,
        ),
        colors = when (variant) {
            <Prefix><Name>Variant.Primary -> ButtonDefaults.buttonColors(
                containerColor = colors.accent,
                contentColor = colors.onAccent,
            )
            <Prefix><Name>Variant.Secondary -> ButtonDefaults.outlinedButtonColors(
                containerColor = colors.surface,
                contentColor = colors.onSurface,
            )
            <Prefix><Name>Variant.Ghost -> ButtonDefaults.textButtonColors(
                contentColor = colors.onSurfaceVariant,
            )
        },
    ) {
        Text(text = label, style = <Prefix>Theme.typography.label)
    }
}
```

`role = Role.Button` is **not** needed here — material's `Button` sets it. That is the point of this
basis.

---

## §2 — Primitive on bare **foundation** (the scaffold default)

Read `platform/client/design/src/commonMain/kotlin/platform/design/components/<Prefix>Button.kt` — it is the
worked example and is kept current.

What it must carry that a material component would have given you for free:

- `role = Role.Button` (or the right `Role`) on the clickable modifier — without it a screen reader
  announces nothing.
- A minimum touch target (48dp) — the scaffold's button does **not** have one; add it.
- Focus indication and a pressed/state layer — likewise absent.
- `stateDescription` on any state that stops being interactive but stays meaningful.

---

## §3 — Primitive on **Compose Unstyled**

No verbatim template on purpose: the library's API is not vendored here, and a stale snippet would be
worse than none. Add the dependency, read its current documentation, and hold to the same contract:

- Stateless — props in, events out; no state owned by the component.
- Variant `enum class` in the same file as the component.
- Every colour, dimension and text style from `<Prefix>Theme` / `<Prefix>Spacing` / `<Prefix>Shapes`.
- Explicit semantics on interactive elements, and on non-interactive states that carry meaning.
- One primitive per concept; growth is a new variant.

---

## §4 — Doc page → `design-system/components/<name>.md`

```markdown
# <Name>

Source: [`<Prefix><Name>.kt`](../../src/commonMain/kotlin/platform/design/components/<Prefix><Name>.kt) ·
Doc surface: `<Prefix><Name>DocSurface.kt`

![<Prefix><Name> variants, Light palette](../../src/androidHostTest/snapshots/images/platform/design/<Prefix><Name>VariantsLightPreview.png)
![<Prefix><Name> variants, Dark palette](../../src/androidHostTest/snapshots/images/platform/design/<Prefix><Name>VariantsDarkPreview.png)

<One paragraph: what this primitive is for, and what it deliberately is not.>

## Variants

| Variant | Use |
|---|---|
| `Primary` | ... |
| `Secondary` | ... |
| `Ghost` | ... |

## Usage

```kotlin
<Prefix><Name>(
    label = "Save",
    onClick = viewModel::save,
    variant = <Prefix><Name>Variant.Primary,
)
```

<State what it does not do, and where that responsibility lives instead.>

## Accessibility

<What semantics it carries and why. If it is bespoke rather than material-backed, say what had to be
set by hand.>

## Rules

- <What callers must not do with it.>
- Never pass a literal colour or dimension — the variant decides the tokens.
- Keep it stateless; the caller owns pressed/loading/selected.
- Changing appearance means re-recording this module's goldens in the same change. Renaming a
  preview function renames its golden and breaks the embed above — `DesignSystemDocImagesTest`
  will fail.
```

Add a row for the page to the table in `design-system/README.md`.

---

## §5 — Doc surface → `src/androidHostTest/kotlin/platform/design/<Prefix><Name>DocSurface.kt`

`@Preview` functions discovered by the module's SHRINK-mode `PreviewSnapshotTest`, each wrapping a
curated sheet — every variant, labelled — in `DocSurface`'s fixed-size container. One preview per
palette, passing the palette explicitly; bare `@Preview` with no qualifiers, so the golden's name
is exactly the function name. The golden lands at
`snapshots/images/platform/design/<FunctionName>.png`.

```kotlin
package platform.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import platform.design.components.<Prefix><Name>
import platform.design.components.<Prefix><Name>Variant

@Preview
@Composable
private fun <Prefix><Name>VariantsLightPreview() {
    DocSurface(<Prefix>Colors.Light, width = 220.dp, height = 220.dp) {
        VariantsSheet(paletteName = "Light")
    }
}

@Preview
@Composable
private fun <Prefix><Name>VariantsDarkPreview() {
    DocSurface(<Prefix>Colors.Dark, width = 220.dp, height = 220.dp) {
        VariantsSheet(paletteName = "Dark")
    }
}

@Composable
private fun VariantsSheet(paletteName: String) {
    val colors = <Prefix>Theme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(<Prefix>Spacing.md),
        verticalArrangement = Arrangement.spacedBy(<Prefix>Spacing.sm),
    ) {
        Text(
            text = paletteName,
            style = <Prefix>Theme.typography.caption,
            color = colors.onSurfaceVariant,
        )
        <Prefix><Name>Variant.entries.forEach { variant ->
            <Prefix><Name>(label = variant.name, onClick = {}, variant = variant)
        }
    }
}
```

**Sizing:** the author picks `width`/`height` — `DocSurface` fixes the golden's exact canvas
(SHRINK crops to the container), so the doc page embeds stable geometry. Size so nothing clips; the
sheet's `fillMaxSize().background(...)` paints any spare canvas, and content that outgrows the
container is clipped rather than shrunk. Record and *look at the PNG* before settling the numbers.

**Canvas budget:** the device canvas (~960dp per axis) is still the ceiling — a `DocSurface` larger
than it clips. Split an oversized sheet into another preview instead of growing one image.

`private` is fine throughout — the scanner includes private previews, and a `private` sheet is
called directly from previews in the same file, which also keeps same-named sheets in sibling files
from colliding.
