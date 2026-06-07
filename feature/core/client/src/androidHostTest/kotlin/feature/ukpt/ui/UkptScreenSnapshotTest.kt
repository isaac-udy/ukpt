package feature.ukpt.ui

import androidx.compose.material3.MaterialTheme
import org.junit.Rule
import org.junit.Test
import platform.snapshot.SnapshotRule

/**
 * Snapshot test for [UkptScreenContent]. Screens are paired with an `internal [Name]ScreenContent`
 * composable (architecture rule R-UI-11) so their visual states can be snapshot-tested without a
 * ViewModel. Add a `@Test` per meaningful state (loaded, empty, error, …) as the screen grows.
 */
class UkptScreenSnapshotTest {

    @get:Rule
    val snapshot = SnapshotRule()

    @Test
    fun ukptScreenContent() {
        snapshot.screen {
            MaterialTheme {
                UkptScreenContent(UkptState())
            }
        }
    }
}
