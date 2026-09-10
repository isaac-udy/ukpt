package feature.shop.client.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import platform.design.UkptPreviewFrame
import platform.design.UkptSpacing
import platform.design.UkptTheme

@Composable
internal fun ShopScreenContent(state: SaveState) {
    Column(modifier = Modifier.padding(UkptSpacing.md)) {
        Text(text = "Shop", color = UkptTheme.colors.onSurface)
    }
}

@Preview
@Composable
internal fun ShopScreenCompactWidthPreview() {
    UkptPreviewFrame(width = 360.dp) {
        ShopScreenContent(state = SaveState())
    }
}
