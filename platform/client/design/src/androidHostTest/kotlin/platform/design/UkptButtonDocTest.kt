package platform.design

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
import platform.design.components.UkptButton
import platform.design.components.UkptButtonVariant

/**
 * Documentation surface for [UkptButton] — every variant, in both palettes, in one image.
 *
 * This is a **doc surface**, not regression coverage: it is a composition *about* the system, and
 * `design-system/components/button.md` embeds its golden by filename. Renaming [variants] renames
 * the golden and breaks that link, which `DesignSystemDocImagesTest` turns into a failing build.
 *
 * Doc surfaces are hand-written rather than `@Preview`-driven on purpose: a preview is a single
 * state of a real screen, whereas this is a curated grid that exists only to be read. It also lives
 * in the test source set, so it never ships in the artifact.
 */
class UkptButtonDocTest {

    @get:Rule
    val snapshot = SnapshotRule()

    @Test
    fun variants() {
        snapshot.component {
            Row(horizontalArrangement = Arrangement.spacedBy(UkptSpacing.md)) {
                VariantColumn(colors = UkptColors.Light, paletteName = "Light")
                VariantColumn(colors = UkptColors.Dark, paletteName = "Dark")
            }
        }
    }
}

/**
 * One palette's column of every variant, plus a disabled example.
 *
 * `internal` rather than `private` so the Compose compiler and layoutlib can reach it when the
 * snapshot renders.
 */
@Composable
internal fun VariantColumn(
    colors: UkptColors,
    paletteName: String,
) {
    UkptTheme(colors = colors) {
        Column(
            modifier = Modifier
                .width(220.dp)
                .background(colors.background)
                .padding(UkptSpacing.md),
            verticalArrangement = Arrangement.spacedBy(UkptSpacing.sm),
        ) {
            Text(
                text = paletteName,
                style = UkptTheme.typography.caption,
                color = colors.onSurfaceVariant,
            )
            UkptButtonVariant.entries.forEach { variant ->
                Row(horizontalArrangement = Arrangement.spacedBy(UkptSpacing.sm)) {
                    UkptButton(
                        label = variant.name,
                        onClick = {},
                        variant = variant,
                    )
                }
            }
            UkptButton(
                label = "Disabled",
                onClick = {},
                variant = UkptButtonVariant.Primary,
                enabled = false,
            )
        }
    }
}
