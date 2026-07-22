# ukpt-design-system templates

`<Prefix>` is the project's type prefix (`Ukpt` in the template itself). `<Name>` is the primitive's
PascalCase name, `<name>` its lowercase form.

Read the live `platform/client/ui` module alongside these — it is the working reference, and it wins
if the two ever disagree.

---

## §1 — Primitive on **Material3**

Wraps a material component and styles it entirely from tokens. Prefer this basis unless the project
has a reason not to: it inherits semantics, minimum touch targets, state layers, ripple and focus.

Requires `api(compose.material3)` in `platform/client/ui/build.gradle.kts` — material types now
appear in the primitive's own surface.

```kotlin
package platform.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import platform.ui.<Prefix>Shapes
import platform.ui.<Prefix>Spacing
import platform.ui.<Prefix>Theme

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

Read `platform/client/ui/src/commonMain/kotlin/platform/ui/components/<Prefix>Button.kt` — it is the
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

Source: [`<Prefix><Name>.kt`](../../src/commonMain/kotlin/platform/ui/components/<Prefix><Name>.kt) ·
Doc surface: `<Prefix><Name>DocTest.variants`

![<Prefix><Name> variants in both palettes](../../src/androidHostTest/snapshots/images/platform.ui_<Prefix><Name>DocTest_variants.png)

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
- Changing appearance means re-recording `<Prefix><Name>DocTest` in the same change. Renaming the
  test method renames its golden and breaks the embed above — `DesignSystemDocImagesTest` will fail.
```

Add a row for the page to the table in `design-system/README.md`.

---

## §5 — Doc surface → `src/androidHostTest/kotlin/platform/ui/<Prefix><Name>DocTest.kt`

A curated composition: every variant, in both palettes, labelled. Not a `@Preview`.

```kotlin
package platform.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.isaacudy.udytils.snapshot.SnapshotRule
import org.junit.Rule
import org.junit.Test
import platform.ui.components.<Prefix><Name>
import platform.ui.components.<Prefix><Name>Variant

class <Prefix><Name>DocTest {

    @get:Rule
    val snapshot = SnapshotRule()

    @Test
    fun variants() {
        snapshot.component {
            Row(horizontalArrangement = Arrangement.spacedBy(<Prefix>Spacing.md)) {
                VariantColumn(colors = <Prefix>Colors.Light, paletteName = "Light")
                VariantColumn(colors = <Prefix>Colors.Dark, paletteName = "Dark")
            }
        }
    }
}

// `internal`, not `private`, so layoutlib and the Compose compiler can reach it.
@Composable
internal fun VariantColumn(
    colors: <Prefix>Colors,
    paletteName: String,
) {
    <Prefix>Theme(colors = colors) {
        Column(
            modifier = Modifier
                .width(220.dp)
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
}
```

If a module gains several doc tests, give each its own `VariantColumn`-equivalent or move the helper
to a shared file — two top-level `internal` helpers with the same name in the same package will
collide.

**Canvas budget:** the default device is ~960dp per axis and Paparazzi scales the PNG to ~1000px on
its long side. Content beyond that is clipped rather than shrunk — split into a second test method
instead of growing one image.
