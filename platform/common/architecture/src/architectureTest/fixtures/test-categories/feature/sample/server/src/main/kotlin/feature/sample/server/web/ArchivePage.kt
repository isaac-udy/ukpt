package feature.sample.server.web

import kotlinx.html.HTML
import kotlinx.html.ul
import platform.server.web.LayoutState
import platform.server.web.ukptLayout

internal fun HTML.archivePage(state: SamplePageState) {
    ukptLayout(LayoutState(title = "Archive")) {
        ul("list") { state.labels.forEach { sampleItem(it) } }
    }
}
