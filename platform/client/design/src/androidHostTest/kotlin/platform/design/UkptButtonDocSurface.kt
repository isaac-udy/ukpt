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
import platform.design.components.UkptButton
import platform.design.components.UkptButtonVariant

/*
 * Doc surfaces for [UkptButton] — every variant in each palette, labelled, for
 * `design-system/components/button.md`. These are compositions *about* the system, not regression
 * coverage of any screen, and preview function names are load-bearing: the page embeds each golden
 * by filename, so renaming a preview breaks the embed and `DesignSystemDocImagesTest` fails.
 */
@Preview
@Composable
private fun UkptButtonVariantsLightPreview() {
    DocSurface(UkptColors.Light, width = 220.dp, height = 220.dp) {
        VariantsSheet(paletteName = "Light")
    }
}

@Preview
@Composable
private fun UkptButtonVariantsDarkPreview() {
    DocSurface(UkptColors.Dark, width = 220.dp, height = 220.dp) {
        VariantsSheet(paletteName = "Dark")
    }
}

/** One palette's column of every variant, plus a disabled example. */
@Composable
private fun VariantsSheet(paletteName: String) {
    val colors = UkptTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
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
            UkptButton(
                label = variant.name,
                onClick = {},
                variant = variant,
            )
        }
        UkptButton(
            label = "Disabled",
            onClick = {},
            variant = UkptButtonVariant.Primary,
            enabled = false,
        )
    }
}
