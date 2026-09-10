package feature.shop.client.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun ShopBadge(label: String) {
    Text(
        text = label,
        modifier = Modifier
            .background(Color(0xFF112233))
            .padding(16.dp),
    )
}
